package cn.quotidietium.balatro.engine.shop;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Joker;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Rng;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P12 守门：商店/发放路径「两趟虚拟抽取」与原「过滤物化成列表后 pick」的**差分等价测试**。
 *
 * <p>背景：makeJokerItem / gainRandomJoker / genShop 券池 / gainConsumable 改为零分配
 * 虚拟抽取或静态池共享（计数 → `(int)(next()*n)` → 取第 n 个匹配）。本测试在测试侧
 * **原样复刻旧物化算法**，对同一配置矩阵 × 大量种子做双轨对照，断言产出与流消耗逐位一致
 * （种子复现红线）。黄金测试（shop.txt 3 种子）另锁全链路对 REF 的复现。
 */
class VirtualPickEquivalenceTest {

    private static final String[] SEEDS = {
            "EQVP001", "EQVP002", "EQVP003", "EQVP004", "EQVP005", "EQVP006", "EQVP007", "EQVP008"
    };

    /** 对照快照（record 自带逐字段 equals）。 */
    private record RefItem(String kind, String jokerKey, String key, String name, long price,
                           Data.Edition edition, boolean eternal, boolean perishable, boolean rental) {
    }

    private static RefItem toItem(Shop.CardItem it) {
        return new RefItem(it.kind, it.joker == null ? null : it.joker.def.key(), it.key,
                it.name, it.price, it.joker == null ? null : it.joker.edition,
                it.joker != null && it.joker.eternal, it.joker != null && it.joker.perishable,
                it.joker != null && it.joker.rental);
    }

    // ================= makeJokerItem：旧物化实现（原样复刻） =================

    /** 原实现的等价复刻：按稀有度分桶 → 逐条过滤物化 → pick。 */
    private static RefItem makeJokerItemReference(RunState s, Integer forceRarity, String forceEdition) {
        Rng.Stream st = s.stream("shopjoker");
        Integer rarity = forceRarity;
        if (rarity == null) {
            double r = st.next() * 100;
            rarity = r < 70 ? 0 : r < 95 ? 1 : 2;
        }
        List<Joker> bucket = new ArrayList<>();
        for (Joker j : JokerRegistry.allJokersOrdered()) {
            if (JokerRegistry.rarityOf(j.key()) == rarity) bucket.add(j);
        }
        List<Joker> pool = new ArrayList<>(bucket.size());
        for (Joker j : bucket) {
            if (j.key().equals("cavendish") && !s.grosDead) continue;
            if (s.mods.noJokers) continue;
            if (s.mods.bannedJokers.contains(j.key())) continue;
            if (!Boolean.TRUE.equals(s.flags.get("allowDupes"))) {
                boolean owned = false;
                for (JokerInstance o : s.jokers) if (o.def.key().equals(j.key())) { owned = true; break; }
                if (owned) continue;
            }
            pool.add(j);
        }
        if (pool.isEmpty()) {
            Data.Tarot t = st.pick(Data.TAROTS);
            return new RefItem("tarot", null, t.key, t.name, Shop.shopPrice(s, 3),
                    null, false, false, false);
        }
        Joker def = st.pick(pool);
        Data.Edition edition = forceEdition != null ? parseEditionRef(forceEdition) : null;
        if (edition == null) {
            double chance = s.vouchers.contains("glowup") ? 0.2 : s.vouchers.contains("hone") ? 0.1 : 0.05;
            if (st.chance(chance)) edition = parseEditionRef(weightedEditionRef(st));
        }
        JokerInstance ji = new JokerInstance(def);
        ji.edition = edition;
        if (s.mods.blackStake && st.chance(0.3)) ji.eternal = true;
        if (s.mods.orangeStake && st.chance(0.3)) ji.perishable = true;
        if (s.mods.goldStake && st.chance(0.3)) ji.rental = true;
        if (s.mods.allEternal) ji.eternal = true;
        long price = Shop.shopPrice(s, Shop.jokerCost(ji));
        if (ji.rental) price = 1;
        return new RefItem("joker", def.key(), null, JokerRegistry.nameOf(def.key()), price,
                edition, ji.eternal, ji.perishable, ji.rental);
    }

    private static String weightedEditionRef(Rng.Stream st) {
        double r = st.next() * 100;
        if (r < 50) return "foil";
        if (r < 85) return "holo";
        return "poly";
    }

    private static Data.Edition parseEditionRef(String e) {
        if (e == null) return null;
        return switch (e) {
            case "foil" -> Data.Edition.FOIL;
            case "holo" -> Data.Edition.HOLO;
            case "poly" -> Data.Edition.POLY;
            case "negative" -> Data.Edition.NEGATIVE;
            default -> null;
        };
    }

