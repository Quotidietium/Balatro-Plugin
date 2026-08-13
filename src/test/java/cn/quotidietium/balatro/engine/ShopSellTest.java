package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 商店阶段出售持有牌的引擎层测试（0.4.21）。
 *
 * <p>验证引擎层出售逻辑（{@link RunState#sellJoker} / {@link RunState#sellConsumable}）
 * 在商店阶段正确生效：金钱增加、物品移除、永恒小丑不可售、越界安全返回 false。
 * 全息渲染层与命令层 TOCTOU 防护另由 Bukkit 集成测试覆盖（需实机）。
 */
class ShopSellTest {

    /** 出售小丑：金钱增加 = sellValue，小丑从列表移除。 */
    @Test
    void sellJokerGivesMoneyAndRemoves() {
        RunState s = Engine.createRun("red", 0, "SHOPSELL1", null);
        assertTrue(s.gainJoker("joker", null), "应成功获得小丑");
        assertEquals(1, s.jokers.size());
        long moneyBefore = s.money;
        int expectedVal = s.sellValue(s.jokers.get(0));
        assertTrue(s.sellJoker(0), "出售应成功");
        assertEquals(0, s.jokers.size(), "小丑应已移除");
        assertEquals(moneyBefore + expectedVal, s.money, "金钱应增加售价");
    }

    /** 出售消耗品：金钱增加 = max(1, 1+sellBonus)，消耗品从列表移除。 */
    @Test
    void sellConsumableGivesMoneyAndRemoves() {
        RunState s = Engine.createRun("red", 0, "SHOPSELL2", null);
        assertTrue(s.addConsumableKey("tarot", "magician"), "应成功获得消耗品");
        assertEquals(1, s.consumables.size());
        long moneyBefore = s.money;
        int expectedVal = RunState.sellValue(s.consumables.get(0));
        assertTrue(s.sellConsumable(0), "出售应成功");
        assertEquals(0, s.consumables.size(), "消耗品应已移除");
        assertEquals(moneyBefore + expectedVal, s.money, "金钱应增加售价");
    }

    /** sellValue(Consumable) 统一口径：渲染/对话框/引擎共用同一公式。 */
    @Test
    void consumableSellValueHelperConsistent() {
        RunState s = Engine.createRun("red", 0, "SHOPSELLV1", null);
        assertTrue(s.addConsumableKey("tarot", "magician"));
        Consumable c = s.consumables.get(0);
        // 基础 sellBonus=0 → max(1, 1+0) = 1
        assertEquals(1, RunState.sellValue(c), "sellBonus=0 时售价应 $1");
        // 模拟礼品卡加成：sellBonus=3 → max(1, 1+3) = 4
        c.sellBonus = 3;
        assertEquals(4, RunState.sellValue(c), "sellBonus=3 时售价应 $4");
        // 负值不会发生（sellBonus 只增不减），但 max(1,...) 兜底
        c.sellBonus = -5;
        assertEquals(1, RunState.sellValue(c), "负 sellBonus 兜底为 $1");
    }

    /** 越界索引安全返回 false，不抛异常。 */
    @Test
    void outOfBoundsReturnsFalse() {
        RunState s = Engine.createRun("red", 0, "SHOPSELL3", null);
        assertFalse(s.sellJoker(-1), "负索引应返回 false");
        assertFalse(s.sellJoker(0), "空列表索引 0 应返回 false");
        assertFalse(s.sellConsumable(-1), "负索引应返回 false");
        assertFalse(s.sellConsumable(0), "空列表索引 0 应返回 false");
    }

    /** 出售后可腾出槽位再获得新牌（商店场景：卖旧买新）。 */
    @Test
    void sellThenRegainFreesSlot() {
        RunState s = Engine.createRun("red", 0, "SHOPSELL4", null);
        // 填满小丑槽（5）
        for (int i = 0; i < s.jokerSlots; i++) {
            assertTrue(s.gainJoker("joker", null), "应能填满小丑槽");
        }
        assertEquals(0, s.jokerSpace(), "槽应已满");
        // 出售第 1 张腾出 1 槽
        assertTrue(s.sellJoker(0), "出售应成功");
        assertEquals(1, s.jokerSpace(), "应腾出 1 槽");
        assertTrue(s.gainJoker("joker", null), "腾出后应能再获得小丑");
    }

    /** 出售多张消耗品（连续出售不崩）。 */
    @Test
    void sellMultipleConsumablesSequentially() {
        RunState s = Engine.createRun("red", 0, "SHOPSELL5", null);
        assertTrue(s.addConsumableKey("tarot", "magician"));
        assertTrue(s.addConsumableKey("planet", "mercury"));
        assertEquals(2, s.consumables.size());
        long moneyBefore = s.money;
        assertTrue(s.sellConsumable(0), "出售第 1 个");
        assertEquals(1, s.consumables.size());
        assertTrue(s.sellConsumable(0), "出售第 2 个（列表收缩后仍 idx 0）");
        assertEquals(0, s.consumables.size());
        assertTrue(s.money > moneyBefore, "金钱应增加");
    }
}
