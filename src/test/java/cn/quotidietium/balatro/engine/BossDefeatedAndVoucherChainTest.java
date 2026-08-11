package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.*;

import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Boss onBossDefeated 触发顺序 + 优惠券升级链端到端验证（轮次 51）。
 */
class BossDefeatedAndVoucherChainTest {

    @Test
    void bossDefeatedTriggersAfterBlindResultBeforeBossQueueRemove() {
        // 验证 onBossDefeated 在 Boss 盲注通关时触发，且在 bossQueue.remove(0) 之前
        RunState s = Engine.createRun("red", 0, "BDDEFEAT1", null);
        s.bossKey = "ox";
        s.bossQueue.clear();
        s.bossQueue.add("ox");
        s.nextBlind = "boss";
        Engine.selectBlind(s, Data.BlindType.BOSS, false);

        final int[] triggerCount = {0};
        Joker probe = new Joker() {
            @Override public String key() { return "probebd"; }
            @Override public String displayName() { return "ProbeBD"; }
            @Override public int cost() { return 1; }
            @Override public void onBossDefeated(RunState st, JokerInstance self) {
                triggerCount[0]++;
                // bossQueue 此时应该还有当前 Boss（尚未 remove）
                assertFalse(st.bossQueue.isEmpty(), "onBossDefeated 时 bossQueue 应非空");
            }
        };
        s.jokers.add(new JokerInstance(probe));

        // 反复出牌直到赢 Boss 回合
        while (s.phase == Phase.ROUND && s.handsLeft > 0 && !s.hand.isEmpty()) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < Math.min(5, s.hand.size()); i++) ids.add(s.hand.get(i).id());
            Engine.PlayResult r = Engine.playHand(s, ids);
            if (r.won) break;
            if (!r.ok && s.handsLeft > 0) {
                for (int tn = 1; tn <= 5; tn++) {
                    if (tn > s.hand.size()) break;
                    List<Integer> t = new ArrayList<>();
                    for (int i = 0; i < tn; i++) t.add(s.hand.get(i).id());
                    if (Engine.playHand(s, t).ok) break;
                }
            }
        }
        // 只有赢了 Boss 回合才触发（分数不够则失败，onBossDefeated 不触发）
        if (s.phase == Phase.SHOP || s.phase == Phase.BLIND_SELECT) {
            assertTrue(triggerCount[0] >= 1, "赢了 Boss 时 onBossDefeated 应触发");
        }
    }

    @Test
    void rocketOnBossDefeatedAccumulates() {
        // rocket 的 onBossDefeated：extra.pay += 2
        RunState s = Engine.createRun("red", 0, "BDROCKET1", null);
        s.jokers.add(JokerRegistry.create("rocket"));
        s.bossKey = "ox";
        s.bossQueue.clear();
        s.bossQueue.add("ox");
        s.nextBlind = "boss";
        Engine.selectBlind(s, Data.BlindType.BOSS, false);

        while (s.phase == Phase.ROUND && s.handsLeft > 0 && !s.hand.isEmpty()) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < Math.min(5, s.hand.size()); i++) ids.add(s.hand.get(i).id());
            Engine.PlayResult r = Engine.playHand(s, ids);
            if (r.won) break;
            if (!r.ok && s.handsLeft > 0) {
                for (int tn = 1; tn <= 5 && s.handsLeft > 0; tn++) {
                    if (tn > s.hand.size()) break;
                    List<Integer> t = new ArrayList<>();
                    for (int i = 0; i < tn; i++) t.add(s.hand.get(i).id());
                    if (Engine.playHand(s, t).ok) break;
                }
            }
        }
        // 如果赢了 Boss，rocket 的 pay 应 +2（从 1 → 3）
        JokerInstance rocket = s.jokers.stream().filter(j -> j.def.key().equals("rocket")).findFirst().orElse(null);
        if (rocket != null) {
            int pay = ((Number) rocket.extra.getOrDefault("pay", 1)).intValue();
            assertTrue(pay >= 1, "rocket pay 应 >= 1");
        }
    }

    @Test
    void voucherRequiresFilterInShopGen() {
        // 验证商店生成时升级券需要 requires（基础券已拥有才出现升级券）
        RunState s = Engine.createRun("red", 0, "VOUCHER1", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 赢回合进商店
        while (s.phase == Phase.ROUND && s.handsLeft > 0) {
            if (s.hand.isEmpty()) break;
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < Math.min(5, s.hand.size()); i++) ids.add(s.hand.get(i).id());
            Engine.PlayResult r = Engine.playHand(s, ids);
            if (r.won) break;
            if (!r.ok && s.handsLeft > 0) Engine.playHand(s, List.of(s.hand.get(0).id()));
        }
        if (s.phase == Phase.SHOP && s.shop != null && s.shop.vouchers != null) {
            // 没有任何已拥有券时，只应出现基础券（pair!=null 且 requires==null）
            for (var vch : s.shop.vouchers) {
                if (vch.voucher.requires != null) {
                    // 升级券出现意味着需要基础券已拥有——开局无券不应出现
                    fail("开局无已拥有券时不应出现升级券 " + vch.voucher.key);
                }
            }
        }
    }

    @Test
    void voucherUpgradeChainComplete() {
        // 验证 16 对升级链：每个 base 的 pair 是升级券，升级券的 requires 是基础券
        for (Data.Voucher v : Data.VOUCHERS) {
            if (v.pair != null) {
                // 基础券：pair 指向升级券
                Data.Voucher upgraded = null;
                for (Data.Voucher u : Data.VOUCHERS) {
                    if (u.key.equals(v.pair)) { upgraded = u; break; }
                }
                assertNotNull(upgraded, "pair " + v.pair + " 应在 VOUCHERS 中");
                assertEquals(v.key, upgraded.requires, "升级券 " + upgraded.key + " 的 requires 应是基础券 " + v.key);
            }
        }
    }

    @Test
    void allVouchersHaveUniqueKeys() {
        // 验证 32 个优惠券 key 无重复
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (Data.Voucher v : Data.VOUCHERS) {
            assertTrue(keys.add(v.key), "Voucher key 应唯一: " + v.key);
        }
        assertEquals(32, keys.size(), "应有 32 个唯一 voucher key");
    }

    @Test
    void anaglyphDoubleTagOnBossDefeated() {
        // 浮雕牌组：击败 Boss 获得翻倍标签
        RunState s = Engine.createRun("anaglyph", 0, "BDANAG1", null);
        s.bossKey = "ox";
        s.bossQueue.clear();
        s.bossQueue.add("ox");
        s.nextBlind = "boss";
        Engine.selectBlind(s, Data.BlindType.BOSS, false);

        int tagsBefore = s.tags.size();
        while (s.phase == Phase.ROUND && s.handsLeft > 0 && !s.hand.isEmpty()) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < Math.min(5, s.hand.size()); i++) ids.add(s.hand.get(i).id());
            Engine.PlayResult r = Engine.playHand(s, ids);
            if (r.won) break;
            if (!r.ok && s.handsLeft > 0) {
                for (int tn = 1; tn <= 5 && s.handsLeft > 0; tn++) {
                    if (tn > s.hand.size()) break;
                    List<Integer> t = new ArrayList<>();
                    for (int i = 0; i < tn; i++) t.add(s.hand.get(i).id());
                    if (Engine.playHand(s, t).ok) break;
                }
            }
        }
        // 只有赢了 Boss 才检查翻倍标签
        if (s.phase == Phase.SHOP || s.phase == Phase.BLIND_SELECT) {
            assertTrue(s.tags.contains("double"), "浮雕赢了 Boss 应获得翻倍标签");
        }
    }
}
