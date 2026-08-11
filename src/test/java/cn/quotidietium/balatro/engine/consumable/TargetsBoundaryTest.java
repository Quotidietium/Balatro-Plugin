package cn.quotidietium.balatro.engine.consumable;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Consumables.use targets 函数边界测试（轮次 47）。
 *
 * <p>验证 targets 在 0 张/超 max 张/重复 id/不存在 id/非回合阶段时的行为。
 */
class TargetsBoundaryTest {

    private RunState setupRound() {
        RunState s = Engine.createRun("red", 0, "TGTTEST", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        return s;
    }

    @Test
    void zeroTargetsReturnsNullForRequiredMax() {
        // targets(max=1, exact=true)：不传目标 → null（精确匹配要求 1 张）
        RunState s = setupRound();
        // 通过 use 直接测试——使用需 1 张目标的塔罗 lovers
        s.consumables.add(new cn.quotidietium.balatro.engine.Consumable("tarot", "lovers"));
        Consumables.Result r = Consumables.use(s, 0, List.of());
        // lovers exact=1，传 0 张 → null → err
        assertNotNull(r.err);
    }

    @Test
    void overMaxTargetsReturnsNull() {
        // targets(max=2, exact=false)：传 3 张 → null（超过 max）
        RunState s = setupRound();
        s.consumables.add(new cn.quotidietium.balatro.engine.Consumable("tarot", "magician"));
        // magician max=2，传 3 张手牌
        List<Integer> three = List.of(
            s.hand.get(0).id(), s.hand.get(1).id(), s.hand.get(2).id());
        Consumables.Result r = Consumables.use(s, 0, three);
        assertNotNull(r.err, "超过 max 应返回错误");
    }

    @Test
    void duplicateIdsReturnNull() {
        // 重复 id → null（targets 检查去重）
        RunState s = setupRound();
        s.consumables.add(new cn.quotidietium.balatro.engine.Consumable("tarot", "magician"));
        int id = s.hand.get(0).id();
        List<Integer> dup = List.of(id, id);
        Consumables.Result r = Consumables.use(s, 0, dup);
        assertNotNull(r.err, "重复 id 应返回错误");
    }

    @Test
    void nonexistentIdReturnNull() {
        // 不存在的 id → null（findInHand 返回 null）
        RunState s = setupRound();
        s.consumables.add(new cn.quotidietium.balatro.engine.Consumable("tarot", "magician"));
        List<Integer> bad = List.of(99999); // 不存在的 id
        Consumables.Result r = Consumables.use(s, 0, bad);
        assertNotNull(r.err, "不存在 id 应返回错误");
    }

    @Test
    void exactMatchEnforced() {
        // death exact=2，传 1 张 → null
        RunState s = setupRound();
        s.consumables.add(new cn.quotidietium.balatro.engine.Consumable("tarot", "death"));
        List<Integer> one = List.of(s.hand.get(0).id());
        Consumables.Result r = Consumables.use(s, 0, one);
        assertNotNull(r.err, "death 需恰好 2 张，传 1 张应报错");
    }

    @Test
    void validTargetsSucceed() {
        // 有效的 2 张目标 → 成功
        RunState s = setupRound();
        s.consumables.add(new cn.quotidietium.balatro.engine.Consumable("tarot", "magician"));
        List<Integer> two = List.of(s.hand.get(0).id(), s.hand.get(1).id());
        Consumables.Result r = Consumables.use(s, 0, two);
        assertEquals(true, r.ok, "有效 2 张目标应成功");
    }

    @Test
    void shopPhaseWithoutTargetsAllowed() {
        // 商店阶段使用不需目标的消耗品（如 hermit）→ 成功
        RunState s = Engine.createRun("red", 0, "TGTSHOP", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 手动推进到商店
        s.phase = Phase.SHOP;
        s.shop = null; // 无商店数据也能用 hermit（不需目标）
        s.consumables.add(new cn.quotidietium.balatro.engine.Consumable("tarot", "hermit"));
        Consumables.Result r = Consumables.use(s, 0, null);
        // hermit 不需目标，在 SHOP 可用
        assertEquals(true, r.ok, "hermit 在商店阶段应可用");
    }

    @Test
    void shopPhaseWithRoundTargetRejected() {
        // 商店阶段使用需手牌目标的消耗品 → 拒绝
        RunState s = Engine.createRun("red", 0, "TGTREJ", null);
        s.phase = Phase.SHOP;
        s.consumables.add(new cn.quotidietium.balatro.engine.Consumable("tarot", "lovers"));
        Consumables.Result r = Consumables.use(s, 0, null);
        assertNotNull(r.err, "lovers 需回合内目标，商店阶段应拒绝");
    }
}
