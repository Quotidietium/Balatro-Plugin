package cn.quotidietium.balatro.engine.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 商店重掷的作用域 + egg 售价成长（R121）。
 *
 * <p>①reroll 只重掷**商品行**（shop.cards = genShopCardsPublic），优惠券与补充包**保持不变**
 * （真版行为：每家店的券固定、包不随重掷变化）；重掷后商品均为未售新条目。
 * <p>②egg：每回合结束 sellBonus +3，sellValue = cost/2 + sellBonus 随之成长。
 */
class RerollScopeTest {

    private static RunState inShop(String seed) {
        RunState s = Engine.createRun("red", 0, seed, null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertTrue(s.phase == Phase.SHOP);
        s.money = 10_000;
        return s;
    }

    @Test
    void rerollRegeneratesCardsButKeepsVouchersAndPacks() {
        RunState s = inShop("RRS1");
        List<String> voucherKeys = new ArrayList<>();
        for (var v : s.shop.vouchers) voucherKeys.add(v.voucher.key);
        List<String> packKeys = new ArrayList<>();
        for (var p : s.shop.packs) packKeys.add(p.pack.key);
        int cardN = s.shop.cards.size();

        // 先买空一件商品制造 sold 标记，再重掷
        Shop.buyCard(s, 0);
        assertTrue(s.shop.cards.get(0).sold, "已售标记就位");

        Shop.reroll(s);

        assertEquals(voucherKeys, keysOf(s), "重掷不得改变优惠券");
        assertEquals(packKeys.size(), s.shop.packs.size(), "重掷不得改变补充包");
        for (int i = 0; i < packKeys.size(); i++) {
            assertEquals(packKeys.get(i), s.shop.packs.get(i).pack.key, "包定义保持（i=" + i + "）");
        }
        assertEquals(cardN, s.shop.cards.size(), "商品数量不变");
        for (var c : s.shop.cards) {
            assertTrue(!c.sold, "重掷后商品均为未售新条目");
        }
        assertEquals(1, s.shop.rerollCount, "重掷计数 +1");
    }

    private static List<String> keysOf(RunState s) {
        List<String> ks = new ArrayList<>();
        for (var v : s.shop.vouchers) ks.add(v.voucher.key);
        return ks;
    }

    @Test
    void eggSellValueGrowsThreePerRoundEnd() {
        RunState s = inShop("EGG1");
        JokerInstance egg = JokerRegistry.create("egg");
        s.jokers.add(egg);
        int base = s.sellValue(egg); // cost/2（egg cost 2 → 1）
        // 模拟三个回合结束钩子
        for (int r = 0; r < 3; r++) egg.def.onRoundEnd(s, egg);
        assertEquals(base + 9, s.sellValue(egg), "每回合 +$3 售价，三回合 +9");
        long before = s.money;
        assertTrue(s.sellJoker(0), "出售");
        assertEquals(before + base + 9, s.money, "出售入账与卖价一致");
    }
}
