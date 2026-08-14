package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Consumable;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.RunState;
import org.junit.jupiter.api.Test;

/**
 * 小丑/消耗品复制产线的副本语义（R113）：隐形小丑复制产出 + perkeo 负片副本的槽位闭环。
 *
 * <p>承接 R111/R112（多副本独立计数、复制器读共享、ankh 空起步）：
 * <ul>
 *   <li>隐形小丑：经 gainJoker 产出**空起步**副本（不继承累积值）+ 继承源版本；
 *       未满 3 回合出售不出副本；到期出售从**其他**小丑中挑源。</li>
 *   <li>perkeo：满普通槽时仍加出负片副本（自带 +1 槽）；随后 addConsumableKey 到顶拒绝；
 *       售出负片后上限收缩回满——R23/R90 槽位数学经真实产线路径闭环；
 *       副本 sellBonus 重置、源版本被覆盖为负片。</li>
 * </ul>
 */
class JokerDuplicationSemanticsTest {

    private static RunState inRound(String seed) {
        RunState s = Engine.createRun("red", 0, seed, null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        return s;
    }

    @Test
    void invisibleDuplicateIsFreshAndInheritsEdition() {
        RunState s = inRound("INV1");
        JokerInstance runner = JokerRegistry.create("runner");
        runner.extra.put("chips", 30);              // 源已累积
        runner.edition = Data.Edition.FOIL;         // 源带版本
        s.jokers.add(runner);
        JokerInstance inv = JokerRegistry.create("invisible");
        s.jokers.add(inv);

        // 未满 3 回合出售：不出副本
        assertTrue(s.sellJoker(1), "出售隐形小丑应成功");
        assertEquals(1, s.jokers.size(), "未到期不出副本（只剩 runner）");
        assertEquals(30, ((Number) runner.extra.get("chips")).intValue(), "源不受影响");

        // 重新装隐形并快进 3 回合（直接调 onRoundEnd 模拟回合结束钩子）
        JokerInstance inv2 = JokerRegistry.create("invisible");
        s.jokers.add(inv2);
        for (int r = 0; r < 3; r++) inv2.def.onRoundEnd(s, inv2);
        assertEquals(0, ((Number) inv2.extra.get("rounds")).intValue(), "3 回合后到期");
        int before = s.jokers.size();
        assertTrue(s.sellJoker(s.jokers.indexOf(inv2)), "到期出售");
        assertEquals(before, s.jokers.size(), "卖一得一：数量不变");
        JokerInstance dup = s.jokers.get(s.jokers.size() - 1);
        assertEquals("runner", dup.def.key(), "复制的是其他小丑");
        assertTrue(dup != runner, "新实例");
        assertFalse(dup.extra.containsKey("chips"), "副本空起步（不继承 30）");
        assertEquals(Data.Edition.FOIL, dup.edition, "继承源版本");
    }

    @Test
    void perkeoNegativeCopyClosesSlotMathLoop() {
        RunState s = inRound("PERK1");
        s.jokers.add(JokerRegistry.create("perkeo"));
        // 满普通槽：2 张正常消耗品（第二张带 sellBonus/版本以验证副本重置与覆盖）
        assertTrue(s.addConsumableKey("tarot", "fool"));
        assertTrue(s.addConsumableKey("planet", "pluto"));
        s.consumables.get(1).sellBonus = 4;
        s.consumables.get(1).edition = Data.Edition.FOIL;
        assertFalse(s.addConsumableKey("tarot", "magician"), "普通槽应已满");

        // perkeo 回合结束：满槽仍加出负片副本（自带 +1 槽）
        s.jokers.get(0).def.onRoundEnd(s, s.jokers.get(0));
        assertEquals(3, s.consumables.size(), "满槽仍复制（负片自带槽）");
        Consumable copy = s.consumables.get(2);
        assertEquals(Data.Edition.NEGATIVE, copy.edition, "副本强制负片（覆盖源 foil）");
        assertEquals(0, copy.sellBonus, "sellBonus 重置（不继承 4）");

        // 槽位数学闭环：3 张/neg1 → 上限 3（满）；售出负片 → 上限回 2（仍满）
        assertFalse(s.addConsumableKey("tarot", "magician"), "neg+1 槽后仍满（3/3）");
        assertTrue(s.sellConsumable(2), "售出负片副本");
        assertFalse(s.addConsumableKey("tarot", "magician"), "上限收缩回 2（2/2 仍满）");
        assertEquals(2, s.consumables.size());
    }

    @Test
    void perkeoRepeatedRoundsStackNegativesEachWithSlot() {
        RunState s = inRound("PERK2");
        JokerInstance pk = JokerRegistry.create("perkeo");
        s.jokers.add(pk);
        assertTrue(s.addConsumableKey("tarot", "fool"));
        for (int r = 0; r < 3; r++) pk.def.onRoundEnd(s, pk);
        // 1 普通 + 3 负片 = 4 张；上限 = 2 + 3neg = 5
        assertEquals(4, s.consumables.size(), "每回合各复制一张负片");
        int neg = 0;
        for (Consumable c : s.consumables) if (c.edition == Data.Edition.NEGATIVE) neg++;
        assertEquals(3, neg, "三张均为负片");
        assertTrue(s.addConsumableKey("tarot", "magician"), "neg 槽位足够再入一张（5 上限）");
        assertFalse(s.addConsumableKey("tarot", "sun"), "入满后拒绝");
        assertEquals(5, s.consumables.size());
    }
}
