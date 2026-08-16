package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R144：牌型歧义格优先级锁——同一手牌同时满足多个牌型条件时，判定结果必须是
 * {@code order} 字段更大的型（HandEval 的 if-else 链即按 order 降序编码）。
 *
 * <p>eval.txt 黄金锁的是**单型识别**（每型的典型构型），未显式锁定「共满足手」的
 * 优先级；chain 与 order 表任何一侧的漂移（如改表忘改链）都应在此失败。
 * 共满足对按可自然构造性枚举：五条/同花族经 wild 构造（自然牌无法同花成对）。
 */
class HandPrecedenceOrderTest {

    private static RunState state() {
        return Engine.createRun("red", 0, "PRECEDENCE", null);
    }

    private static Card c(RunState s, int rank, int suit) {
        return s.makeCard(rank, suit);
    }

    private static Card wild(RunState s, int rank, int suit) {
        Card card = s.makeCard(rank, suit);
        card.setEnh(Data.Enhancement.WILD);
        return card;
    }

    private static Data.HandType eval(RunState s, List<Card> hand) {
        return HandEval.evaluate(s, hand).type;
    }

    /** 断言共满足手中 winner 胜出，且其 order 高于落选型（链序=order 序的双向锁定）。 */
    private static void assertWins(RunState s, List<Card> hand, Data.HandType winner, Data.HandType loser) {
        assertEquals(winner, eval(s, hand), winner + " 应胜过 " + loser + "：" + hand);
        assertTrue(winner.order > loser.order, "order 表与链序一致性：" + winner + " 应 order > " + loser);
    }

    @Test
    void ambiguityLatticeFollowsOrderField() {
        RunState s = state();

        // 同花顺 > 同花 / 顺子 / 高牌（2-6 同花）
        List<Card> sf = List.of(c(s, 2, 0), c(s, 3, 0), c(s, 4, 0), c(s, 5, 0), c(s, 6, 0));
        assertWins(s, sf, Data.HandType.SFLUSH, Data.HandType.FLUSH);
        assertWins(s, sf, Data.HandType.SFLUSH, Data.HandType.STRAIGHT);
        assertWins(s, sf, Data.HandType.SFLUSH, Data.HandType.HIGH);

        // 皇家 > 同花（10-A 同花）
        List<Card> royal = List.of(c(s, 10, 0), c(s, 11, 0), c(s, 12, 0), c(s, 13, 0), c(s, 14, 0));
        assertWins(s, royal, Data.HandType.ROYAL, Data.HandType.FLUSH);

        // 四条 > 三条 / 对子（3,3,3,3,5 混花）
        List<Card> four = List.of(c(s, 3, 0), c(s, 3, 1), c(s, 3, 2), c(s, 3, 3), c(s, 5, 0));
        assertWins(s, four, Data.HandType.FOUR, Data.HandType.THREE);
        assertWins(s, four, Data.HandType.FOUR, Data.HandType.PAIR);

        // 葫芦 > 三条 / 两对 / 对子
        List<Card> full = List.of(c(s, 7, 0), c(s, 7, 1), c(s, 7, 2), c(s, 9, 3), c(s, 9, 0));
        assertWins(s, full, Data.HandType.FULL, Data.HandType.THREE);
        assertWins(s, full, Data.HandType.FULL, Data.HandType.TWOPAIR);
        assertWins(s, full, Data.HandType.FULL, Data.HandType.PAIR);

        // 同花 > 高牌；顺子 > 高牌；三条 > 对子；两对 > 对子
        List<Card> flush = List.of(c(s, 2, 0), c(s, 4, 0), c(s, 6, 0), c(s, 8, 0), c(s, 13, 0));
        assertWins(s, flush, Data.HandType.FLUSH, Data.HandType.HIGH);
        List<Card> straight = List.of(c(s, 2, 0), c(s, 3, 1), c(s, 4, 2), c(s, 5, 3), c(s, 6, 0));
        assertWins(s, straight, Data.HandType.STRAIGHT, Data.HandType.HIGH);
        List<Card> three = List.of(c(s, 8, 0), c(s, 8, 1), c(s, 8, 2), c(s, 2, 3), c(s, 3, 0));
        assertWins(s, three, Data.HandType.THREE, Data.HandType.PAIR);
        List<Card> twoPair = List.of(c(s, 5, 0), c(s, 5, 1), c(s, 9, 2), c(s, 9, 3), c(s, 13, 0));
        assertWins(s, twoPair, Data.HandType.TWOPAIR, Data.HandType.PAIR);

        // wild 构造的 13 型顶层：五条（无同花）/ 同花葫芦 / 同花五条
        List<Card> five = new ArrayList<>(List.of(c(s, 7, 0), c(s, 7, 1), c(s, 7, 2), c(s, 7, 3)));
        five.add(wild(s, 7, 0)); // 4 异花自然 + 1 wild：无 5 同花 → 五条
        assertWins(s, five, Data.HandType.FIVE, Data.HandType.THREE);
        List<Card> fhouse = new ArrayList<>(List.of(c(s, 7, 0), wild(s, 7, 1), wild(s, 7, 2), c(s, 9, 0), wild(s, 9, 1)));
        assertWins(s, fhouse, Data.HandType.FHOUSE, Data.HandType.FULL);
        assertWins(s, fhouse, Data.HandType.FHOUSE, Data.HandType.FLUSH);
        List<Card> ffive = new ArrayList<>(List.of(c(s, 7, 0), wild(s, 7, 1), wild(s, 7, 2), wild(s, 7, 3), wild(s, 7, 1)));
        assertWins(s, ffive, Data.HandType.FFIVE, Data.HandType.FIVE);
        assertWins(s, ffive, Data.HandType.FFIVE, Data.HandType.FOUR);
    }
}
