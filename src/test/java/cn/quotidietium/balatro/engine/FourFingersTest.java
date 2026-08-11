package cn.quotidietium.balatro.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Four Fingers（四指）规则验证：同花只需 4 张同花色，第 5 张可为任意花色（不阻止同花）；
 * 计分牌仅为同花色的牌。
 *
 * <p>REF JS 误要求"所有非万能牌同花色"，与真实规则不符，已修正。
 */
class FourFingersTest {

    private RunState stateWith(Map<String, Object> flags) {
        RunState s = new RunState("x");
        s.flags = flags != null ? flags : new HashMap<>();
        return s;
    }

    private Card card(int id, int rank, int suit) {
        return new Card(id, rank, suit);
    }

    @Test
    void fourCardFlushWithFourFingers() {
        // 4 黑桃(非顺子点数) + 1 红桃（异花色），四指 → 同花（非同花顺）
        Map<String, Object> f = new HashMap<>();
        f.put("fourFingers", true);
        RunState s = stateWith(f);
        List<Card> cards = List.of(card(1, 2, 0), card(2, 4, 0), card(3, 6, 0), card(4, 8, 0), card(5, 9, 1));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertEquals(Data.HandType.FLUSH, r.type, "四指下 4 同花+1 异花应为同花");
        assertEquals(4, r.scoring.size(), "仅 4 张同花色牌计分");
        for (Card c : r.scoring) assertEquals(0, c.suit(), "计分牌均应为黑桃");
    }

    @Test
    void noFlushWithoutFourFingers() {
        // 同样 4 黑桃 + 1 红桃，无四指 → 非同花
        RunState s = stateWith(new HashMap<>());
        List<Card> cards = List.of(card(1, 2, 0), card(2, 4, 0), card(3, 6, 0), card(4, 8, 0), card(5, 9, 1));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertTrue(r.type != Data.HandType.FLUSH, "无四指下 4 同花+1 异花不应为同花");
    }

    @Test
    void fourCardStraightWithFourFingers() {
        // 4 连续 + 1 异点，四指 → 顺子；计分仅 4 连续牌
        Map<String, Object> f = new HashMap<>();
        f.put("fourFingers", true);
        RunState s = stateWith(f);
        List<Card> cards = List.of(card(1, 2, 0), card(2, 3, 1), card(3, 4, 2), card(4, 5, 3), card(5, 9, 0));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertEquals(Data.HandType.STRAIGHT, r.type, "四指下 4 连续+1 异点应为顺子");
        assertEquals(4, r.scoring.size(), "仅 4 张构成顺子的牌计分");
    }

    @Test
    void threeSameSuitNotFlushEvenWithFourFingers() {
        // 仅 3 黑桃 + 2 红桃，四指也不够（需 4）
        Map<String, Object> f = new HashMap<>();
        f.put("fourFingers", true);
        RunState s = stateWith(f);
        List<Card> cards = List.of(card(1, 2, 0), card(2, 3, 0), card(3, 4, 0), card(4, 9, 1), card(5, 10, 1));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertTrue(r.type != Data.HandType.FLUSH, "仅 3 同花色不应为同花");
    }

    @Test
    void fourFingersRoyalExcludesOffsuitCardFromScoring() {
        // 四指：10JQK 黑桃（4 张构成 K-Q-J-10 皇家，全 ≥10）+ 异花 A。
        // 皇家成立（四指下 K-Q-J-10 同花即皇家，不要求 A），但异花 A 不应计分。
        Map<String, Object> f = new HashMap<>();
        f.put("fourFingers", true);
        RunState s = stateWith(f);
        List<Card> cards = List.of(card(1, 10, 0), card(2, 11, 0), card(3, 12, 0), card(4, 13, 0), card(5, 14, 1));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertEquals(Data.HandType.ROYAL, r.type, "四指下 10JQK 同花应为皇家（K-Q-J-10，全≥10）");
        assertEquals(4, r.scoring.size(), "仅 4 张同花色牌计分，异花 A 不计分");
        for (Card c : r.scoring) assertEquals(0, c.suit(), "计分牌均应为黑桃");
    }

    @Test
    void fourFingersStraightFlushExcludesOffsuitCardFromScoring() {
        // 四指：5-6-7-8 黑桃（4 张同花顺）+ 异花 K。异花 K 不应计分。
        Map<String, Object> f = new HashMap<>();
        f.put("fourFingers", true);
        RunState s = stateWith(f);
        List<Card> cards = List.of(card(1, 5, 0), card(2, 6, 0), card(3, 7, 0), card(4, 8, 0), card(5, 13, 1));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertEquals(Data.HandType.SFLUSH, r.type, "四指下 5-8 同花应为同花顺");
        assertEquals(4, r.scoring.size(), "仅 4 张同花色牌计分，异花 K 不计分");
        for (Card c : r.scoring) assertEquals(0, c.suit());
    }

    @Test
    void royalFlushWithoutFourFingersUnchanged() {
        // 无四指：10JQKA 全黑桃 → 皇家，5 张计分（非四指行为回归保险）
        RunState s = stateWith(new HashMap<>());
        List<Card> cards = List.of(card(1, 10, 0), card(2, 11, 0), card(3, 12, 0), card(4, 13, 0), card(5, 14, 0));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertEquals(Data.HandType.ROYAL, r.type);
        assertEquals(5, r.scoring.size());
    }
}