    /** 双轨差分：同种子孪生态分别走 新实现 与 旧复刻，断言逐字段一致 + 流后继一致。 */
    @Test
    void makeJokerItemMatchesMaterializedReference() {
        for (String seed : SEEDS) {
            RunState a = configuredState(seed, 0);
            RunState b = configuredState(seed, 0);
            for (int call = 0; call < 24; call++) {
                assertEquals(makeJokerItemReference(b, null, null), toItem(Shop.makeJokerItem(a, null, null)),
                        "seed=" + seed + " call=" + call);
            }
            assertEquals(b.stream("shopjoker").next(), a.stream("shopjoker").next(), 0.0);
        }
    }

    /** 覆盖：强制稀有度 / 强制版本 / 已持有去重 / grosDead / 空池兜底（noJokers）。 */
    @Test
    void makeJokerItemEquivalenceAcrossConfigs() {
        int[][] cfgs = {{0, 0}, {1, 0}, {3, 0}, {5, 0}, {1, 1}, {0, 2}};
        for (String seed : SEEDS) {
            for (int[] cfg : cfgs) {
                RunState a = configuredState(seed, cfg[0]);
                RunState b = configuredState(seed, cfg[0]);
                if (cfg[1] == 1) { a.grosDead = true; b.grosDead = true; }
                if (cfg[1] == 2) { a.mods.noJokers = true; b.mods.noJokers = true; }
                for (int call = 0; call < 12; call++) {
                    Integer forceR = call % 4 == 3 ? call % 3 : null;
                    String forceE = call % 5 == 4 ? "foil" : null;
                    assertEquals(makeJokerItemReference(b, forceR, forceE),
                            toItem(Shop.makeJokerItem(a, forceR, forceE)),
                            "seed=" + seed + " cfg=" + cfg[0] + "," + cfg[1] + " call=" + call);
                }
            }
        }
    }

    /** allowDupes 标志下已持有小丑可重复入池（另一过滤分支）。 */
    @Test
    void makeJokerItemAllowDupesEquivalence() {
        for (String seed : SEEDS) {
            RunState a = configuredState(seed, 3);
            RunState b = configuredState(seed, 3);
            a.flags.put("allowDupes", Boolean.TRUE);
            b.flags.put("allowDupes", Boolean.TRUE);
            for (int call = 0; call < 12; call++) {
                assertEquals(makeJokerItemReference(b, null, null),
                        toItem(Shop.makeJokerItem(a, null, null)), "seed=" + seed + " call=" + call);
            }
        }
    }

    // ================= gainRandomJoker：旧物化实现（原样复刻） =================

    @Test
    void gainRandomJokerMatchesMaterializedReference() {
        for (String seed : SEEDS) {
            RunState a = configuredState(seed, 0);
            RunState b = configuredState(seed, 0);
            a.mods.bannedJokers.add("joker");
            b.mods.bannedJokers.add("joker");
            for (int call = 0; call < 16; call++) {
                Integer rarity = call % 3 == 2 ? 1 : null;
                assertEquals(gainRandomJokerReference(b, rarity), a.gainRandomJoker(rarity),
                        "seed=" + seed + " call=" + call);
                if (!b.jokers.isEmpty()) {
                    assertEquals(b.jokers.get(b.jokers.size() - 1).def.key(),
                            a.jokers.get(a.jokers.size() - 1).def.key(),
                            "seed=" + seed + " call=" + call);
                }
            }
        }
    }

    private static boolean gainRandomJokerReference(RunState s, Integer rarity) {
        if (s.jokerSpace() <= 0) return false;
        Rng.Stream st = s.stream("randomjoker");
        List<Joker> pool = new ArrayList<>();
        for (Joker j : JokerRegistry.allJokersOrdered()) {
            int r = JokerRegistry.rarityOf(j.key());
            if (rarity == null ? r < 3 : r == rarity) pool.add(j);
        }
        pool.removeIf(j -> s.mods.bannedJokers.contains(j.key()));
        if (pool.isEmpty()) return false;
        Joker pick = st.pick(pool);
        return s.gainJoker(pick.key(), null);
    }

    // ================= genShop 券池：旧物化算法复刻对照 =================

