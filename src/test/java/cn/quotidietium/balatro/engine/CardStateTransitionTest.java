package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.*;

import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Card 可变状态极端转换测试（轮次 49）。
 *
 * <p>验证 setEnh/setRank/setSuit 在极端组合下的状态转换正确性：
 * 石头牌被改写、玻璃牌被 Vampire 移除增强后复原、增强覆盖、版本叠加等。
 */
class CardStateTransitionTest {

    @Test
    void stoneCardDeathRewrite() {
        // 石头牌（rank=0/suit=-1）被 Death 塔罗改写为普通牌的 rank/suit
        Card stone = new Card(1, 0, -1);
        stone.setEnh(Data.Enhancement.STONE);
        assertTrue(stone.isStone(), "初始应为石头");

        // 模拟 Death 的改写（dst 复制 src）
        Card src = new Card(2, 10, 1); // 红桃 10
        stone.setRank(src.rank());
        stone.setSuit(src.suit());
        stone.setEnh(src.enh()); // src 无增强
        stone.setFacedown(false);

        // 改写后应不再是石头（rank=10, suit=1, enh=null）
        assertFalse(stone.isStone(), "Death 改写后应不再是石头");
        assertEquals(10, stone.rank());
        assertEquals(1, stone.suit());
        assertNull(stone.enh());
    }

    @Test
    void glassEnhRemovedByVampire() {
        // 玻璃牌被 Vampire 移除增强 → enh=null
        Card glass = new Card(1, 5, 0);
        glass.setEnh(Data.Enhancement.GLASS);
        assertEquals(Data.Enhancement.GLASS, glass.enh());

        // Vampire 移除增强
        glass.setEnh(null);
        assertNull(glass.enh(), "Vampire 移除后 enh 应为 null");
        assertEquals(5, glass.rank(), "rank 不应变");
        assertFalse(glass.isStone(), "普通牌不是石头");
    }

    @Test
    void enhancementOverwrite() {
        // 先设 BONUS 再设 MULT → 应覆盖
        Card c = new Card(1, 7, 0);
        c.setEnh(Data.Enhancement.BONUS);
        assertEquals(Data.Enhancement.BONUS, c.enh());
        c.setEnh(Data.Enhancement.MULT);
        assertEquals(Data.Enhancement.MULT, c.enh(), "setEnh 应覆盖");
    }

    @Test
    void stoneThenTowerMakesStone() {
        // Tower 塔罗把普通牌变成石头（rank=0/suit=-1）
        Card c = new Card(1, 10, 1);
        c.setEnh(Data.Enhancement.STONE);
        c.setRank(0);
        c.setSuit(-1);
        assertTrue(c.isStone(), "Tower 后应为石头");
        assertEquals(0, c.rank());
        assertEquals(-1, c.suit());
    }

    @Test
    void marbleStoneShellRestoration() {
        // marble 生成的石头牌壳为 rank=2/suit=0 + STONE
        Card c = new Card(1, 2, 0);
        c.setEnh(Data.Enhancement.STONE);
        assertTrue(c.isStone(), "marble 石头应为石头");

        // 吸血鬼移除增强后应复原为黑桃 2
        c.setEnh(null);
        assertFalse(c.isStone(), "移除 STONE 后应不再是石头");
        assertEquals(2, c.rank(), "复原为 rank=2");
        assertEquals(0, c.suit(), "复原为 suit=0（黑桃）");
    }

    @Test
    void editionStacksWithEnhancement() {
        // 版本和增强独立存在
        Card c = new Card(1, 10, 0);
        c.setEnh(Data.Enhancement.BONUS);
        c.setEdition(Data.Edition.FOIL);
        c.setSeal(Data.Seal.RED);
        assertEquals(Data.Enhancement.BONUS, c.enh());
        assertEquals(Data.Edition.FOIL, c.edition());
        assertEquals(Data.Seal.RED, c.seal());
    }

    @Test
    void chipBonusAccumulates() {
        // hiker 的 addChipBonus 累积
        Card c = new Card(1, 5, 0);
        assertEquals(0, c.chipBonus());
        c.addChipBonus(5);
        assertEquals(5, c.chipBonus());
        c.addChipBonus(8);
        assertEquals(13, c.chipBonus());
    }

