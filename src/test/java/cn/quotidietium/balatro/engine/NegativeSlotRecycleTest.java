package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * negative 版本小丑/消耗品的「加槽 + 出售回收」闭环（R89 补的回归测试）。
 *
 * <p>R23 修复了 negative 加槽（jokerSpace = jokerSlots + neg - size；addConsumableKey
 * 上限 = consumableSlots + neg）。但「出售 negative 物品后槽位是否正确回收」此前无专门
 * 回归测试——若回收算错，会导致槽位泄漏（能无限塞牌）或槽位塌缩（卖一张反而满槽锁死）。
 *
 * <p>本测试覆盖：negative 小丑净占 1 槽（贡献 +1 抵消自身）、出售 negative 后槽位正确收缩、
 * negative 消耗品同理、满槽时 gainJoker/addConsumableKey 正确拒绝。
 */
class NegativeSlotRecycleTest {

    @Test
    void negativeJokerNetOccupiesOneSlot() {
        // jokerSlots 默认 5：填满 5 张普通小丑后 jokerSpace=0
        RunState s = Engine.createRun("red", 0, "NEGJ1", null);
        for (int i = 0; i < 5; i++) {
            assertTrue(s.gainJoker("joker", null), "第 " + i + " 张普通小丑应能加入");
        }
        assertEquals(0, s.jokerSpace(), "5 张普通小丑后应满槽");
        assertFalse(s.gainJoker("joker", null), "满槽后普通小丑应被拒绝");

        // negative 小丑：贡献 +1 槽但自身占 1 槽 → 净占 1，仍可加入（jokerSpace 回到 1 再 -1 = 0）
        assertTrue(s.gainJoker("joker", Data.Edition.NEGATIVE), "negative 小丑应能加入（自带 +1 槽）");
        // 现在 jokers=6, neg=1 → jokerSpace = 5 + 1 - 6 = 0
        assertEquals(0, s.jokerSpace(), "negative 小丑加入后净占 1 槽，应再次满槽");
        assertFalse(s.gainJoker("joker", null), "再次满槽后普通小丑应被拒绝");
    }

    @Test
    void sellingNegativeJokerRecyclesSlotCorrectly() {
        RunState s = Engine.createRun("red", 0, "NEGJ2", null);
        // 4 普通 + 1 negative = 5 张，neg=1 → jokerSpace = 5+1-5 = 1
        for (int i = 0; i < 4; i++) assertTrue(s.gainJoker("joker", null));
        assertTrue(s.gainJoker("joker", Data.Edition.NEGATIVE));
        assertEquals(1, s.jokerSpace(), "negative 贡献的 +1 槽可用");

        // 出售 negative 小丑（idx=4，最后一张）：neg 0→0? 不，neg 从 1→0，size 5→4
        // jokerSpace = 5 + 0 - 4 = 1
        assertTrue(s.sellJoker(4), "出售 negative 小丑应成功");
        assertEquals(0, countNegativeJokers(s), "negative 小丑已售出");
        assertEquals(1, s.jokerSpace(), "出售 negative 后槽位 = 5 + 0 - 4 = 1（不应泄漏额外槽）");
    }

    @Test
    void sellingNormalJokerWithNegativePresentRecyclesCorrectly() {
        RunState s = Engine.createRun("red", 0, "NEGJ3", null);
        // 3 普通 + 1 negative = 4 张，neg=1 → jokerSpace = 5+1-4 = 2
        for (int i = 0; i < 3; i++) assertTrue(s.gainJoker("joker", null));
        assertTrue(s.gainJoker("joker", Data.Edition.NEGATIVE));
        assertEquals(2, s.jokerSpace());

        // 出售一张普通小丑（idx=0）：size 4→3, neg 仍 1 → jokerSpace = 5+1-3 = 3
        assertTrue(s.sellJoker(0), "出售普通小丑应成功");
        assertEquals(1, countNegativeJokers(s), "negative 小丑仍在");
        assertEquals(3, s.jokerSpace(), "出售普通后槽位 = 5 + 1 - 3 = 3");
    }

    @Test
    void negativeConsumableNetOccupiesOneSlot() {
        // consumableSlots 默认 2
        RunState s = Engine.createRun("red", 0, "NEGC1", null);
        assertTrue(s.addConsumableKey("tarot", "fool"));
        assertTrue(s.addConsumableKey("tarot", "fool"));
        assertFalse(s.addConsumableKey("tarot", "fool"), "2 张普通消耗品后应满槽");

        // 手动加 negative 消耗品（模拟 hex/ankh/ectoplasm 产生的 negative）
        Consumable neg = new Consumable("tarot", "fool");
        neg.edition = Data.Edition.NEGATIVE;
        s.consumables.add(neg);
        // 现在 consumables=3, neg=1 → 上限 2+1=3，已满
        assertFalse(s.addConsumableKey("tarot", "fool"), "negative 消耗品净占 1 槽后应再次满槽");

        // 出售 negative 消耗品（idx=2）：size 3→2, neg 1→0 → 上限 2+0=2，已满
        assertTrue(s.sellConsumable(2), "出售 negative 消耗品应成功");
        assertEquals(0, countNegativeConsumables(s));
        assertFalse(s.addConsumableKey("tarot", "fool"), "出售 negative 后上限收缩回 2，应满槽拒绝");
    }

    @Test
    void repeatedNegativeJokerGainDoesNotLeakSlots() {
        // 循环：加 negative 小丑 → 出售 → 再加，验证槽位不逐步泄漏
        RunState s = Engine.createRun("red", 0, "NEGLK", null);
        for (int cycle = 0; cycle < 20; cycle++) {
            int before = s.jokerSpace();
            assertTrue(s.gainJoker("joker", Data.Edition.NEGATIVE),
                    "cycle " + cycle + ": negative 小丑应总能加入（自带槽）");
            // 加入后 jokerSpace 不变（negative 净占 1 槽：+1 贡献 -1 自身）
            assertEquals(before, s.jokerSpace(), "cycle " + cycle + ": negative 小丑净占 1 槽，剩余槽位应不变");
            // 出售刚加的（最后一张）
            assertTrue(s.sellJoker(s.jokers.size() - 1), "cycle " + cycle + ": 出售应成功");
            assertEquals(before, s.jokerSpace(), "cycle " + cycle + ": 出售后槽位应恢复");
        }
        // 20 轮循环后不应有残留 negative，且 jokerSpace 应回到初始 5
        assertEquals(0, countNegativeJokers(s));
        assertEquals(5, s.jokerSpace(), "20 轮加-售循环后槽位不应泄漏");
    }

    private static int countNegativeJokers(RunState s) {
        int n = 0;
        for (JokerInstance j : s.jokers) if (j.edition == Data.Edition.NEGATIVE) n++;
        return n;
    }

    private static int countNegativeConsumables(RunState s) {
        int n = 0;
        for (Consumable c : s.consumables) if (c.edition == Data.Edition.NEGATIVE) n++;
        return n;
    }
}
