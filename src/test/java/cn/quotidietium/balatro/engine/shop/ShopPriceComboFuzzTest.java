package cn.quotidietium.balatro.engine.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 商店价格组合（重掷 × 优惠券 × 通胀）fuzz + rerollBoss×director/retcon 路径核验（R105）。
 *
 * <p>锁定：
 * <ul>
 *   <li>shopPrice 公式等价（liquidation/clearance else-if 链 → shopDiscount 乘 → +inflation → max(1)）
 *       与恒 ≥1 下限，在随机券组合/折扣/通胀下成立；</li>
 *   <li>buyCard 扣款精确等于商品标价（无隐藏重算漂移）；</li>
 *   <li>reroll 费用推进（5,6,7…；reroll1+reroll2 各 -2；freeRerolls 优先归零）与扣款精确性——
 *       与 REF rerollShop 逐行一致（L1380-1394）；</li>
 *   <li>rerollBoss 无券拒绝 / director 扣 $10 / retcon 免费 / 双券免费 / 贫穷拒绝不扣款。</li>
 * </ul>
 */
class ShopPriceComboFuzzTest {

    @Test
    void shopPriceFormulaHoldsAcrossRandomVoucherCombos() {
        Random rnd = new Random(20260821L);
        for (int trial = 0; trial < 3000; trial++) {
            RunState s = Engine.createRun("red", 0, "PRICE" + trial, null);
            if (rnd.nextBoolean()) s.vouchers.add("clearance");
            if (rnd.nextBoolean()) s.vouchers.add("liquidation"); // 双券：else-if 取 liquidation（升级链语义）
            double discount = rnd.nextInt(4) == 0 ? 0.6 : 0;
            s.mods.shopDiscount = discount;
            s.mods.inflation = rnd.nextInt(4) == 0;
            s.inflation = rnd.nextInt(30);
            long base = 1 + rnd.nextInt(50);

            long expected = base;
            if (s.vouchers.contains("liquidation")) expected = (long) Math.ceil(expected * 0.5);
            else if (s.vouchers.contains("clearance")) expected = (long) Math.ceil(expected * 0.75);
            if (discount != 0) expected = (long) Math.ceil(expected * discount);
            if (s.mods.inflation) expected += s.inflation;
            expected = Math.max(1, expected);

            long actual = Shop.shopPrice(s, base);
            assertEquals(expected, actual, "trial=" + trial + " base=" + base);
            assertTrue(actual >= 1, "价格恒 ≥1（trial=" + trial + "）");
        }
    }

    @Test
    void buyCardChargesExactlyDisplayedPrice() {
        RunState s = Engine.createRun("red", 0, "BUYEXACT", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertTrue(s.phase == Phase.SHOP);
        s.money = 10_000;
        for (int i = 0; i < s.shop.cards.size(); i++) {
            Shop.CardItem it = s.shop.cards.get(i);
            long before = s.money;
            long price = it.price;
            boolean ok = Shop.buyCard(s, i);
            if (ok) {
                assertEquals(before - price, s.money, "扣款应精确等于标价（item " + i + "）");
            } else {
                assertEquals(before, s.money, "购买失败不得扣款（item " + i + "）");
            }
        }
    }

    @Test
    void rerollCostProgressionAndExactCharge() {
        RunState s = Engine.createRun("red", 0, "REROLL1", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        s.money = 10_000;

        // 无券：5,6,7,8…
        for (int n = 0; n < 4; n++) {
            long before = s.money;
            long cost = Shop.reroll(s);
            assertEquals(5 + n, cost, "第 " + (n + 1) + " 次重掷费用应为 " + (5 + n));
            assertEquals(before - cost, s.money, "重掷扣款精确");
        }
        // 加双券（-2-2）：后续 9-4=5, 10-4=6…
        s.vouchers.add("reroll1");
        s.vouchers.add("reroll2");
        long before = s.money;
        assertEquals(5, Shop.reroll(s), "rerollCount=4 + 5 - 4 = 5");
        assertEquals(before - 5, s.money);
        // 廉价到 0 的钳制：重置商店模拟新局
        RunState s2 = Engine.createRun("red", 0, "REROLL2", null);
        Engine.selectBlind(s2, Data.BlindType.SMALL, false);
        s2.roundScore = s2.blindTarget;
        Engine.playHand(s2, List.of(s2.hand.get(0).id()));
        s2.money = 1_000;
        s2.vouchers.add("reroll1");
        s2.vouchers.add("reroll2");
        assertEquals(1, Shop.reroll(s2), "5-4=1（未到 0，正常收费）");
        // freeRerolls 优先归零
        RunState s3 = Engine.createRun("red", 0, "REROLL3", null);
        Engine.selectBlind(s3, Data.BlindType.SMALL, false);
        s3.roundScore = s3.blindTarget;
        Engine.playHand(s3, List.of(s3.hand.get(0).id()));
        s3.money = 1_000;
        s3.mods.freeReroll = true; // 商店开店时已折算 freeRerolls +99（openShop→genShop）
        RunState s4 = Engine.createRun("red", 0, "REROLL4", null);
        Engine.selectBlind(s4, Data.BlindType.SMALL, false);
        s4.roundScore = s4.blindTarget;
        Engine.playHand(s4, List.of(s4.hand.get(0).id()));
        s4.money = 1_000;
        s4.shop.freeRerolls = 2; // 直接预置 2 次免费
        assertEquals(0, Shop.reroll(s4), "免费次数内费用为 0");
        assertEquals(0, Shop.reroll(s4), "第二次仍免费");
        assertEquals(1_000, s4.money, "免费重掷不扣款");
        // REF rerollShop L1387：免费路径同样累计 rerollCount → 2 次免费后 count=2，恢复收费即 5+2=7
        assertEquals(7, Shop.reroll(s4), "免费耗尽后按累计次数收费（5+2）");
        assertEquals(993, s4.money);
        // 资金不足拒绝不扣款（下一次 5+3=8 > $1）
        s4.money = 1;
        assertEquals(-1, Shop.reroll(s4), "买不起应返回 -1");
        assertEquals(1, s4.money, "拒绝不得扣款");
    }

    @Test
    void rerollBossVoucherPaths() {
        // rerollBoss 为 void：拒绝/放行经钱与 Boss 队列观测
        RunState s = Engine.createRun("red", 0, "BOSSRR1", null);
        s.money = 100;
        String boss0 = s.bossQueue.get(0);
        Engine.rerollBoss(s, false); // 无 director/retcon → 应拒绝
        assertEquals(100, s.money, "无券拒绝不扣款");
        assertEquals(boss0, s.bossQueue.get(0), "无券拒绝不换 Boss");

        // director（无 retcon）：扣 $10 换 Boss
        s.vouchers.add("director");
        Engine.rerollBoss(s, false);
        assertEquals(90, s.money, "director 扣 $10");
        assertFalse(s.bossQueue.isEmpty());

        // 贫穷 + director：拒绝不扣款
        s.money = 5;
        Engine.rerollBoss(s, false);
        assertEquals(5, s.money, "钱不够拒绝不扣款");

        // retcon：免费（钱不变）
        s.vouchers.add("retcon");
        s.money = 5;
        Engine.rerollBoss(s, false);
        assertEquals(5, s.money, "retcon 免费");

        // 贫穷 + retcon：仍放行（免费路径不查钱）
        s.money = 0;
        Engine.rerollBoss(s, false);
        assertEquals(0, s.money, "免费路径零元也放行且不扣款");
        assertFalse(s.bossQueue.isEmpty());
    }
}
