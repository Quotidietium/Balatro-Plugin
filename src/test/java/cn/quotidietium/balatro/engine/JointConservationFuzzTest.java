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
 * R158：守恒模板跨维联合 fuzz——卡（id 多重集）+ 钱（逐操作精确差额）+ 槽（占用）
 * 三不变量在**同一局**的交错动作下同时成立：每步动作后同时核对三维。
 *
 * <p>跨维扰动设计：商店买包→开包选牌（牌组被商店动作改动，卡守恒须仍成立）；
 * 消耗品使用（hermit/temperance 改钱、hanged 销毁补牌改卡、ouija 改槽外状态）；
 * 商店购买消耗品（钱-槽联动）；endRound 闭式收入（钱）在消耗品/商店动作之后仍精确。
 * 金钱闭包沿用 R156：无小丑/无券/红绿牌组/小大盲注；hermit/temperance 差额可解析。
 */
class JointConservationFuzzTest {

    /** R158 精化：三堆 ∪ 已选未发的包牌 id ≡ fullDeck（包内 playing 牌经 addCardToDeck
     *  只入牌组、下回合 startRound 洗入 drawPile——真版行为，非缺陷）。 */
    private static void assertCardsWithPending(RunState s, List<Integer> pendingPicked, String where) {
        Map<Integer, Integer> piles = new HashMap<>();
        for (Card c : s.hand) piles.merge(c.id(), 1, Integer::sum);
        for (Card c : s.drawPile) piles.merge(c.id(), 1, Integer::sum);
        for (Card c : s.discardPile) piles.merge(c.id(), 1, Integer::sum);
        for (int id : pendingPicked) piles.merge(id, 1, Integer::sum);
        Map<Integer, Integer> deck = new HashMap<>();
        for (Card c : s.fullDeck) deck.merge(c.id(), 1, Integer::sum);
        assertEquals(deck, piles, "卡守恒+包牌暂存（" + where + "）");
    }

    private static void assertCards(RunState s, String where) {
        Map<Integer, Integer> piles = new HashMap<>();
        for (Card c : s.hand) piles.merge(c.id(), 1, Integer::sum);
        for (Card c : s.drawPile) piles.merge(c.id(), 1, Integer::sum);
        for (Card c : s.discardPile) piles.merge(c.id(), 1, Integer::sum);
        Map<Integer, Integer> deck = new HashMap<>();
        for (Card c : s.fullDeck) deck.merge(c.id(), 1, Integer::sum);
        assertEquals(deck, piles, "卡守恒（" + where + "）");
        for (var e : piles.entrySet()) assertTrue(e.getValue() == 1, "跨堆重复（" + where + "）");
    }

    private static void assertSlots(RunState s, String where) {
        int neg = 0;
        for (Consumable c : s.consumables) if (c.edition == Data.Edition.NEGATIVE) neg++;
        assertTrue(s.consumables.size() <= s.consumableSlots + neg, "槽占用（" + where + "）");
    }

    /** 金钱差额断言：期望值由调用方按动作闭式给出。 */
    private static void assertMoneyDelta(long before, RunState s, long expected, String where) {
        assertEquals(expected, s.money - before, "金钱差额（" + where + "）");
    }

    private static boolean playFirst(RunState s) {
        if (s.handsLeft <= 0 || s.hand.isEmpty()) return false;
        int n = Math.min(5, s.hand.size());
        List<Integer> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(s.hand.get(i).id());
        return Engine.playHand(s, ids).ok;
    }

    @Test
    void threeDimensionInvariantsHoldTogether() {
        Random rnd = new Random(20260818L);
        for (int trial = 0; trial < 30; trial++) {
            boolean green = trial % 2 == 1;
            RunState s = Engine.createRun(green ? "green" : "red", 0, "JOINT" + trial, null);
            s.consumableSlots = 2;
            List<Integer> pendingPicked = new ArrayList<>(); // 已选未发的包内游戏牌（下回合洗入）

            // —— 小盲注：回合内交错（出牌/弃牌/改钱消耗品/改卡消耗品）——
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            for (int step = 0; step < 6 && s.phase == Phase.ROUND; step++) {
                int act = rnd.nextInt(4);
                long m0 = s.money;
                if (act == 0) {
                    playFirst(s);
                    // 幸运牌型可能一手直接赢下小盲（如 trial26 的 328≥300）→ endRound 收入
                    // 属下段闭式断言域；仅当回合仍在时出牌本身才必为零差额
                    if (s.phase == Phase.ROUND) {
                        assertMoneyDelta(m0, s, 0, "出牌本身不扣钱 trial=" + trial);
                    }
                } else if (act == 1 && s.discardsLeft > 0 && !s.hand.isEmpty()) {
                    Engine.discard(s, List.of(s.hand.get(0).id()));
                    assertMoneyDelta(m0, s, 0, "弃牌不扣钱");
                } else if (act == 2) {
                    // hermit：+min(20, max(0, money))（可解析改钱消耗品）
                    s.consumables.clear();
                    s.consumables.add(new Consumable("tarot", "hermit"));
                    long expect = Math.min(20, Math.max(0, m0));
                    assertTrue(cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, List.of()).ok);
                    assertMoneyDelta(m0, s, expect, "hermit 收入解析式");
                } else {
                    // hanged：销毁 1 张手牌并补满（改卡消耗品，钱不变）
                    if (s.hand.size() >= 2) {
                        s.consumables.clear();
                        s.consumables.add(new Consumable("tarot", "hanged"));
                        int target = s.hand.get(0).id();
                        assertTrue(cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, List.of(target)).ok);
                        assertMoneyDelta(m0, s, 0, "hanged 不改钱");
                    }
                }
                assertCards(s, "回合步 trial=" + trial + " step=" + step);
                assertSlots(s, "回合步 trial=" + trial + " step=" + step);
            }
            // 赢下小盲注：endRound 闭式（R156 同式）
            if (s.phase == Phase.ROUND) {
                long m0 = s.money;
                int hAfter = s.handsLeft - 1;
                long expected = Data.BlindType.SMALL.reward + (green ? 2L * hAfter + s.discardsLeft : (hAfter > 0 ? hAfter : 0) + (m0 > 0 ? Math.min(5, m0 / 5) : 0));
                s.roundScore = s.blindTarget;
                assertTrue(playFirst(s));
                assertTrue(s.phase == Phase.SHOP);
                assertMoneyDelta(m0, s, expected, "小盲 endRound 闭式");
            }

