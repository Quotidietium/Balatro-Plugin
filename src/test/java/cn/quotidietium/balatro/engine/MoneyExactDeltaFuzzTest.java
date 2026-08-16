package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R156：金钱逐操作**精确差额**守恒 fuzz——R152 只锁回合级下界，R105 只锁商店单点；
 * 本 fuzz 在可全预测的闭包内（无小丑持有/无券/无标签/无金蜡封/只打小·大盲注，
 * 无 Boss 金钱效果）对每一步操作断言 {@code money 变化 == 解析式预期}：
 *
 * <ul>
 *   <li>**endRound 收入解析式**：reward(小3/大4；红注小盲为 0) + 剩余出牌(出牌后)
 *       ——绿牌组替代为 2×剩余出牌+剩余弃牌—— + 利息 min(5, money/5)（红牌组；
 *       绿牌组无利息）；</li>
 *   <li>**商店逐笔**：reroll 费用恰为 5+已重掷次数且扣款精确；购买成功扣款恰为标价；
 *       已售/买不起路径扣款恰为 0；</li>
 *   <li>**nextRound/selectBlind 零金钱影响**；金针挑战（discardCost）每次弃牌恰 -$1。</li>
 * </ul>
 *
 * <p>任何「多扣/少扣/静默扣款」形态的数据操作 BUG 都会击穿对应断言。
 */
class MoneyExactDeltaFuzzTest {

    private static boolean playFirstValid(RunState s) {
        if (s.handsLeft <= 0 || s.hand.isEmpty()) return false;
        int n = Math.min(5, s.hand.size());
        List<Integer> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(s.hand.get(i).id());
        return Engine.playHand(s, ids).ok;
    }

    /** 打赢当前小/大盲注并断言 endRound 金钱差额 == 解析式预期。 */
    private static void winBlindAssertExactDelta(RunState s, boolean greenDeck) {
        assertTrue(s.phase == Phase.ROUND, "应在回合中");
        long moneyBefore = s.money;
        int handsBefore = s.handsLeft;      // 本手出牌消耗 1
        int discardsNow = s.discardsLeft;   // 出牌不改变弃牌数
        boolean small = s.blindType == Data.BlindType.SMALL;

        long reward = s.blindType.reward;
        if (s.mods.redStake && small) reward = 0;
        long handsLeftAfter = handsBefore - 1;
        long expected = reward;
        if (greenDeck) {
            expected += 2L * handsLeftAfter + discardsNow; // 绿牌组替代剩余出牌段，且无利息
        } else {
            if (handsLeftAfter > 0) expected += handsLeftAfter;
            if (moneyBefore > 0) expected += Math.min(5, moneyBefore / 5); // 利息上限 5
        }

        s.roundScore = s.blindTarget; // 任意合法出牌即胜
        assertTrue(playFirstValid(s), "合法出牌应成功");
        assertTrue(s.phase == Phase.SHOP, "胜利后应进商店（当前 " + s.phase + "）");
        assertEquals(expected, s.money - moneyBefore,
                "endRound 金钱差额 != 解析式预期（" + (small ? "小" : "大") + "盲注）");
    }

    @Test
    void endRoundAndShopTransactionsExactDelta() {
        for (int trial = 0; trial < 60; trial++) {
            boolean green = trial % 2 == 1;
            int stake = trial % 8 < 4 ? 0 : 1; // 红注变体：小盲奖励 0
            // R218：偶数试验注入重掷券（reroll1=−2 / reroll1+2=−4 / 无券 三态）
            String rerollVoucher = switch (trial % 6) {
                case 2 -> "reroll1";
                case 4 -> "reroll2";
                default -> null;
            };
            RunState s = Engine.createRun(green ? "green" : "red", stake, "MED2-" + trial, null);
            if (rerollVoucher != null) {
                s.vouchers.add(rerollVoucher);
                if ("reroll2".equals(rerollVoucher)) s.vouchers.add("reroll1"); // requires 链
            }

            // —— 小盲注 ——
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            winBlindAssertExactDelta(s, green);

            // —— 商店：reroll 精确扣款（R218：含券路径 max(0, 5+次数−券减免)）——
            long m0 = s.money;
            long discount = s.vouchers.contains("reroll1") ? 2 : 0;
            discount += s.vouchers.contains("reroll2") ? 2 : 0;
            long expectedCost = Math.max(0, 5 + s.shop.rerollCount - (int) discount);
            long cost = cn.quotidietium.balatro.engine.shop.Shop.reroll(s);
            assertEquals(expectedCost, cost, "重掷费用应恰为 max(0,5+次数−券减免)（券=" + rerollVoucher + "）");
            assertEquals(m0 - expectedCost, s.money, "重掷扣款应精确等于费用（券=" + rerollVoucher + "）");

            // —— 商店：购买成功路径精确扣款（买第一个可负担的未售**非小丑**项——
            // 小丑的 onRoundEnd 收入不在本闭包的解析式内）——
            var cards = s.shop.cards;
            int buyIdx = -1;
            for (int i = 0; i < cards.size(); i++) {
                if (!cards.get(i).sold && !"joker".equals(cards.get(i).kind)
                        && s.money >= cards.get(i).price) { buyIdx = i; break; }
            }
            if (buyIdx >= 0) {
                long m1 = s.money;
                long price = cards.get(buyIdx).price;
                assertTrue(cn.quotidietium.balatro.engine.shop.Shop.buyCard(s, buyIdx), "购买应成功");
                assertEquals(m1 - price, s.money, "购买扣款应精确等于标价");
                assertTrue(cards.get(buyIdx).sold, "购买后应标记已售");
                // 失败路径 1：重买已售项 → 扣款恰 0
                long m2 = s.money;
                assertFalse(cn.quotidietium.balatro.engine.shop.Shop.buyCard(s, buyIdx), "已售项不可再买");
                assertEquals(m2, s.money, "已售项购买尝试扣款应恰为 0");
            }
            // 失败路径 2：买不起（或仅有小丑可选）→ 扣款恰 0
            int poorIdx = -1;
            for (int i = 0; i < cards.size(); i++) {
                if (!cards.get(i).sold && !"joker".equals(cards.get(i).kind)
                        && cards.get(i).price > s.money) { poorIdx = i; break; }
            }
            if (poorIdx >= 0) {
                long m3 = s.money;
                assertFalse(cn.quotidietium.balatro.engine.shop.Shop.buyCard(s, poorIdx), "买不起应拒绝");
                assertEquals(m3, s.money, "买不起尝试扣款应恰为 0");
            }

            // —— nextRound + 开打下一盲注：零金钱影响 ——
            long m4 = s.money;
            assertTrue(Engine.nextRound(s));
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            assertEquals(m4, s.money, "nextRound/selectBlind 不应影响金钱");

            // —— 大盲注（小盲后必为大盲）——
            winBlindAssertExactDelta(s, green);
            assertTrue(Engine.nextRound(s)); // 停在 Boss 选择，不打 Boss
        }
    }

    @Test
    void goldenChallengeDiscardCostsExactlyOneDollar() {
        RunState s = Engine.createRun("red", 0, "MEDGOLD", "golden");
        Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
        assertTrue(s.phase == Phase.ROUND);
        long m = s.money;
        List<Integer> ids = List.of(s.hand.get(0).id());
        assertTrue(Engine.discard(s, ids).ok, "弃牌应成功");
        assertEquals(m - 1, s.money, "金针挑战每次弃牌应恰扣 $1");
    }
}
