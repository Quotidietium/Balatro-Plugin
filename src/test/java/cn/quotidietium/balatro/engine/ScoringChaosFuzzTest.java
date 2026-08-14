package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 计分端到端混沌模糊测试（R95）。
 *
 * <p>动机：EngineApiFuzzTest 覆盖了公开 API 不抛异常，但其 playHand 用随机 id（多为越界被拒），
 * 未能真正驱动「小丑 × 牌型 × 增强/版本/蜡封」的组合计分路径。本测试构造确定的手牌 + 随机
 * 计分小丑组合，强制走完整计分管线，捕获钩子在意外组合下的崩溃 / 负分 / roundScore 倒退。
 *
 * <p>每步后断言：score ≥ 0、roundScore 单调不减、手牌/小丑数量有界、不抛异常。
 */
class ScoringChaosFuzzTest {

    private static final int TRIALS = 200;

    /** 计分类小丑（触发 onScore/onScoreCard/onHeld，覆盖各钩子类型）。 */
    private static final String[] SCORING_JOKERS = {
            "joker", "greedy", "lusty", "wrathful", "gluttonous", "jolly", "zany", "crazy", "droll",
            "sly", "wily", "clever", "devious", "fibonacci", "scaryface", "abstract", "oddtodd",
            "evensteven", "scholar", "walkie", "faceless", "smiley", "ticket", "swashbuckler",
            "chad", "moon", "stuntman", "steel", "vagabond", "baron", "triboulet", "idol",
            "seeingdouble", "seance", "flowerpot", "blackboard", "photograph", "vampire", "hiker",
            "cardsharp", "madness", "constellation", "hologram", "arrowhead", "onyx", "glass",
            "gem", "bloodstone", "parking", "mailin", "certificate", "wee", "merry", "oops",
            "fortune", "luckycat", "baseball", "trousers", "flash", "ancient", "ramen", "castle",
            "campfire", "acrobat", "throwback", "sock", "troubadour", "duo", "trio", "family",
            "order", "tribe",
    };

