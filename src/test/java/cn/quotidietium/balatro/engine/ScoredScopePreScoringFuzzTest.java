package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 计分牌口径 + 计分前阶段定向 fuzz（R135）：R130-R134 新增机制（contains 集合/scoredCards
 * 口径/space·obelisk 计分前/obNoGain 标志/hook onDiscard 豁免 burnt）在随机组合下不崩溃、
 * 不变量成立（每手等级增量 ≤1、obNoGain 无残留、debuff 不计分不越界）。
 */
class ScoredScopePreScoringFuzzTest {

    @Test
    void preScoringAndScopeMechanicsHoldUnderChaos() {
        Random rnd = new Random(20260823L);
        String[] pool = {"space", "obelisk", "flowerpot", "seeingdouble", "green", "burnt",
                "ridebus", "runner", "trousers", "duo", "jolly"};
        for (int trial = 0; trial < 200; trial++) {
            RunState s = Engine.createRun("red", rnd.nextInt(8), "SSF" + trial, null);
            int n = 1 + rnd.nextInt(4);
            for (int j = 0; j < n; j++) {
                var inst = JokerRegistry.create(pool[rnd.nextInt(pool.length)]);
                if (inst != null && s.jokers.size() < 8) s.jokers.add(inst);
            }
            Engine.recomputeFlags(s);
            // 随机 hook 局占比 1/4，驱动钩子 onDiscard 路径（green 触发/burnt 豁免）
            if (rnd.nextInt(4) == 0) {
                s.bossQueue.clear(); s.bossQueue.add("hook");
            }
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            for (int hand = 0; hand < 4 && s.phase == Phase.ROUND; hand++) {
                // 随机 debuff 一张手牌（考验证分口径与不变量）
                if (rnd.nextInt(3) == 0 && !s.hand.isEmpty()) {
                    s.hand.get(rnd.nextInt(s.hand.size())).setDebuff(true);
                }
                Data.HandType t = Engine.evaluateHand(s, s.hand).type;
                int lvl = s.handLevel(t);
                int take = 1 + rnd.nextInt(Math.min(5, s.hand.size()));
                List<Integer> ids = new java.util.ArrayList<>();
                for (int k = 0; k < take; k++) ids.add(s.hand.get(k).id());
                try {
                    Engine.playHand(s, ids);
                } catch (RuntimeException ex) {
                    throw new AssertionError("trial=" + trial + " hand=" + hand + "：" + ex);
                }
                assertTrue(s.handLevel(t) - lvl <= 1,
                        "space 每手至多 +1（trial=" + trial + " hand=" + hand + "）");
                for (var j : s.jokers) {
                    assertTrue(!j.extra.containsKey("obNoGain"),
                            "obNoGain 一次性标志不得残留（trial=" + trial + "）");
                }
            }
            assertTrue(s.jokers.size() <= 8);
            assertTrue(s.hand.size() <= 40);
        }
    }
}
