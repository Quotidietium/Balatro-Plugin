package cn.quotidietium.balatro.engine.shop;

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
 * R203：包嵌套 × **新种子族**——PackNestingFuzzTest（R103）用 NEST* 固定族；
 * 本轮以全新种子族（NFX-*）在未测试随机空间复验同组嵌套链不变量：
 * PACK⟺pack 非空 / packReturn 无残留 / 1≤left≤choose / 反复 skip 脱离 /
 * 每步不抛异常——新种子探索第五维（包嵌套）。
 */
class PackNestingFreshSeedTest {

    private static final int TRIALS = 120;

    @Test
    void freshSeedNestedPackChainsStayConsistent() {
        Random rnd = new Random(20260821L);
        for (int trial = 0; trial < TRIALS; trial++) {
            RunState s = Engine.createRun("red", trial % 3, "NFX-" + trial, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            s.roundScore = s.blindTarget;
            Engine.playHand(s, List.of(s.hand.get(0).id()));
            assertTrue(s.phase == Phase.SHOP, "赢盲后应进商店（NFX-" + trial + "）");

            for (int step = 0; step < 40; step++) {
                try {
                    int op = rnd.nextInt(10);
                    switch (op) {
                        case 0, 1, 2 -> Packs.open(s, Data.PACKS.get(rnd.nextInt(Data.PACKS.size())));
                        case 3 -> s.gainTag(rnd.nextBoolean() ? "standard" : "buffoon");
                        case 4, 5, 6 -> {
                            if (s.pack == null) {
                                assertTrue(!Packs.pick(s, 0), "无包 pick 拒绝");
                            } else {
                                Packs.pick(s, rnd.nextBoolean()
                                        ? rnd.nextInt(s.pack.cards.size())
                                        : rnd.nextInt(200) - 100);
                            }
                        }
                        case 7 -> Packs.skip(s);
                        case 8 -> {
                            if (s.phase == Phase.ROUND) {
                                s.roundScore = s.blindTarget;
                                Engine.playHand(s, List.of(s.hand.get(0).id()));
                            } else if (s.phase == Phase.SHOP) {
                                Engine.nextRound(s);
                                if (s.phase == Phase.BLIND_SELECT) {
                                    Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                                }
                            }
                        }
                        default -> { }
                    }
                } catch (RuntimeException ex) {
                    fail("新种子包操作抛异常（NFX-" + trial + " step=" + step + "）：" + ex);
                }
                assertPackInvariants(s, trial, step);
            }
            int guard = 0;
            while (s.phase == Phase.PACK && guard++ < 20) Packs.skip(s);
            assertTrue(s.phase != Phase.PACK, "反复 skip 应脱离 PACK（NFX-" + trial + "）");
            assertPackInvariants(s, trial, -1);
        }
    }

    private static void assertPackInvariants(RunState s, int trial, int step) {
        String where = "NFX-" + trial + " step=" + step;
        if (s.phase == Phase.PACK) {
            assertTrue(s.pack != null, "PACK 阶段绝不能无包会话（" + where + "）");
        } else {
            assertTrue(s.pack == null, "非 PACK 残留包会话（" + where + "）");
            assertTrue(s.packReturn == null, "非 PACK 残留 packReturn（" + where + "）");
        }
        if (s.pack != null) {
            Packs.Session p = s.pack;
            assertTrue(p.left >= 1 && p.left <= p.def.choose,
                    "1≤left≤choose（" + where + "）：" + p.left + "/" + p.def.choose);
        }
    }
}
