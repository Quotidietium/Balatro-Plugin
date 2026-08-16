package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * R155：卡牌守恒混沌 fuzz——「数据操作 BUG」的核心不变量：
 * <b>任意时刻 hand ∪ drawPile ∪ discardPile 的卡 id 多重集 ≡ fullDeck 的卡 id 多重集</b>。
 *
 * <p>removeCardFromDeck/removeCardFromHand 类路径（玻璃破碎/绞刑/第六感/ familiars 销毁）
 * 应同步从牌组移除；addCardToDeck 类路径（幻灵生成/克隆）应同步入牌组。任何一张卡的
 * **复制**（多重集多出）或**丢失**（多重集缺失）都直接击穿本断言。
 *
 * <p>动作池：随机出牌（1~5 张）· 随机弃牌 · 注入并使用销毁/生成型消耗品（绞刑/
 * famaliar·grim·incantation / cryptid / sigil / ouija）· 给手牌随机上玻璃增强后出牌
 * （随机破碎路径）。固定种子可复现。
 */
class CardConservationFuzzTest {

    private static final String[] SPECTRALS = {
            "hanged", "familiar", "grim", "incantation", "cryptid", "sigil", "ouija", "immolate"};

    private static void assertConserved(RunState s, String where) {
        Map<Integer, Integer> piles = new HashMap<>();
        for (Card c : s.hand) piles.merge(c.id(), 1, Integer::sum);
        for (Card c : s.drawPile) piles.merge(c.id(), 1, Integer::sum);
        for (Card c : s.discardPile) piles.merge(c.id(), 1, Integer::sum);
        Map<Integer, Integer> deck = new HashMap<>();
        for (Card c : s.fullDeck) deck.merge(c.id(), 1, Integer::sum);
        assertEquals(deck, piles, "卡牌多重集守恒被击穿（" + where + "）：牌组 vs 三堆");
        for (var e : piles.entrySet()) {
            assertTrue(e.getValue() == 1, "同一 id 出现在多个堆（" + where + "）：" + e.getKey());
        }
    }

    @Test
    void cardMultisetConservedUnderRandomPlay() {
        Random rnd = new Random(20260822L); // R216 扩展
        for (int trial = 0; trial < 300; trial++) {
            RunState s = Engine.createRun("red", 0, "CONSV2-" + trial, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            assertConserved(s, "开局 trial=" + trial);
            for (int step = 0; step < 40; step++) {
                int act = rnd.nextInt(10);
                try {
                    if (act <= 3 && !s.hand.isEmpty() && s.phase == Phase.ROUND) {
                        // 随机出牌（部分牌随机上玻璃 → 触发随机破碎销毁路径）
                        int n = 1 + rnd.nextInt(Math.min(5, s.hand.size()));
                        List<Integer> ids = new ArrayList<>(n);
                        for (int i = 0; i < n; i++) {
                            Card c = s.hand.get(rnd.nextInt(s.hand.size()));
                            if (rnd.nextInt(4) == 0) c.setEnh(Data.Enhancement.GLASS);
                            if (!ids.contains(c.id())) ids.add(c.id());
                        }
                        Engine.playHand(s, ids);
                    } else if (act <= 6 && s.discardsLeft > 0 && !s.hand.isEmpty() && s.phase == Phase.ROUND) {
                        int n = 1 + rnd.nextInt(Math.min(5, s.hand.size()));
                        List<Integer> ids = new ArrayList<>(n);
                        for (int i = 0; i < n; i++) {
                            Card c = s.hand.get(rnd.nextInt(s.hand.size()));
                            if (!ids.contains(c.id())) ids.add(c.id());
                        }
                        Engine.discard(s, ids);
                    } else if (s.phase == Phase.ROUND && !s.hand.isEmpty()) {
                        // 注入随机幻灵并使用（销毁/生成/改写路径）
                        s.consumables.clear();
                        String key = SPECTRALS[rnd.nextInt(SPECTRALS.length)];
                        s.consumables.add(new cn.quotidietium.balatro.engine.Consumable("spectral", key));
                        List<Integer> targets = new ArrayList<>();
                        if (!s.hand.isEmpty()) {
                            int t = rnd.nextInt(2) + 1;
                            for (int i = 0; i < t && i < s.hand.size(); i++) {
                                Card c = s.hand.get(rnd.nextInt(s.hand.size()));
                                if (!targets.contains(c.id())) targets.add(c.id());
                            }
                        }
                        cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, targets);
                    }
                } catch (RuntimeException ex) {
                    throw new AssertionError("动作抛异常 trial=" + trial + " step=" + step
                            + "：" + ex, ex);
                }
                assertConserved(s, "trial=" + trial + " step=" + step);
                // 回合打完推进（胜利/失败/商店路径都不应破坏守恒）
                if (s.phase == Phase.ROUND && s.handsLeft <= 0) {
                    if (s.roundScore >= s.blindTarget) { /* endRound 已由 playHand 触发 */ }
                }
                if (s.phase == Phase.SHOP) {
                    cn.quotidietium.balatro.engine.shop.Shop.openShop(s);
                    Engine.nextRound(s);
                    if (s.phase == Phase.BLIND_SELECT) {
                        Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                    }
                }
                if (s.phase != Phase.ROUND) break; // 失败/终局即止
            }
        }
    }
}
