package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 三个挑战对齐真版机制的回归测试（R102，用户拍板「对齐原版机制」）。
 *
 * <p>REF 原版网页这 3 个挑战的描述与 mods 配置本身不一致（REF 上游缺陷），
 * 本项目按 balatrowiki.org（取自游戏文件）的真版规则重写：
 * <ul>
 *   <li><b>omelette 煎蛋卷</b>：All Blinds give no reward money / Extra Hands no longer earn
 *       money / Earn no Interest（三封禁）+ 5 蛋开局。</li>
 *   <li><b>city15 十五分钟城市</b>：Two copies of every face card, no Aces/2s/3s +
 *       开局永恒「乘公交」「捷径」（无免费重掷）。</li>
 *   <li><b>xray X 光视界</b>：1 in 4 cards are drawn face down（无手牌 -2）。</li>
 * </ul>
 */
class ChallengeRealMechanicsTest {

    @Test
    void omeletteBlocksAllThreeIncomeSources() {
        RunState s = Engine.createRun("red", 0, "REALOM1", "omelette");
        assertEquals(5, s.jokers.size(), "应开局 5 张蛋");
        assertEquals(4, s.money, "开局金钱仍为 $4");
        assertTrue(s.mods.noBlindReward && s.mods.noHandPay && s.mods.noInterest,
                "三重金钱封禁 mod 应全部生效");

        // 直接赢下小盲（预置达标分），endRound 应不加任何钱：
        // 盲注奖励 $3 ✗ + 剩余出牌（handsLeft 手）✗ + 利息 ✗ → money 保持 $4
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertEquals(4, s.money, "煎蛋卷：盲注奖励/剩余出牌/利息均不得产生金钱");
    }

    @Test
    void omeletteGoldCardStillPays() {
        // 真版仅封禁三种来源；黄金牌/出售/塔罗等其他来源不受影响
        RunState s = Engine.createRun("red", 0, "REALOM2", "omelette");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        for (Card c : s.hand) c.setEnh(Data.Enhancement.GOLD);
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertTrue(s.money > 4, "黄金牌手中 +$3 不在封禁之列（money=" + s.money + "）");
    }

    @Test
    void city15DeckIsFaceDoubled() {
        RunState s = Engine.createRun("red", 0, "REALCITY", "city15");
        assertEquals(52, s.fullDeck.size(), "牌组仍为 52 张");
        int face = 0, banned = 0;
        for (Card c : s.fullDeck) {
            if (c.rank() >= 11 && c.rank() <= 13) face++;
            if (c.rank() == 14 || c.rank() == 2 || c.rank() == 3) banned++;
        }
        assertEquals(24, face, "人头牌应翻倍：J/Q/K × 4 花色 × 2 = 24");
        assertEquals(0, banned, "不应有任何 A/2/3");
        // 开局永恒双小丑
        assertEquals(2, s.jokers.size(), "开局应有乘公交+捷径");
        assertEquals("ridebus", s.jokers.get(0).def.key());
        assertEquals("shortcut", s.jokers.get(1).def.key());
        assertTrue(s.jokers.get(0).eternal && s.jokers.get(1).eternal, "双小丑应为永恒");
        // 免费重掷已移除（真版无此机制）
        assertEquals(false, s.mods.freeReroll, "真版十五分钟城市无免费重掷");
    }

    @Test
    void omeletteBannedEconomyContentNeverOffered() {
        // 真版禁入清单（Wiki）：券=种子基金/摇钱树；小丑=奔月/火箭/黄金/卫星（R108）
        RunState s = Engine.createRun("red", 0, "REALBAN", "omelette");
        assertEquals(java.util.Set.of("seedmoney", "moneytree"), s.mods.bannedVouchers, "禁入券清单");
        assertEquals(java.util.Set.of("tothemoon", "rocket", "golden", "satellite"), s.mods.bannedJokers, "禁入小丑清单");

        // ① 随机小丑路径（wraith/soul/gainRandomJoker）：清空 5 蛋腾槽，反复随机获得，
        //    收集 key 断言与禁入集不相交
        s.jokers.clear();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 400 && s.jokers.size() < 10; i++) {
            int before = s.jokers.size();
            s.gainRandomJoker(null);
            if (s.jokers.size() > before) seen.add(s.jokers.get(s.jokers.size() - 1).def.key());
        }
        for (String k : seen) {
            assertTrue(!s.mods.bannedJokers.contains(k), "随机路径不得产出禁入小丑: " + k);
        }

        // ② 商店券池 + ③ 商店小丑位：反复开店收集，断言 disjoint
        java.util.Set<String> vouchersSeen = new java.util.HashSet<>();
        java.util.Set<String> shopJokersSeen = new java.util.HashSet<>();
        for (int round = 0; round < 60; round++) {
            cn.quotidietium.balatro.engine.shop.Shop.openShop(s);
            for (var v : s.shop.vouchers) vouchersSeen.add(v.voucher.key);
            for (var c : s.shop.cards) {
                if ("joker".equals(c.kind) && c.joker != null) shopJokersSeen.add(c.joker.def.key());
            }
        }
        for (String k : vouchersSeen) {
            assertTrue(!s.mods.bannedVouchers.contains(k), "商店不得出售禁入券: " + k);
        }
        for (String k : shopJokersSeen) {
            assertTrue(!s.mods.bannedJokers.contains(k), "商店不得出售禁入小丑: " + k);
        }
        // 非空性防退化（60 家店应有券；小丑池禁 4 只后仍极大）
        assertTrue(!vouchersSeen.isEmpty(), "应有券出现在样本中（防过滤误伤全池）");
    }

    @Test
    void xrayKeepsHandSizeAndDrawsFacedown() {
        RunState s = Engine.createRun("red", 0, "REALXRAY", "xray");
        assertEquals(8, s.handSizeBase, "真版 X 光视界无手牌上限 -2");
        assertEquals(false, s.mods.freeReroll);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 统计：反复弃 5 张重抽，累计大量抽牌，断言面朝下比例接近 1/4（宽松界 8%~45%，固定种子可复现）
        int draws = 0, down = 0;
        for (Card c : s.hand) { draws++; if (c.facedown()) down++; }
        for (int i = 0; i < 40 && s.discardsLeft > 0; i++) {
            var ids = new java.util.ArrayList<Integer>();
            for (int k = 0; k < Math.min(5, s.hand.size()); k++) ids.add(s.hand.get(k).id());
            Engine.discard(s, ids);
            for (Card c : s.hand) { draws++; if (c.facedown()) down++; }
        }
        assertTrue(draws >= 40, "应累计足够抽牌样本（draws=" + draws + "）");
        double ratio = (double) down / draws;
        assertTrue(ratio > 0.08 && ratio < 0.45,
                "面朝下比例应接近 1/4（" + down + "/" + draws + " = " + ratio + "）");
    }
}