    @Test
    void randomScoringJokerCombosNeverCrashOrNegativeScore() {
        Random rnd = new Random(20260816L);
        for (int trial = 0; trial < TRIALS; trial++) {
            RunState s = Engine.createRun("red", rnd.nextInt(8), "SCORE" + trial, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            // 随机装 0~5 个计分小丑（绕过槽位直接 add）
            int jcount = rnd.nextInt(6);
            for (int j = 0; j < jcount; j++) {
                var inst = cn.quotidietium.balatro.engine.joker.JokerRegistry.create(
                        SCORING_JOKERS[rnd.nextInt(SCORING_JOKERS.length)]);
                if (inst != null && s.jokers.size() < 10) s.jokers.add(inst);
            }
            Engine.recomputeFlags(s);
            // 给手牌随机加增强/版本/蜡封（覆盖 scoreOneCard 各分支）
            for (Card c : s.hand) {
                if (rnd.nextInt(3) == 0) c.setEnh(Data.Enhancement.values()[rnd.nextInt(8)]);
                if (rnd.nextInt(5) == 0) c.setEdition(Data.Edition.values()[rnd.nextInt(4)]);
                if (rnd.nextInt(6) == 0) c.setSeal(Data.Seal.values()[rnd.nextInt(4)]);
            }
            // 出 1~5 张手牌（从真实手牌取，保证计分真正发生）
            long scoreBefore = s.roundScore;
            List<Integer> playIds = new ArrayList<>();
            int playN = 1 + rnd.nextInt(Math.min(5, s.hand.size()));
            for (int i = 0; i < playN && i < s.hand.size(); i++) {
                playIds.add(s.hand.get(i).id());
            }
            try {
                Engine.PlayResult r = Engine.playHand(s, playIds);
                // 计分结果非负（即使有小丑 debuff/Boss，基础牌型分必为正）
                assertTrue(r.score >= 0, "score 为负（trial=" + trial + "）：" + r.score);
                if (r.ok) {
                    assertTrue(s.roundScore >= scoreBefore,
                            "roundScore 倒退（trial=" + trial + "）：" + scoreBefore + "→" + s.roundScore);
                }
            } catch (RuntimeException ex) {
                fail("计分抛异常（trial=" + trial + "，小丑数=" + s.jokers.size() + "）：" + ex);
            }
            // 不变量
            assertTrue(s.hand.size() <= 40, "手牌泄漏（trial=" + trial + "）：" + s.hand.size());
            assertTrue(s.jokers.size() <= 40, "小丑泄漏（trial=" + trial + "）：" + s.jokers.size());
            assertTrue(s.roundScore >= 0, "roundScore 为负（trial=" + trial + "）：" + s.roundScore);
        }
    }

    /**
     * R100：复制类小丑（blueprint/brainstorm）混入随机槽位的计分 fuzz。
     *
     * <p>复制类经 Engine.resolveCopy 触发**其他**小丑的钩子（蓝图=右邻、头脑风暴=最左），
     * 随机位置组合下：蓝图右邻可能是蓝图/头脑风暴（返回 null 跳过）、头脑风暴最左可能是
     * 自身（null）、被复制者与持有者版本加成的归属（R4：按持有者）等路径全部混沌覆盖。
     * 首个 fuzz 的随机池不含复制类，此为补充盲区。
     */
    @Test
    void copyJokersInRandomSlotsNeverCrash() {
        Random rnd = new Random(20260819L);
        String[] copyJokers = {"blueprint", "brainstorm"};
        for (int trial = 0; trial < TRIALS; trial++) {
            RunState s = Engine.createRun("red", 0, "COPY" + trial, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            // 随机装 2~6 个：计分小丑与复制类混合（复制类至少 1 个保证路径触发）
            int total = 2 + rnd.nextInt(5);
            boolean anyCopy = false;
            for (int j = 0; j < total; j++) {
                String key;
                if (!anyCopy && j == total - 1) {
                    key = copyJokers[rnd.nextInt(2)]; // 保证至少一个复制类
                } else {
                    key = rnd.nextInt(3) == 0 ? copyJokers[rnd.nextInt(2)] : SCORING_JOKERS[rnd.nextInt(SCORING_JOKERS.length)];
                }
                if ("blueprint".equals(key) || "brainstorm".equals(key)) anyCopy = true;
                var inst = cn.quotidietium.balatro.engine.joker.JokerRegistry.create(key);
                if (inst != null && s.jokers.size() < 10) s.jokers.add(inst);
            }
            Engine.recomputeFlags(s);
            // 手牌随机增强（让被复制的 onScoreCard/onScore 有区分度）
            for (Card c : s.hand) {
                if (rnd.nextInt(3) == 0) c.setEnh(Data.Enhancement.values()[rnd.nextInt(8)]);
            }
            long scoreBefore = s.roundScore;
            List<Integer> playIds = new ArrayList<>();
            int playN = 1 + rnd.nextInt(Math.min(5, s.hand.size()));
            for (int i = 0; i < playN && i < s.hand.size(); i++) playIds.add(s.hand.get(i).id());
            try {
                Engine.PlayResult r = Engine.playHand(s, playIds);
                assertTrue(r.score >= 0, "score 为负（trial=" + trial + "）：" + r.score);
                if (r.ok) {
                    assertTrue(s.roundScore >= scoreBefore,
                            "roundScore 倒退（trial=" + trial + "）");
                }
            } catch (RuntimeException ex) {
                fail("复制类组合计分抛异常（trial=" + trial + "，小丑=" + jokerKeys(s) + "）：" + ex);
            }
            assertTrue(s.jokers.size() <= 40, "小丑泄漏（trial=" + trial + "）");
            assertTrue(s.roundScore >= 0, "roundScore 为负（trial=" + trial + "）");
        }
    }

    private static String jokerKeys(RunState s) {
        StringBuilder sb = new StringBuilder();
        for (var j : s.jokers) sb.append(j.def.key()).append(",");
        return sb.toString();
    }
}
