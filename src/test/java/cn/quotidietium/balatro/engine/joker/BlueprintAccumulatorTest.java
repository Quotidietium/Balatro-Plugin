package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 复制类（blueprint/brainstorm）× 累积器的 extra 状态归属语义 + ankh 副本空起步（R112）。
 *
 * <p>锁定真版语义：
 * <ul>
 *   <li><b>读共享、不复制状态</b>：blueprint 复制冰淇淋的 onScore——计分读取同一份 chips
 *       两次（ctx.joker=源实例），但融化只发生一次（blueprint 自身无 onPlayHand）。</li>
 *   <li><b>累积不翻倍</b>：brainstorm 复制绿色小丑的 onScore 读同一 mult，但 onPlayHand(self)
 *       只给源实例 +1（R111 修复后副本/复制器不再污染他人状态）。</li>
 *   <li><b>ankh 副本空起步</b>：gainJoker 产出全新实例（extra 空），不继承累积值。</li>
 * </ul>
 *
 * <p>计分对比用同种子双局同手牌：纯牌面+这两类小丑的计分路径不消耗随机流，delta 精确可断言。
 */
class BlueprintAccumulatorTest {

    private static RunState inRound(String seed) {
        RunState s = Engine.createRun("red", 0, seed, null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        return s;
    }

    @Test
    void blueprintCopiesIceCreamReadSharedStateButMeltsOnce() {
        // 同种子：A=仅冰淇淋；B=冰淇淋+蓝图（蓝图在右）。B-A 的分差应恰为 +100（复制读同一份）
        RunState a = inRound("BPA");
        JokerInstance ice = JokerRegistry.create("icecream");
        a.jokers.add(ice);
        long sa = playTwoAndScore(a);

        RunState b = inRound("BPA");
        JokerInstance ice2 = JokerRegistry.create("icecream");
        b.jokers.add(JokerRegistry.create("blueprint")); // 蓝图复制【右侧】→ 蓝图必须在左
        b.jokers.add(ice2);
        long sb = playTwoAndScore(b);

        assertEquals(100, sb - sa, "蓝图复制冰淇淋：计分读取同一份 chips 两次（+100），非复制状态");
        assertEquals(95, ((Number) ice2.extra.get("chips")).intValue(),
                "融化只发生一次（蓝图无 onPlayHand，不应 -10）");
        assertEquals(95, ((Number) ice.extra.get("chips")).intValue(), "对照局单份正常 -5");
    }

    @Test
    void brainstormCopiesLeftmostChipsReadSharedStateAndAccumulatesOnce() {
        // 头脑风暴复制【最左】：左放冰淇淋（筹码类，delta 线性精确可断言）
        RunState b = inRound("BPB");
        JokerInstance ice = JokerRegistry.create("icecream");
        b.jokers.add(ice);                              // 最左
        b.jokers.add(JokerRegistry.create("brainstorm")); // 复制最左
        long sb = playTwoAndScore(b);

        RunState a = inRound("BPB");
        a.jokers.add(JokerRegistry.create("icecream"));
        long sa = playTwoAndScore(a);

        assertEquals(100, sb - sa, "头脑风暴复制冰淇淋：读同一份 chips 两次（+100）");
        assertEquals(95, ((Number) ice.extra.get("chips")).intValue(), "融化仍只一次");

        // 绿色（mult 类，加成随整手筹码放大，不做分差断言）只验证累积语义：
        // onPlayHand(self) 仅源实例 +1，复制器不触发额外累积
        RunState g = inRound("BPG");
        JokerInstance green = JokerRegistry.create("green");
        green.extra.put("mult", 5);
        g.jokers.add(green);
        g.jokers.add(JokerRegistry.create("brainstorm"));
        playTwoAndScore(g);
        assertEquals(6, ((Number) green.extra.get("mult")).intValue(),
                "onPlayHand(self) 仅源实例 +1（复制器不触发累积）");
    }

    @Test
    void ankhProducedCopiesStartWithFreshExtra() {
        RunState s = Engine.createRun("red", 0, "ANKH1", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 累积跑者至 30，然后模拟 ankh：清除全部并 gainJoker 产出副本
        JokerInstance runner = JokerRegistry.create("runner");
        runner.extra.put("chips", 30);
        s.jokers.add(runner);
        s.jokers.remove(runner); // ankh removeIf 语义
        assertTrue(s.gainJoker("runner", null), "ankh 路径应产出新副本");
        JokerInstance copy = s.jokers.get(0);
        assertTrue(copy != runner, "新实例");
        assertTrue(!copy.extra.containsKey("chips"), "副本应空起步（不继承累积值 30）");
        // 打一手顺子：副本应从 0 累积到 15（不是 45）
        for (int i = 0; i < 5 && i < s.hand.size(); i++) {
            s.hand.get(i).setRank(5 + i);
            s.hand.get(i).setSuit(i % 2);
        }
        Engine.playHand(s, List.of(s.hand.get(0).id(), s.hand.get(1).id(),
                s.hand.get(2).id(), s.hand.get(3).id(), s.hand.get(4).id()));
        assertEquals(15, ((Number) copy.extra.get("chips")).intValue(), "副本从 0 起步 +15");
    }

    /** 打前两张并返回本手得分（roundScore 从 0 起算）。 */
    private static long playTwoAndScore(RunState s) {
        s.roundScore = 0;
        Engine.PlayResult r = Engine.playHand(s, List.of(s.hand.get(0).id(), s.hand.get(1).id()));
        assertTrue(r.ok);
        return r.score;
    }
}
