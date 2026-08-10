package cn.quotidietium.balatro.engine.joker;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.RunState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * perkeo（佩尔凯奥）小丑测试：回合结束时复制一张随机消耗品为负片。
 * 0.3.0 前为 TODO 桩，对齐 jokers.js perkeo，单独锁定。
 */
class PerkeoTest {

    @Test
    void copiesConsumableAsNegative() {
        RunState s = Engine.createRun("red", 0, "PERK");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        assertTrue(s.addConsumableKey("tarot", "fool"));
        JokerInstance perkeo = JokerRegistry.create("perkeo");
        s.jokers.add(perkeo);
        int before = s.consumables.size();

        long gain = perkeo.def.onRoundEnd(s, perkeo);
        assertEquals(0L, gain, "perkeo 不直接产钱");
        assertEquals(before + 1, s.consumables.size(), "应复制出一张消耗品");
        var copy = s.consumables.get(s.consumables.size() - 1);
        assertEquals("fool", copy.key);
        assertEquals(Data.Edition.NEGATIVE, copy.edition, "复制应为负片");
    }

    @Test
    void noOpWhenNoConsumables() {
        RunState s = Engine.createRun("red", 0, "PERK0");
        JokerInstance perkeo = JokerRegistry.create("perkeo");
        s.jokers.add(perkeo);
        perkeo.def.onRoundEnd(s, perkeo);
        assertEquals(0, s.consumables.size(), "无消耗品时不复制");
    }
}