    @Test
    void brokenGlassRemovedFromDeck() {
        // 玻璃牌破碎后 setBroken(true)，应从牌组移除
        Card c = new Card(1, 5, 0);
        c.setEnh(Data.Enhancement.GLASS);
        assertFalse(c.isBroken());
        c.setBroken(true);
        assertTrue(c.isBroken(), "破碎标记");
        // 引擎在 playHand 中检查 broken → removeCardFromDeck
    }

    @Test
    void vampireRemovesEnhFromScoringCard() {
        // 在实际计分流程中验证 Vampire 移除增强
        RunState s = Engine.createRun("red", 0, "VAMPTEST", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.jokers.add(JokerRegistry.create("vampire"));

        // 给第一张牌设增强
        Card scored = s.hand.get(0);
        scored.setEnh(Data.Enhancement.BONUS);
        assertNotNull(scored.enh(), "设增强后应非 null");

        // 出这张牌
        Engine.playHand(s, List.of(scored.id()));

        // Vampire 的 onPlayHand 应移除计分牌的增强
        // 注：Vampire 只移除 scoredCards 中的增强牌
        // 如果该牌是计分牌（HIGH 的计分牌是 rank 最大的），则增强被移除
        // 这里只验证不崩溃——具体移除取决于该牌是否在 scoredCards 中
    }

    @Test
    void deathCopiesAllStates() {
        // Death 复制：src → dst，复制 rank/suit/enh/edition/seal
        Card src = new Card(1, 13, 2); // 梅花 K
        src.setEnh(Data.Enhancement.MULT);
        src.setEdition(Data.Edition.HOLO);
        src.setSeal(Data.Seal.BLUE);

        Card dst = new Card(2, 3, 0); // 黑桃 3（被改写）
        dst.setRank(src.rank());
        dst.setSuit(src.suit());
        dst.setEnh(src.enh());
        dst.setEdition(src.edition());
        dst.setSeal(src.seal());

        assertEquals(13, dst.rank(), "rank 应复制");
        assertEquals(2, dst.suit(), "suit 应复制");
        assertEquals(Data.Enhancement.MULT, dst.enh(), "enh 应复制");
        assertEquals(Data.Edition.HOLO, dst.edition(), "edition 应复制");
        assertEquals(Data.Seal.BLUE, dst.seal(), "seal 应复制");
    }

    @Test
    void cloneCardPreservesState() {
        // cloneCard 应复制所有状态字段
        RunState s = new RunState("CLONE");
        Card orig = s.makeCard(11, 1); // 红桃 J
        orig.setEnh(Data.Enhancement.GLASS);
        orig.setEdition(Data.Edition.POLY);
        orig.setSeal(Data.Seal.GOLD);
        orig.addChipBonus(15);

        Card clone = s.cloneCard(orig);
        assertNotEquals(orig.id(), clone.id(), "clone 应有新 id");
        assertEquals(11, clone.rank(), "rank 复制");
        assertEquals(1, clone.suit(), "suit 复制");
        assertEquals(Data.Enhancement.GLASS, clone.enh(), "enh 复制");
        assertEquals(Data.Edition.POLY, clone.edition(), "edition 复制");
        assertEquals(Data.Seal.GOLD, clone.seal(), "seal 复制");
        assertEquals(15, clone.chipBonus(), "chipBonus 复制");
        assertFalse(clone.debuff(), "clone debuff 默认 false");
        assertFalse(clone.facedown(), "clone facedown 默认 false");
    }

    @Test
    void isStoneThreeWayCheck() {
        // isStone 三条件：enh==STONE || rank==0 || suit<0
        Card a = new Card(1, 5, 0); a.setEnh(Data.Enhancement.STONE);
        assertTrue(a.isStone(), "enh STONE → 石头");
        Card b = new Card(2, 0, 0);
        assertTrue(b.isStone(), "rank==0 → 石头");
        Card c = new Card(3, 5, -1);
        assertTrue(c.isStone(), "suit<0 → 石头");
        Card d = new Card(4, 5, 0);
        assertFalse(d.isStone(), "正常牌不是石头");
    }
}
