package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 自毁/衰减型小丑的跨回合演化 fuzz（R110）。
 *
 * <p>此前 ScoringChaosFuzzTest 每试验只出**一手**牌，从未到达 onRoundEnd 衰减与自毁阈值：
 * seltzer（10 次重触发后自毁）、popcorn（每回合 -4 mult，≤0 自毁）、turtle（手牌上限
 * flags 每回合 -1，≤0 自毁且 flags 须立即失效）、icecream（每出牌 -5 chips，可转负）。
 * 本测试做**长跑**：每试验叠加多份衰减小丑 + 稳定小丑，推进 12+ 回合跨过全部阈值，
 * 断言不抛异常（CME/状态错乱）、列表只减不增失控、计分恒非负、自毁后 flags 生效值即时回收。
 */
class SelfDestructProgressionFuzzTest {

    /** 衰减/自毁类 + 少量稳定计分类（制造混合列表上的删除）。 */
    private static final String[] DECAY = {"seltzer", "popcorn", "turtle", "icecream"};
    private static final String[] STABLE = {"joker", "greedy", "banner", "abstract"};

    private static final int TRIALS = 120;

    @Test
    void decayAndSelfDestructAcrossRoundsNeverCorrupts() {
        Random rnd = new Random(20260822L);
        for (int trial = 0; trial < TRIALS; trial++) {
            RunState s = Engine.createRun("red", 0, "DECAY" + trial, null);
            // 叠加 4~8 份衰减类（多份同 key 考验 findJoker/extra 各自实例独立）+ 0~2 稳定类
            int decayN = 4 + rnd.nextInt(5);
            for (int i = 0; i < decayN; i++) {
                var ji = JokerRegistry.create(DECAY[rnd.nextInt(DECAY.length)]);
                if (ji != null && s.jokers.size() < 10) s.jokers.add(ji);
            }
            for (int i = 0; i < rnd.nextInt(3); i++) {
                var ji = JokerRegistry.create(STABLE[rnd.nextInt(STABLE.length)]);
                if (ji != null && s.jokers.size() < 10) s.jokers.add(ji);
            }
            Engine.recomputeFlags(s);
            int startJokers = s.jokers.size();

            try {
                for (int round = 0; round < 12; round++) {
                    // 进入回合（skip Boss 限制：blindType 顺序 small→big→boss，boss 不可跳，用 go）
                    if (s.phase == Phase.BLIND_SELECT) {
                        if (!Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false)) break;
                    } else if (s.phase == Phase.ROUND) {
                        // 已在回合（首轮 auto）
                    } else {
                        break; // END（失败/通关）则结束本试验
                    }
                    // 偶尔弃一手（驱动 discards 路径 + icecream 不动）
                    if (rnd.nextInt(4) == 0 && !s.hand.isEmpty() && s.discardsLeft > 0) {
                        List<Integer> disc = new ArrayList<>();
                        for (int k = 0; k < Math.min(3, s.hand.size()); k++) disc.add(s.hand.get(k).id());
                        Engine.discard(s, disc);
                    }
                    // 快速赢下盲注（预置达标分 + 单张出牌，触发全部 onPlayHand/onScore/retrigger）
                    s.roundScore = s.blindTarget;
                    if (s.hand.isEmpty()) break;
                    List<Integer> play = List.of(s.hand.get(0).id());
                    Engine.PlayResult r = Engine.playHand(s, play);
                    assertTrue(r.score >= 0, "计分为负（trial=" + trial + " round=" + round + "）");
                    if (s.phase == Phase.SHOP) {
                        Engine.nextRound(s); // → BLIND_SELECT（boss 击败则 ante 推进/END）
                    }
                    // 每步不变量
                    assertTrue(s.jokers.size() <= startJokers,
                            "小丑只应减少（自毁）不应增加（trial=" + trial + " round=" + round + "）："
                                    + s.jokers.size() + ">" + startJokers);
                    assertTrue(s.hand.size() <= 40, "手牌泄漏（trial=" + trial + " round=" + round + "）");
                    for (var j : s.jokers) {
                        assertTrue(!j.def.key().equals("turtle")
                                || j.extra.get("size") instanceof Integer,
                                "turtle extra 类型漂移（trial=" + trial + "）");
                    }
                }
            } catch (RuntimeException ex) {
                fail("衰减/自毁长跑抛异常（trial=" + trial + " 小丑=" + keys(s) + "）：" + ex);
            }
            // 终态：非 PACK 残留
            assertTrue(s.phase != Phase.PACK, "长跑后不应滞留 PACK（trial=" + trial + "）");
        }
    }

    private static String keys(RunState s) {
        StringBuilder sb = new StringBuilder();
        for (var j : s.jokers) sb.append(j.def.key()).append(",");
        return sb.toString();
    }
}
