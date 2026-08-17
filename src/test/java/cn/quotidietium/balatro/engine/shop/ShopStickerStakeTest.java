package cn.quotidietium.balatro.engine.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import org.junit.jupiter.api.Test;

/**
 * 赌注贴纸（黑注永恒/金注租赁）商店生成确定性锁（R234）。
 *
 * <p>实机采样（check31 三轮 18 只购丑 0 贴纸命中，联合概率 ~6%——运气偏差）由本
 * 固定种子锁补位：种子/赌注确定 ⇒ 贴纸确定。锁四点：黑注出永恒、金注出租赁且
 * 购价恒 $1（R124 真版）、标准局零贴纸、jokerless 挑战商店零小丑（与实机
 * check31 的禁入断言互补——那是实机面，这是引擎面）。
 */
class ShopStickerStakeTest {

    private static RunState shopAt(String deck, int stake, String seed, String challenge) {
        RunState s = Engine.createRun(deck, stake, seed, challenge);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.phase = Phase.SHOP;
        Shop.openShop(s);
        return s;
    }

    @Test
    void blackStakeShopsEternalJoker() {
        RunState s = shopAt("red", 3, "ESTICK0", null);
        assertTrue(s.mods.blackStake);
        long eternal = s.shop.cards.stream().filter(c -> "joker".equals(c.kind) && c.joker != null && c.joker.eternal).count();
        assertTrue(eternal >= 1, "黑注固定种子商店应含永恒小丑，实际 " + eternal);
    }

    @Test
    void goldStakeShopsRentalJokerAtOneDollar() {
        RunState s = shopAt("red", 7, "RSTICK0", null);
        assertTrue(s.mods.goldStake);
        var rental = s.shop.cards.stream()
                .filter(c -> "joker".equals(c.kind) && c.joker != null && c.joker.rental).findFirst();
        assertTrue(rental.isPresent(), "金注固定种子商店应含租赁小丑");
        assertEquals(1L, rental.get().price, "租赁小丑购价恒 $1（R124 真版）");
    }

    @Test
    void standardStakeHasNoStickers() {
        RunState s = shopAt("red", 0, "ESTICK0", null);
        assertFalse(s.mods.blackStake && s.mods.goldStake);
        s.shop.cards.forEach(c -> {
            if ("joker".equals(c.kind) && c.joker != null) {
                assertFalse(c.joker.eternal, "标准局不应出现永恒：" + c.key);
                assertFalse(c.joker.rental, "标准局不应出现租赁：" + c.key);
            }
        });
    }

    @Test
    void jokerlessShopsZeroJokers() {
        RunState s = shopAt("red", 0, "ESTICK0", "jokerless");
        long jokers = s.shop.cards.stream().filter(c -> "joker".equals(c.kind)).count();
        assertEquals(0L, jokers, "jokerless 商店零小丑（noJokers 权重移除 + 池空回退均不得产生小丑）");
    }
}
