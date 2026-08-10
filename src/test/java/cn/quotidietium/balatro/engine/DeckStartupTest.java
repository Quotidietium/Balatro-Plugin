package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 牌组开局效果测试：magic/ghost/zodiac/nebula 的优惠券与消耗品开局（对齐 engine.js createRun）。
 * 这些牌组在 0.2.7 前是 TODO，且黄金套件不覆盖，故单独锁定。
 */
class DeckStartupTest {

    @Test
    void magicDeckStartsWithCrystalAndTwoFools() {
        RunState s = Engine.createRun("magic", 0, "MAGIC");
        assertTrue(s.vouchers.contains("crystal"), "魔法牌组应有水晶球优惠券");
        assertEquals(2, s.consumables.size(), "应有 2 张愚人");
        assertEquals("fool", s.consumables.get(0).key);
        assertEquals("fool", s.consumables.get(1).key);
        assertEquals(3, s.consumableSlots, "水晶球使消耗品槽 +1（2+1）");
    }

    @Test
    void nebulaDeckStartsWithTelescopeAndOneSlot() {
        RunState s = Engine.createRun("nebula", 0, "NEB");
        assertTrue(s.vouchers.contains("telescope"), "星云牌组应有望远镜优惠券");
        assertEquals(1, s.consumableSlots, "星云牌组消耗品槽 -1");
    }

    @Test
    void ghostDeckStartsWithHexAndSpectralShop() {
        RunState s = Engine.createRun("ghost", 0, "GH");
        assertTrue(s.mods.spectralInShop, "幽灵牌组应允许幻灵进商店");
        assertEquals(1, s.consumables.size());
        assertEquals("spectral", s.consumables.get(0).kind);
        assertEquals("hex", s.consumables.get(0).key);
    }

    @Test
    void zodiacDeckStartsWithThreeVouchers() {
        RunState s = Engine.createRun("zodiac", 0, "ZOD");
        assertTrue(s.vouchers.contains("tarotm"));
        assertTrue(s.vouchers.contains("planetm"));
        assertTrue(s.vouchers.contains("overstock"));
        assertEquals(3, s.shopSlots, "多重库存使商店卡牌位 +1（2+1）");
    }
}
