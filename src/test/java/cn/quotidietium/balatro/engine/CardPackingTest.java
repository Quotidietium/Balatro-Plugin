package cn.quotidietium.balatro.engine;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P13 守门：Card 位打包表示的字段独立性与域边界 roundtrip。
 *
 * <p>可变状态压入单个 int（rank 6b | suit+1 3b | enh 4b | edition 3b | seal 3b | 3 flags）。
 * 本测试锁定：① 各域任意组合写入后读回不变；② 任一域的写不影响其他域（位独立性）；
 * ③ 游戏域边界（rank 0..14 / suit -1..3 / null 枚举）行为与原 10 字段布局一致。
 * 行为面由 CardTest/CardStateTransitionTest/54 黄金测试另行锁定。
 */
class CardPackingTest {

    @Test
    void rankSuitRoundtripFullDomain() {
        for (int rank = 0; rank <= 14; rank++) {
            for (int suit = -1; suit <= 3; suit++) {
                Card c = new Card(1, rank, suit);
                assertEquals(rank, c.rank(), "rank=" + rank);
                assertEquals(suit, c.suit(), "suit=" + suit);
            }
        }
    }

    @Test
    void enumRoundtripAllValues() {
        Card c = new Card(1, 7, 2);
        assertNull(c.enh());
        assertNull(c.edition());
        assertNull(c.seal());
        for (Data.Enhancement e : Data.Enhancement.values()) {
            c.setEnh(e);
            assertEquals(e, c.enh());
        }
        c.setEnh(null);
        assertNull(c.enh());
        for (Data.Edition e : Data.Edition.values()) {
            c.setEdition(e);
            assertEquals(e, c.edition());
        }
        c.setEdition(null);
        assertNull(c.edition());
        for (Data.Seal s : Data.Seal.values()) {
            c.setSeal(s);
            assertEquals(s, c.seal());
        }
        c.setSeal(null);
        assertNull(c.seal());
    }

    /** 各域任意组合互不串扰（位独立性核心保证）。 */
    @Test
    void fieldIndependenceAcrossAllCombinations() {
        List<Card> probes = new ArrayList<>();
        for (Data.Enhancement e : Data.Enhancement.values()) {
            Card c = new Card(9, 12, 1);
            c.setEnh(e);
            c.setEdition(Data.Edition.POLY);
            c.setSeal(Data.Seal.RED);
            c.setDebuff(true);
            c.setFacedown(true);
            c.setBroken(true);
            c.addChipBonus(77L);
            probes.add(c);
        }
        for (Card c : probes) {
            assertEquals(12, c.rank());
            assertEquals(1, c.suit());
            assertEquals(Data.Edition.POLY, c.edition());
            assertEquals(Data.Seal.RED, c.seal());
            assertTrue(c.debuff());
            assertTrue(c.facedown());
            assertTrue(c.isBroken());
            assertEquals(77L, c.chipBonus());
        }
        // 单域翻转不影响他域
        Card c = probes.get(0);
        c.setRank(3);
        c.setSuit(0);
        assertEquals(Data.Edition.POLY, c.edition());
        assertEquals(Data.Seal.RED, c.seal());
        assertTrue(c.debuff());
        c.setDebuff(false);
        assertEquals(3, c.rank());
        assertEquals(Data.Seal.RED, c.seal());
        assertFalse(c.debuff());
    }

    /** 石头壳与 applyEnhancement 转换路径（与原布局行为逐字一致，CardStateTransitionTest 全覆盖）。 */
    @Test
    void stoneShellAndConversion() {
        Card stone = new Card(1, 0, -1);
        assertTrue(stone.isStone());
        stone.setEnh(Data.Enhancement.STONE);
        stone.applyEnhancement(Data.Enhancement.BONUS); // 石头 → 奖励牌：恢复黑桃2底层
        assertEquals(Data.Enhancement.BONUS, stone.enh());
        assertEquals(2, stone.rank());
        assertEquals(0, stone.suit());
        assertFalse(stone.isStone());

        Card normal = new Card(2, 13, 3);
        normal.applyEnhancement(Data.Enhancement.STONE); // 普通 → 石头：0/-1 壳
        assertEquals(0, normal.rank());
        assertEquals(-1, normal.suit());
        assertTrue(normal.isStone());
    }

    /** 域约定：游戏域（0..14 / -1..3）内构造-改写-读回稳定。 */
    @Test
    void gameDomainMutationStability() {
        Card c = new Card(5, 14, 0); // A 黑桃
        c.setRank(11);
        c.setSuit(3);
        c.setSeal(Data.Seal.GOLD);
        c.setEdition(Data.Edition.FOIL);
        c.setEnh(Data.Enhancement.WILD);
        c.setFacedown(true);
        assertEquals(11, c.rank());
        assertEquals(3, c.suit());
        assertEquals(Data.Seal.GOLD, c.seal());
        assertEquals(Data.Edition.FOIL, c.edition());
        assertEquals(Data.Enhancement.WILD, c.enh());
        assertTrue(c.facedown());
        assertTrue(c.isFace());
        assertEquals("♦J", c.toString());
    }
}