            // —— 商店：钱-槽-卡三维联动 ——
            if (s.phase == Phase.SHOP) {
                // R189：补 pending 源② 覆盖——买一张商店 playing 牌（只入牌组不入三堆，
                // 与选包牌同为「已入组未发」，入 pendingPicked 清单精确跟踪）
                for (int ci = 0; ci < s.shop.cards.size(); ci++) {
                    var c = s.shop.cards.get(ci);
                    if (!c.sold && "playing".equals(c.kind) && s.money >= c.price) {
                        long mb = s.money;
                        assertTrue(cn.quotidietium.balatro.engine.shop.Shop.buyCard(s, ci), "买游戏牌应成功");
                        assertMoneyDelta(mb, s, -c.price, "买游戏牌精确扣款");
                        pendingPicked.add(c.card.id());
                        assertCardsWithPending(s, pendingPicked, "商店买游戏牌");
                        break;
                    }
                }
                long m1 = s.money;
                long expectReroll = 5 + s.shop.rerollCount;
                assertEquals(expectReroll, cn.quotidietium.balatro.engine.shop.Shop.reroll(s));
                assertMoneyDelta(m1, s, -expectReroll, "重掷精确扣款");

                // 买包（若可负担）→ 开包选第一张可选（卡/槽在商店动作扰动下守恒）
                for (int pi = 0; pi < s.shop.packs.size(); pi++) {
                    var pack = s.shop.packs.get(pi);
                    if (!pack.sold && s.money >= pack.price && s.phase == Phase.SHOP) {
                        long m2 = s.money;
                        assertTrue(cn.quotidietium.balatro.engine.shop.Shop.buyPack(s, pi), "买包应成功");
                        assertMoneyDelta(m2, s, -pack.price, "买包精确扣款");
                        assertTrue(s.phase == Phase.PACK, "买包后应进开包");
                        int ci = 0;
                        while (s.phase == Phase.PACK && s.pack != null && ci < s.pack.cards.size()) {
                            String kind = s.pack.cards.get(ci).kind;
                            if (!"playing".equals(kind)) { ci++; continue; } // 只选游戏牌：小丑的金钱钩子/消耗品不可解析，闭包排除
                            int cardId = s.pack.cards.get(ci).card.id();
                            if (cn.quotidietium.balatro.engine.shop.Packs.pick(s, ci) && cardId >= 0) {
                                pendingPicked.add(cardId); // 游戏牌入组未发——暂存清单精确跟踪
                                // 闭包维持：清除包牌随机增强/版本/蜡封（金增强计分+$3 等会击穿
                                // 金钱闭式；清除后保留跨维卡流扰动、去除不可解析金钱源）
                                for (Card c : s.fullDeck) {
                                    if (c.id() == cardId) { c.setEnh(null); c.setEdition(null); c.setSeal(null); }
                                }
                            }
                            assertCardsWithPending(s, pendingPicked, "开包选卡");
                            assertSlots(s, "开包选卡");
                            ci++;
                        }
                        if (s.phase == Phase.PACK) cn.quotidietium.balatro.engine.shop.Packs.skip(s);
                        break;
                    }
                }
                if (s.phase == Phase.PACK) cn.quotidietium.balatro.engine.shop.Packs.skip(s);
                assertCardsWithPending(s, pendingPicked, "商店后段");
                assertSlots(s, "商店后段");
                long m3 = s.money;
                assertTrue(Engine.nextRound(s));
                Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                assertMoneyDelta(m3, s, 0, "nextRound 零影响");
                pendingPicked.clear(); // startRound 已把全部牌组洗入 drawPile——严格守恒恢复
                assertCards(s, "洗牌后严格守恒恢复");

                // —— 大盲注（速胜后止于 Boss 前）——
                if (s.phase == Phase.ROUND) {
                    long m4 = s.money;
                    int hAfter = s.handsLeft - 1;
                    long expected = Data.BlindType.BIG.reward + (green ? 2L * hAfter + s.discardsLeft : (hAfter > 0 ? hAfter : 0) + (m4 > 0 ? Math.min(5, m4 / 5) : 0));
                    s.roundScore = s.blindTarget;
                    assertTrue(playFirst(s));
                    assertMoneyDelta(m4, s, expected, "大盲 endRound 闭式");
                }
            }
            assertCards(s, "试验收尾 trial=" + trial);
            assertSlots(s, "试验收尾 trial=" + trial);
        }
    }
}
