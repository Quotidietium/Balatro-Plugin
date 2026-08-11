package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.consumable.Consumables;
import cn.quotidietium.balatro.engine.shop.Shop;
import org.junit.jupiter.api.Test;

/**
 * 小丑销毁 / 消耗品改写小丑列表后的 flags 与金钱边界回归测试（轮次 R5）。
 *
 * <p>对照 REF engine.js destroyJoker（置 grosDead + computeFlags）；hex 直接改写
 * 小丑列表为 REF 遗漏点，按插件既有「修正 REF 遗漏」惯例补齐 recomputeFlags。
 */
class JokerDestroyFlagsTest {

    /** destroyJoker 后重算 flags：被毁小丑的标志（信用卡 credit）立即失效。 */
    @Test
    void destroyJokerRecomputesFlags() {
        RunState s = Engine.createRun("red", 0, "DESTROYFLG1", null);
        assertTrue(s.gainJoker("creditcard", null));
        assertEquals(20, ((Number) s.flags.get("credit")).intValue(), "获得信用卡后应有 credit=20");

        s.destroyJoker(s.jokers.get(0), "测试销毁");
        assertTrue(s.jokers.isEmpty());
        assertFalse(s.flags.containsKey("credit"), "销毁信用卡后 credit 标志应立即消失");
    }

    /** 任何途径销毁格罗米歇尔都置 grosDead（对齐 REF：仪式匕首/癫狂吞掉也解锁卡文迪什）。 */
    @Test
    void destroyGrossmichelSetsGrosDead() {
        RunState s = Engine.createRun("red", 0, "GROSDEAD01", null);
        assertTrue(s.gainJoker("grossmichel", null));
        assertFalse(s.grosDead);

        s.destroyJoker(s.jokers.get(0), "仪式匕首吞掉了 格罗米歇尔");
        assertTrue(s.grosDead, "非自毁途径销毁格罗米歇尔也应置 grosDead");
    }

    /** hex 直接移除小丑后 flags 不残留：两张信用卡 credit=40 → 剩一张应为 20。 */
    @Test
    void hexConsumableClearsDestroyedJokerFlags() {
        RunState s = Engine.createRun("red", 0, "HEXFLAGS01", null);
        assertTrue(s.gainJoker("creditcard", null));
        assertTrue(s.gainJoker("creditcard", null)); // 引擎层允许重复（仅商店生成去重）
        assertEquals(40, ((Number) s.flags.get("credit")).intValue());
        s.phase = Phase.SHOP;
        assertTrue(s.addConsumableKey("spectral", "hex"));

        Consumables.Result r = Consumables.use(s, 0, null);
        assertTrue(r.ok, "hex 使用应成功: " + r.err);
        assertEquals(1, s.jokers.size(), "hex 应只剩 1 张小丑");
        assertEquals(Data.Edition.NEGATIVE, s.jokers.get(0).edition, "留下的小丑应变为负片");
        assertEquals(20, ((Number) s.flags.get("credit")).intValue(),
                "hex 销毁一张信用卡后 credit 应立即从 40 降为 20（无残留）");
    }

    /** canAfford 饱和：金钱达 long 上限时 +credit 不环绕为负数（误判买不起）。 */
    @Test
    void canAffordSaturatesAtLongMax() {
        RunState s = Engine.createRun("red", 0, "AFFORDSAT1", null);
        assertTrue(s.gainJoker("creditcard", null)); // credit=20
        s.money = Long.MAX_VALUE;
        assertTrue(Shop.canAfford(s, 10), "金钱饱和时仍能买得起（+credit 不环绕）");
        assertTrue(Shop.canAfford(s, Long.MAX_VALUE));
        // 负钱 + 信用额度：-15 + 20 >= 5 可负担（信用卡语义），-15 + 20 < 6 不可
        s.money = -15;
        assertTrue(Shop.canAfford(s, 5));
        assertFalse(Shop.canAfford(s, 6));
    }
}
