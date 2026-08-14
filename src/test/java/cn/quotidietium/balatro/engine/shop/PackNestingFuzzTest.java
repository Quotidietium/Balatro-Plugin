package cn.quotidietium.balatro.engine.shop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 补充包嵌套开启（packReturn 链）的定向 fuzz（R103）。
 *
 * <p>R15 曾人工核验「PACK 阶段开新包保留旧 packReturn」，EngineApiFuzzTest 也随机调用过
 * pick/skip，但二者的不变量都未锁定**嵌套链一致性**：
 * <ul>
 *   <li>phase==PACK ⟺ pack!=null（绝不允许「有包无阶段」或「PACK 阶段无包」的孤儿态）；</li>
 *   <li>phase!=PACK ⟹ packReturn==null（返回目标不残留——残留会把下一次 finish/skip 送错阶段）；</li>
 *   <li>pack!=null ⟹ 1≤left≤def.choose≤def.size；</li>
 *   <li>嵌套深度任意（free-tag 在 PACK 阶段直接 gainTag → openFreePack 再嵌套）。</li>
 * </ul>
 * 覆盖：商店买包、PACK 中开新包（替换会话、保留返回目标）、合法/越界/已选 pick、
 * skip、free-tag 触发的嵌套（standard/buffoon）、跨回合 roundCount 变化下的流名唯一性。
 */
class PackNestingFuzzTest {

    private static final int TRIALS = 200;

    @Test
    void nestedPackChainStaysConsistent() {
        Random rnd = new Random(20260820L);
        for (int trial = 0; trial < TRIALS; trial++) {
            RunState s = Engine.createRun("red", 0, "NEST" + trial, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            // 赢下盲注进商店（真实路径）
            s.roundScore = s.blindTarget;
            Engine.playHand(s, List.of(s.hand.get(0).id()));
            assertTrue(s.phase == Phase.SHOP, "赢盲后应进商店（trial=" + trial + "）");

            for (int step = 0; step < 40; step++) {
                try {
                    int op = rnd.nextInt(10);
                    switch (op) {
                        case 0, 1, 2 -> {
                            // 开包（含 PACK 阶段嵌套开——free-tag 的引擎级等价路径）
                            Data.Pack def = Data.PACKS.get(rnd.nextInt(Data.PACKS.size()));
                            Packs.open(s, def);
                        }
                        case 3 -> {
                            // free-tag 嵌套：standard/buffoon 标签立即开免费包（可能在任何阶段）
                            s.gainTag(rnd.nextBoolean() ? "standard" : "buffoon");
                        }
                        case 4, 5, 6 -> {
                            // pick：半数合法索引，半数越界/负数
                            if (s.pack == null) {
                                assertFalse(Packs.pick(s, 0), "无包时 pick 应拒绝");
                            } else {
                                int idx = rnd.nextBoolean()
                                        ? rnd.nextInt(s.pack.cards.size())
                                        : rnd.nextInt(200) - 100;
                                Packs.pick(s, idx); // 返回值不敏感：拒绝也合法
                            }
                        }
                        case 7 -> Packs.skip(s); // 无包时安全 no-op（返回 false）
                        case 8 -> {
                            // 偶尔推进回合：赢盲→商店→next（改变 roundCount，流名前缀变化）
                            if (s.phase == Phase.ROUND) {
                                s.roundScore = s.blindTarget;
                                Engine.playHand(s, List.of(s.hand.get(0).id()));
                            } else if (s.phase == Phase.SHOP) {
                                Engine.nextRound(s);
                                if (s.phase == Phase.BLIND_SELECT) {
                                    assertTrue(Engine.selectBlind(s,
                                            Data.BlindType.byKey(s.nextBlind), false),
                                            "go 应成功");
                                }
                            }
                        }
                        default -> { /* 空步：仅跑不变量 */ }
                    }
                } catch (RuntimeException ex) {
                    fail("补充包操作抛异常（trial=" + trial + " step=" + step
                            + " phase=" + s.phase + "）：" + ex);
                }
                assertPackInvariants(s, trial, step);
            }
            // 收尾：反复 skip 直到脱离 PACK，验证能正常回到非 PACK 阶段
            int guard = 0;
            while (s.phase == Phase.PACK && guard++ < 20) Packs.skip(s);
            assertTrue(s.phase != Phase.PACK, "反复 skip 应脱离 PACK（trial=" + trial + "）");
            assertPackInvariants(s, trial, -1);
        }
    }

    /** 嵌套链一致性不变量（每步后必须成立）。 */
    private static void assertPackInvariants(RunState s, int trial, int step) {
        String where = "trial=" + trial + " step=" + step;
        if (s.phase == Phase.PACK) {
            assertTrue(s.pack != null, "PACK 阶段绝不能无包会话（" + where + "）");
        } else {
            assertTrue(s.pack == null, "非 PACK 阶段不应残留包会话（" + where + "）");
            assertTrue(s.packReturn == null, "非 PACK 阶段 packReturn 不应残留（" + where + "）→ 会把下次 finish/skip 送错阶段");
        }
        if (s.pack != null) {
            Packs.Session p = s.pack;
            assertTrue(p.left >= 1 && p.left <= p.def.choose,
                    "剩余可选数越界（" + where + "）：" + p.left + "/" + p.def.choose);
            assertTrue(p.def.choose <= p.def.size, "choose 不应超过 size（" + where + "）");
            assertTrue(p.cards.size() <= p.def.size, "包内卡数越界（" + where + "）");
        }
        // 宽松全局界：包内卡与手牌不失控
        assertTrue(s.hand.size() <= 40, "手牌泄漏（" + where + "）");
    }
}
