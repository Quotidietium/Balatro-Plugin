package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import cn.quotidietium.balatro.engine.shop.Shop;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 租赁贴纸的经济语义（R124 修复回归，第 54 处；真版：Reddit/Steam/TV Tropes/Stickers）。
 *
 * <p>真版：租赁小丑**售价恒 $1**（"costs $1 to buy"）、**卖价恒 $1**（"sell for only $1"，
 * 蛋类 sellBonus 仍叠加）、持有每回合 -$3。REF 的 price-3 与 cost/2 均为 REF bug。
 */
class RentalEconomyTest {

    @Test
    void rentalJokerAlwaysCostsOneRegardlessOfBase() {
        // 金注下扫描足量商店：凡 rental 小丑标价必为 $1（$8 基价小丑也是 $1）
        boolean sawRental = false;
        for (int i = 0; i < 300 && !sawRental; i++) {
            RunState s = Engine.createRun("red", 7, "RENT" + i, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            s.roundScore = s.blindTarget;
            Engine.playHand(s, List.of(s.hand.get(0).id()));
            for (var c : s.shop.cards) {
                if ("joker".equals(c.kind) && c.joker != null && c.joker.rental) {
                    assertEquals(1, c.price, "租赁小丑售价恒 $1（基价 $" + c.joker.def.cost() + "）");
                    sawRental = true;
                }
            }
        }
        assertTrue(sawRental, "300 家商店应至少出现一个租赁小丑（30% 概率）");
    }

    @Test
    void rentalSellValueIsOnePlusSellBonus() {
        RunState s = Engine.createRun("red", 7, "RENTS1", null);
        JokerInstance rental = JokerRegistry.create("rocket"); // cost 6
        rental.rental = true;
        s.jokers.add(rental);
        assertEquals(1, s.sellValue(rental), "卖价恒 $1（非 6/2=3）");
        rental.sellBonus = 5; // 蛋类成长
        assertEquals(6, s.sellValue(rental), "sellBonus 仍叠加（1+5）");
        // 非租赁对照不受影响
        JokerInstance normal = JokerRegistry.create("rocket");
        s.jokers.add(normal);
        assertEquals(3, s.sellValue(normal), "非租赁 rocket 卖价 6/2=3");
    }

    @Test
    void rentalUpkeepThreePerRoundUnchanged() {
        RunState s = Engine.createRun("red", 7, "RENTU1", null);
        JokerInstance rental = JokerRegistry.create("joker");
        rental.rental = true;
        s.jokers.add(rental);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        long before = s.money;
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        // 金注叠红注：小盲奖励 $0；剩余出牌(3手)+$3 与租赁 -$3 恰好抵消；利息 $0（$4/5）
        assertEquals(before, s.money, "维护费 -$3 生效（被剩余出牌 +$3 抵消后的净额）");
    }
}
