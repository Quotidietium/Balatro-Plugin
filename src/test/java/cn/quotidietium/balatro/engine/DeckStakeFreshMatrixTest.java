package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * R201：牌组 × 赌注 × 新种子矩阵 smoke——FullMatrix 用固定种子矩阵；
 * 本轮以全新种子族（DSFM-*）覆盖 **15 牌组 × 8 赌注全叉叉** 各 1 新种子
 *（120 局），每局速胜推进盲注（最多 3 盲注），断言不崩 + 卡守恒（洗牌后严格式
 * 或大理石差）+ 金钱下界。这是「新种子探索三部曲」的矩阵面（R186 确定性 /
 * R187 挑战 / 本轮牌组×赌注）。
 */
class DeckStakeFreshMatrixTest {

    private static boolean playFirst(RunState s) {
        if (s.handsLeft <= 0 || s.hand.isEmpty()) return false;
        int n = Math.min(5, s.hand.size());
        List<Integer> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(s.hand.get(i).id());
        return Engine.playHand(s, ids).ok;
    }

    @Test
    void deckStakeCrossFreshSeedsHoldInvariants() {
        String[] decks = {"red", "blue", "yellow", "green", "black", "magic", "nebula",
                "ghost", "abandoned", "checkered", "zodiac", "painted", "anaglyph", "plasma", "erratic"};
        for (int d = 0; d < decks.length; d++) {
            for (int stake = 0; stake <= 7; stake++) {
                RunState s = Engine.createRun(decks[d], stake, "DSFM-" + d + "-" + stake, null);
                for (int blind = 0; blind < 3; blind++) {
                    if (s.phase == Phase.END) break;
                    if (s.phase == Phase.BLIND_SELECT) {
                        Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                    }
                    if (s.phase != Phase.ROUND) break;
                    // 洗牌后回合入口：守恒（严格式，或带 marble 差——本矩阵无挑战故 marble=0）
                    Map<Integer, Integer> piles = new HashMap<>();
                    for (Card c : s.hand) piles.merge(c.id(), 1, Integer::sum);
                    for (Card c : s.drawPile) piles.merge(c.id(), 1, Integer::sum);
                    for (Card c : s.discardPile) piles.merge(c.id(), 1, Integer::sum);
                    Map<Integer, Integer> deck = new HashMap<>();
                    for (Card c : s.fullDeck) deck.merge(c.id(), 1, Integer::sum);
                    assertTrue(deck.equals(piles),
                            "回合入口守恒（" + decks[d] + "/" + stake + " blind#" + blind + "）");
                    s.roundScore = s.blindTarget;
                    int guard = 0;
                    while (s.phase == Phase.ROUND && guard++ < 25) {
                        if (playFirst(s)) break;
                        if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                            Engine.discard(s, List.of(s.hand.get(0).id()));
                        } else break;
                    }
                    if (s.phase == Phase.SHOP) Engine.nextRound(s);
                }
                assertTrue(s.money >= 0, "金钱下界（" + decks[d] + "/" + stake + "）：" + s.money);
            }
        }
    }
}
