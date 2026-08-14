package cn.quotidietium.balatro.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HandEval 极端手牌组合边界回归测试（R76 新增）。
 *
 * <p>覆盖先前由代码追踪验证但无专门测试的路径：
 * <ul>
 *   <li>5 张万能牌 → FFIVE（同花五条）</li>
 *   <li>万能牌参与同花（万能牌适配任意花色）</li>
 *   <li>万能牌的 rank 固定（不因万能而适配任意点数参与顺子）</li>
 *   <li>Four Fingers + Ace-low 顺子（A-2-3-4 四张即可）</li>
 *   <li>全石头手牌 → HIGH，5 张均计分</li>
 * </ul>
 */
class HandEvalEdgeCaseTest {

    private RunState stateWith(Map<String, Object> flags) {
        RunState s = new RunState("x");
        s.flags = flags != null ? flags : new HashMap<>();
        return s;
    }

    private Card card(int id, int rank, int suit) {
        return new Card(id, rank, suit);
    }

    private Card wildCard(int id, int rank, int suit) {
        Card c = new Card(id, rank, suit);
        c.setEnh(Data.Enhancement.WILD);
        return c;
    }

    private Card stoneCard(int id) {
        Card c = new Card(id, 0, -1);
        c.setEnh(Data.Enhancement.STONE);
        return c;
    }

    /** 5 张万能牌（同 rank）→ FFIVE（同花五条）：万能牌适配任意花色凑同花 + 五条。 */
    @Test
    void fiveWildCardsIsFlushFive() {
        RunState s = stateWith(null);
        List<Card> cards = List.of(
                wildCard(1, 7, 0), wildCard(2, 7, 0), wildCard(3, 7, 0),
                wildCard(4, 7, 0), wildCard(5, 7, 0));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertEquals(Data.HandType.FFIVE, r.type, "5 张万能牌（同 rank）应为同花五条");
        assertEquals(5, r.scoring.size(), "5 张应全计分");
    }

    /** 万能牌可适配任意花色参与同花：3 黑桃 + 2 万能牌 → 同花。 */
    @Test
    void wildCardAdaptsSuitForFlush() {
        RunState s = stateWith(null);
        List<Card> cards = List.of(
                card(1, 2, 0), card(2, 4, 0), card(3, 6, 0),
                wildCard(4, 9, 1), wildCard(5, 11, 2));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertEquals(Data.HandType.FLUSH, r.type, "3 黑桃 + 2 万能牌应为同花");
        assertEquals(5, r.scoring.size(), "5 张应全计分（万能牌适配黑桃）");
    }

    /** 万能牌的 rank 固定：万能牌不因万能增强而适配任意 rank 参与顺子。
     *  {万能(rank=9), 2, 3, 4, 5} — 万能 rank=9 不在 2-5 顺子窗口内 → 非顺子。 */
    @Test
    void wildCardRankIsFixedForStraight() {
        RunState s = stateWith(null);
        // 万能牌 rank=9（非 6 也非 A），其余 2,3,4,5 无法与 9 构成顺子
        List<Card> cards = List.of(
                wildCard(1, 9, 0), card(2, 2, 1), card(3, 3, 1),
                card(4, 4, 1), card(5, 5, 1));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertTrue(r.type != Data.HandType.STRAIGHT, "万能牌 rank 固定，不应凑成顺子");
    }

    /** Four Fingers + Ace-low 顺子：{A, 2, 3, 4} + 1 张不相关 → 顺子（4 张即可）。 */
    @Test
    void fourFingersAceLowStraight() {
        Map<String, Object> f = new HashMap<>();
        f.put("fourFingers", true);
        RunState s = stateWith(f);
        // A(14), 2, 3, 4 + K（不相关）
        List<Card> cards = List.of(
                card(1, 14, 0), card(2, 2, 1), card(3, 3, 2),
                card(4, 4, 3), card(5, 13, 0));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertEquals(Data.HandType.STRAIGHT, r.type, "四指下 A-2-3-4 应为顺子");
        // A, 2, 3, 4 计分；K 不计分
        assertTrue(r.scoring.size() >= 4, "A,2,3,4 应计分");
    }

    /** 全石头手牌（5 张石头）→ HIGH，5 张石头全计分。 */
    @Test
    void allStoneHandIsHighAndAllScore() {
        RunState s = stateWith(null);
        List<Card> cards = List.of(
                stoneCard(1), stoneCard(2), stoneCard(3),
                stoneCard(4), stoneCard(5));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertEquals(Data.HandType.HIGH, r.type, "5 张石头牌应为高牌");
        assertEquals(5, r.scoring.size(), "5 张石头牌应全计分");
    }

    /** Four Fingers + 4 黑桃(含万能) → 同花（万能适配黑桃凑 4 张）。 */
    @Test
    void fourFingersWildCompletesFlush() {
        Map<String, Object> f = new HashMap<>();
        f.put("fourFingers", true);
        RunState s = stateWith(f);
        // 3 黑桃 + 1 万能(rank=8, suit=1→适配黑桃) + 1 红桃 → 四指下 4 张黑桃同花
        List<Card> cards = List.of(
                card(1, 2, 0), card(2, 4, 0), card(3, 6, 0),
                wildCard(4, 8, 1), card(5, 13, 1));
        HandEval.Result r = HandEval.evaluate(s, cards);
        assertEquals(Data.HandType.FLUSH, r.type, "四指下 3 黑桃+1 万能(适配黑桃)=4 黑桃应为同花");
    }
}
