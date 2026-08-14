package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 「计分牌口径」三处微边界（R134，第 64 处修复族）：
 * ①Flowerpot 只看计分且非 debuff 牌（R130 只改 desc 漏改循环）；
 * ②Seeing Double 的 debuffed 计分牌不计（Wiki 明示）；
 * ③Burnt 不被钩子 Boss 强弃激活（Wiki："The Hook's forced discards won't activate it"）。
 */
class ScoredCardsScopesTest {

    @Test
    void flowerpotIgnoresUnscoredFourthSuit() {
        // 同种子双局：普通局 vs 带 flowerpot——出牌含四花色但第四花色牌【不计分】时不得 ×3
        // 用高牌（1 计分牌）无法测四花色；改为：两对（4 计分牌）三花色 + 未计分第四花色
        RunState base = Engine.createRun("red", 0, "FPB1", null);
        Engine.selectBlind(base, Data.BlindType.SMALL, false);
        setUpTwoPairThreeSuits(base);
        long bare = play5(base);

        RunState fp = Engine.createRun("red", 0, "FPB1", null);
        Engine.selectBlind(fp, Data.BlindType.SMALL, false);
        fp.jokers.add(JokerRegistry.create("flowerpot"));
        setUpTwoPairThreeSuits(fp);
        long with = play5(fp);
        assertEquals(bare, with, "未计分的第四花色不得触发花盆（×3 缺席 → 分数相等）");
    }

    @Test
    void seeingDoubleDebuffedNonClubDoesNotCount() {
        // 场景 A：计分对全为梅花（另一花色仅在未计分第 5 张）→ 不触发
        RunState a1 = Engine.createRun("red", 0, "SDD1", null);
        Engine.selectBlind(a1, Data.BlindType.SMALL, false);
        setAllClubPairPlusKicker(a1, false);
        long a1score = play5(a1);
        RunState a2 = Engine.createRun("red", 0, "SDD1", null);
        Engine.selectBlind(a2, Data.BlindType.SMALL, false);
        setAllClubPairPlusKicker(a2, false);
        a2.jokers.add(JokerRegistry.create("seeingdouble"));
        assertEquals(a1score, play5(a2), "A：无计分另一花色 → 重影不触发");

        // 场景 B：混合花色两对（正常应触发），但把计分黑桃 debuff → 不触发（Wiki：debuffed 不计）
        RunState b1 = Engine.createRun("red", 0, "SDD2", null);
        Engine.selectBlind(b1, Data.BlindType.SMALL, false);
        setAllClubPairPlusKicker(b1, true);
        long b1score = play5(b1);
        RunState b2 = Engine.createRun("red", 0, "SDD2", null);
        Engine.selectBlind(b2, Data.BlindType.SMALL, false);
        setAllClubPairPlusKicker(b2, true);
        b2.jokers.add(JokerRegistry.create("seeingdouble"));
        assertEquals(b1score, play5(b2), "B：另一花色计分牌被 debuff → 重影不触发（R134 修复）");
    }

    /** 计分两对=梅花对(+黑桃对当 mixed=true，其中黑桃对标记 debuff)；第 5 张任意。
     *  【先排序后赋值】——sortHand 会按点数重排，先赋值会被原牌组顶替（SDProbe 实证）。 */
    private static void setAllClubPairPlusKicker(RunState s, boolean mixed) {
        Engine.sortHand(s);
        s.hand.get(0).setRank(9); s.hand.get(0).setSuit(2);
        s.hand.get(1).setRank(9); s.hand.get(1).setSuit(2);
        s.hand.get(2).setRank(5); s.hand.get(2).setSuit(mixed ? 0 : 2);
        s.hand.get(3).setRank(5); s.hand.get(3).setSuit(mixed ? 0 : 2);
        if (mixed) { s.hand.get(2).setDebuff(true); s.hand.get(3).setDebuff(true); }
        s.hand.get(4).setRank(7); s.hand.get(4).setSuit(0);
    }

    @Test
    void burntNotActivatedByHookForcedDiscard() {
        RunState s = Engine.createRun("red", 0, "BHK1", null);
        s.bossQueue.clear(); s.bossQueue.add("hook");
        s.nextBlind = "boss";
        Engine.selectBlind(s, Data.BlindType.BOSS, false);
        s.jokers.add(JokerRegistry.create("burnt"));
        int lvl = s.handLevel(Data.HandType.PAIR);
        s.roundScore = 0;
        Engine.playHand(s, List.of(s.hand.get(0).id(), s.hand.get(1).id()));
        // 钩子强弃 2 张（很可能构成对子）——Burnt 不得升级
        assertEquals(lvl, s.handLevel(Data.HandType.PAIR),
                "钩子强弃不激活烧焦（Wiki 明示）；discardsUsedThisRound 未增，守卫不足以挡——本次已显式跳过");
    }

    private static void setUpTwoPairThreeSuits(RunState s) {
        Engine.sortHand(s); // 先排序后赋值（同上）
        s.hand.get(0).setRank(9); s.hand.get(0).setSuit(0);
        s.hand.get(1).setRank(9); s.hand.get(1).setSuit(1);
        s.hand.get(2).setRank(5); s.hand.get(2).setSuit(2);
        s.hand.get(3).setRank(5); s.hand.get(3).setSuit(2);
        s.hand.get(4).setRank(7); s.hand.get(4).setSuit(3); // 未计分的第四花色
    }

    private static long play5(RunState s) {
        List<Integer> ids = List.of(s.hand.get(0).id(), s.hand.get(1).id(),
                s.hand.get(2).id(), s.hand.get(3).id(), s.hand.get(4).id());
        Engine.PlayResult r = Engine.playHand(s, ids);
        assertTrue(r.ok);
        return r.score;
    }
}
