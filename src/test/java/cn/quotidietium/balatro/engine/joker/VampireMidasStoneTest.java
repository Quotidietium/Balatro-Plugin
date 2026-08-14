package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.RunState;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Vampire / Midas Mask 小丑对石头牌的增强转换回归测试（R93 补）。
 *
 * <p>R92 发现消耗品增强塔罗对石头牌 setEnh 不恢复 rank/suit；R93 系统审查发现 Vampire/Midas
 * 小丑有同根因 bug。本测试固化修复：石头牌被 Vampire 移除增强 / 被 Midas 变黄金后，
 * rank/suit 必须恢复合法值，isStone 与 enh 不再矛盾。
 */
class VampireMidasStoneTest {

    @Test
    void vampireRemovingStoneEnhancementRestoresRankSuit() {
        // Vampire 移除石头牌的 STONE 增强后，应恢复合法 rank/suit（黑桃2默认），不再矛盾
        RunState s = Engine.createRun("red", 0, "VAMP1", null);
        s.jokers.add(JokerRegistry.create("vampire"));
        // 构造一张石头牌（tower 产物的形态：rank=0/suit=-1/enh=STONE）
        Card stone = s.makeCard(0, -1);
        stone.setEnh(Data.Enhancement.STONE);
        s.fullDeck.add(stone);
        s.hand.add(stone);
        // 直接调用 applyEnhancement(null) 模拟 Vampire 的 onPlayHand 对计分牌的处理
        stone.applyEnhancement(null);
        assertEquals(null, stone.enh(), "Vampire 移除后 enh 应为 null");
        assertFalse(stone.isStone(), "移除 STONE 后不应再判为石头");
        assertTrue(stone.rank() >= 2 && stone.rank() <= 14, "rank 应恢复合法值：" + stone.rank());
        assertTrue(stone.suit() >= 0 && stone.suit() <= 3, "suit 应恢复合法值：" + stone.suit());
    }

    @Test
    void midasTurningStoneToGoldRestoresRankSuit() {
        // Midas Mask（Pareidolia 下）把石头牌变 GOLD 后，应恢复合法 rank/suit
        Card stone = new Card(1, 0, -1);
        stone.setEnh(Data.Enhancement.STONE);
        assertTrue(stone.isStone(), "初始应为石头牌");
        stone.applyEnhancement(Data.Enhancement.GOLD);
        assertEquals(Data.Enhancement.GOLD, stone.enh(), "Midas 后 enh 应为 GOLD");
        assertFalse(stone.isStone(), "变 GOLD 后不应再判为石头");
        assertTrue(stone.rank() >= 2, "rank 应恢复合法值：" + stone.rank());
        assertTrue(stone.suit() >= 0, "suit 应恢复合法值：" + stone.suit());
    }

    @Test
    void vampireAccumulatesOnStoneCard() {
        // Vampire 对石头牌计分也应能处理（石头牌永远计分），转换后状态合法。
        // 累积逻辑由 JokerAccumulateTest 覆盖，此处只验证 applyEnhancement(null) 的状态恢复。
        RunState s = Engine.createRun("red", 0, "VAMP2", null);
        JokerInstance v = JokerRegistry.create("vampire");
        s.jokers.add(v);
        // 初始 extra 无 "x" 键（默认 0.0 在 onPlayHand 里 gd() 兜底）
        assertFalse(v.extra.containsKey("x"));
        // 模拟 Vampire onPlayHand 对一张石头计分牌的 setEnh(null)（现 applyEnhancement(null)）
        Card stone = s.makeCard(0, -1);
        stone.setEnh(Data.Enhancement.STONE);
        stone.applyEnhancement(null);
        assertFalse(stone.isStone(), "移除 STONE 后不应再判为石头");
        assertTrue(stone.rank() >= 2 && stone.suit() >= 0, "应恢复合法底层");
    }

    @Test
    void normalCardUnaffectedByRestoration() {
        // 普通牌 applyEnhancement 不应误改其 rank/suit
        Card normal = new Card(1, 7, 1); // 红桃7
        normal.applyEnhancement(Data.Enhancement.BONUS);
        assertEquals(7, normal.rank(), "普通牌 rank 不应变");
        assertEquals(1, normal.suit(), "普通牌 suit 不应变");
        assertEquals(Data.Enhancement.BONUS, normal.enh());

        // 普通 BONUS 牌转 GLASS
        normal.applyEnhancement(Data.Enhancement.GLASS);
        assertEquals(7, normal.rank(), "转 GLASS 后 rank 不应变");
        assertEquals(Data.Enhancement.GLASS, normal.enh());
    }

    @Test
    void stoneToStoneStaysStone() {
        // 石头牌转 STONE（tower 对已是石头的牌）应保持石头态
        Card stone = new Card(1, 0, -1);
        stone.setEnh(Data.Enhancement.STONE);
        stone.applyEnhancement(Data.Enhancement.STONE);
        assertTrue(stone.isStone(), "STONE→STONE 应仍是石头");
        assertEquals(0, stone.rank());
        assertEquals(-1, stone.suit());
    }
}
