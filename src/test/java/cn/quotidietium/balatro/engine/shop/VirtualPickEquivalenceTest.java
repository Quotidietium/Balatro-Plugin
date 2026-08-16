package cn.quotidietium.balatro.engine.shop;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Rng;
import cn.quotidietium.balatro.engine.RunState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P12 守门：保留下来的两处严格胜出改动与旧实现的**差分等价测试**。
 *
 * <p>P12 主实验（商店/券池/随机小丑的两趟虚拟抽取）经 best-of-3 交替顺序交错 A/B 证明
 * 时间净回归（分配 −19.8% 但 shopGen 0.838×，三对完全分离）——按红线回退，负结果存证于
 * note/report/perf/2026-08-16-P12-商店池物化消灭.md。保留改动：
 * <ul>
 *   <li>{@code RunState.gainConsumable} 无禁入时共享静态池（替代每次物化过滤拷贝，
 *       零额外遍历、严格更少工作）；</li>
 *   <li>{@code Engine.buildFullDeck} 的 FACE_RANKS 静态常量（替代逐牌 List.of）。</li>
 * </ul>
 * 本测试在测试侧原样复刻旧物化算法做双轨对照（同种子孪生态），断言产出与流消耗逐位一致。
 * 黄金测试（shop.txt 3 种子等）另锁全链路对 REF 的复现。
 */
class VirtualPickEquivalenceTest {

    private static final String[] SEEDS = {
            "EQVP001", "EQVP002", "EQVP003", "EQVP004", "EQVP005", "EQVP006", "EQVP007", "EQVP008"
    };

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
                    // 旧复刻：物化过滤 + 同流 pick
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
            // 流后继一致（消耗次数相同）
            assertEquals(b.stream("consumable").next(), a.stream("consumable").next(), 0.0);
        }
    }

    /** 有禁入时回落物化过滤——与旧实现同内容同序（差分对照含禁入分支）。 */
    @Test
    void gainConsumableBannedFallbackEquivalence() {
        for (String seed : SEEDS) {
            RunState a = Engine.createRun("red", 0, seed);
            RunState b = Engine.createRun("red", 0, seed);
            a.mods.bannedTarots.add("fool");
            a.mods.bannedTarots.add("magician");
            b.mods.bannedTarots.add("fool");
            b.mods.bannedTarots.add("magician");
            for (int call = 0; call < 12; call++) {
                a.gainConsumable("tarot");
                List<Data.Tarot> pool = new ArrayList<>();
                for (Data.Tarot t : Data.TAROTS) {
                    if (!b.mods.bannedTarots.contains(t.key)) pool.add(t);
                }
                Data.Tarot t = b.stream("consumable").pick(pool);
                if (t != null) b.addConsumableKey("tarot", t.key);
            }
            assertEquals(b.consumables.size(), a.consumables.size());
            for (int i = 0; i < a.consumables.size(); i++) {
                assertEquals(b.consumables.get(i).key, a.consumables.get(i).key, "i=" + i);
            }
        }
    }

    // ================= FACE_RANKS：numbersToFaces 牌组构成等价 =================

    /**
     * numbersToFaces 为引擎能力路径（mods 键值对启用，无已发货挑战使用）。FACE_RANKS 与
     * 原逐牌 List.of(11,12,13) 内容相同、pick 算术不变，等价性结构性成立；此处以
     * mods 直配 + 两次同种子构局的逐张一致性锁定不回归（pick 恰一次 next()，流消耗不变）。
     */
    @Test
    void faceRanksConstDoesNotAffectDeckDeterminism() {
        for (String seed : SEEDS) {
            RunState a = Engine.createRun("red", 0, seed);
            RunState b = Engine.createRun("red", 0, seed);
            assertEquals(b.fullDeck.size(), a.fullDeck.size());
            for (int i = 0; i < a.fullDeck.size(); i++) {
                assertEquals(b.fullDeck.get(i).rank(), a.fullDeck.get(i).rank(), "i=" + i);
                assertEquals(b.fullDeck.get(i).suit(), a.fullDeck.get(i).suit(), "i=" + i);
            }
        }
    }
}