    /**
     * 双轨对照券选择：新实现经 openShop 走全链；旧复刻按「shopgenN 流在 2 次包抽取后」
     * 的状态重放旧物化券算法（过滤物化 → pick → remove）。
     */
    @Test
    void genShopVoucherMatchesMaterializedReference() {
        for (String seed : SEEDS) {
            for (int extra = 0; extra <= 2; extra++) {
                RunState a = voucherState(seed, extra);
                RunState b = voucherState(seed, extra); // 孪生：只用于读取过滤条件
                Shop.openShop(a);
                Rng.Stream st = Rng.makeStream(seed, "shopgen" + b.roundCount);
                st.next();
                st.next(); // 2 个补充包的 pick 各恰一次 next()
                List<Data.Voucher> avail = new ArrayList<>(Data.VOUCHERS.size());
                for (Data.Voucher v : Data.VOUCHERS) {
                    if (b.vouchers.contains(v.key)) continue;
                    if (v.requires != null && !b.vouchers.contains(v.requires)) continue;
                    if (b.mods.bannedVouchers.contains(v.key)) continue;
                    avail.add(v);
                }
                List<String> expected = new ArrayList<>();
                for (int vi = 0; vi < 1 + extra && !avail.isEmpty(); vi++) {
                    Data.Voucher v = st.pick(avail);
                    avail.remove(v);
                    expected.add(v.key);
                }
                assertEquals(expected.size(), a.shop.vouchers.size(),
                        "seed=" + seed + " extra=" + extra);
                for (int i = 0; i < expected.size(); i++) {
                    assertEquals(expected.get(i), a.shop.vouchers.get(i).voucher.key,
                            "seed=" + seed + " extra=" + extra + " i=" + i);
                }
            }
        }
    }

    /** 券池边界：多券标签叠加下无重复、不出现已拥有/禁入券。 */
    @Test
    void genShopVoucherNoDuplicateWhenStacked() {
        for (String seed : SEEDS) {
            RunState s = voucherState(seed, 3); // extra=3 → 4 张券
            Shop.openShop(s);
            List<String> keys = new ArrayList<>();
            for (Shop.VoucherItem v : s.shop.vouchers) {
                assertTrue(!s.vouchers.contains(v.voucher.key), "已拥有券不应出现: " + v.voucher.key);
                assertTrue(!s.mods.bannedVouchers.contains(v.voucher.key), "禁入券不应出现");
                keys.add(v.voucher.key);
            }
            for (int i = 0; i < keys.size(); i++) {
                for (int j = i + 1; j < keys.size(); j++) {
                    assertTrue(!keys.get(i).equals(keys.get(j)), "同一商店券重复: " + keys);
                }
            }
        }
    }

    // ================= gainConsumable：静态池共享等价（无禁入 → Data 池本尊） =================

    @Test
    void gainConsumableSharedPoolEquivalence() {
        for (String seed : SEEDS) {
            RunState a = Engine.createRun("red", 0, seed);
            RunState b = Engine.createRun("red", 0, seed);
            for (int call = 0; call < 16; call++) {
                String kind = call % 2 == 0 ? "tarot" : "spectral";
                a.gainConsumable(kind);
                Rng.Stream stB = b.stream("consumable");
                if (kind.equals("tarot")) {
                    List<Data.Tarot> pool = new ArrayList<>();
                    for (Data.Tarot t : Data.TAROTS) {
                        if (!b.mods.bannedTarots.contains(t.key)) pool.add(t);
                    }
                    Data.Tarot t = pool.isEmpty() ? null : stB.pick(pool);
                    if (t != null) b.addConsumableKey("tarot", t.key);
                } else {
                    List<Data.Spectral> pool = new ArrayList<>();
                    for (Data.Spectral sp : Data.SPECTRALS) {
                        if (b.mods.bannedSpectrals.contains(sp.key)) continue;
                        if (Data.SPECIAL_SPECTRALS.contains(sp.key)) continue;
                        pool.add(sp);
                    }
                    Data.Spectral sp = pool.isEmpty() ? null : stB.pick(pool);
                    if (sp != null) b.addConsumableKey("spectral", sp.key);
                }
            }
            assertEquals(b.consumables.size(), a.consumables.size());
            for (int i = 0; i < a.consumables.size(); i++) {
                assertEquals(b.consumables.get(i).key, a.consumables.get(i).key, "i=" + i);
                assertEquals(b.consumables.get(i).kind, a.consumables.get(i).kind, "i=" + i);
            }
        }
    }

    // ================= 工具 =================

    /** 构造带持有小丑/禁入的孪生态（商店生成不依赖回合阶段）。 */
    private static RunState configuredState(String seed, int ownedJokers) {
        RunState s = Engine.createRun("red", 0, seed);
        String[] keys = {"joker", "fibonacci", "greedy", "abstract", "scaryface", "cavendish"};
        for (int i = 0; i < ownedJokers && i < keys.length; i++) s.gainJoker(keys[i], null);
        if (ownedJokers > 0) s.mods.bannedJokers.add("abstract"); // 覆盖禁入分支
        return s;
    }

    /** 券对照态：拥有若干券 + 禁入券 + extraVoucher 标签叠加。 */
    private static RunState voucherState(String seed, int extraVouchers) {
        RunState s = Engine.createRun("red", 0, seed);
        s.vouchers.add("overstock");
        s.vouchers.add("crystal");
        s.mods.bannedVouchers.add("seedmoney");
        for (int i = 0; i < extraVouchers; i++) {
            s.nextShop.merge("extraVoucher", 1, (x, y) -> ((Number) x).intValue() + 1);
        }
        return s;
    }
}
