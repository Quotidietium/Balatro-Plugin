package cn.quotidietium.balatro.engine.consumable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Consumable;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import cn.quotidietium.balatro.engine.shop.Shop;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R137 真版对齐：通灵板（ouija）/ 灵质（ectoplasm）的手牌上限 -1 为**整局永久**。
 *
 * <p>缺陷（REF 继承，Java 同病）：两卡直接 {@code handSizeBase -= 1}，而
 * {@code applyVouchersPassive} 每回合从 8 无条件重建 handSizeBase——减量在下回合
 * 被抹除（真版为永久减量，见 Ouija/Ectoplasm Wiki「-1 hand size」）。
 * 修复：减量记入 {@code RunState.handSizePerm}（跨回合存活），重建后叠加；
 * 当前回合 handSizeRound 同步下调（真版即时生效，下限 1 与 startRound 一致）。
 */
class HandSizePermanenceTest {

    /** ouija：用后本回合生效，且跨回合存活（重建后仍为 7 而非回到 8）。 */
    @Test
    void ouijaReductionSurvivesRoundRebuild() {
        RunState s = Engine.createRun("red", 0, "OUJPERM1", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        assertEquals(8, s.handSizeBase, "开局基础手牌上限应为 8");
        assertEquals(8, s.handSizeRound);

        s.consumables.add(new Consumable("spectral", "ouija"));
        Consumables.Result r = Consumables.use(s, 0, List.of());
        assertTrue(r.ok, "ouija 应可使用：" + r.err);
        assertEquals(-1, s.handSizePerm, "永久修正应记入 handSizePerm");
        assertEquals(7, s.handSizeRound, "当前回合即时生效");

        // 推进到下一回合（商店 → next → 开打），验证重建不再抹除
        Shop.openShop(s);
        assertTrue(Engine.nextRound(s));
        Engine.selectBlind(s, Data.BlindType.BIG, false);
        assertEquals(7, s.handSizeBase, "R137 修复点：减量应跨回合存活（旧实现被重建抹回 8）");
        assertEquals(7, s.handSizeRound);
    }

    /** ectoplasm：同 ouija 的永久减量语义（附带给随机小丑上负片）。 */
    @Test
    void ectoplasmReductionSurvivesRoundRebuild() {
        RunState s = Engine.createRun("red", 0, "ECTPERM1", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        assertTrue(s.gainJoker(JokerRegistry.allJokersOrdered().get(0).key(), null), "需至少一个小丑供 ectoplasm 上负片");

        s.consumables.add(new Consumable("spectral", "ectoplasm"));
        Consumables.Result r = Consumables.use(s, 0, List.of());
        assertTrue(r.ok, "ectoplasm 应可使用：" + r.err);
        assertEquals(-1, s.handSizePerm);
        assertEquals(7, s.handSizeRound);

        Shop.openShop(s);
        assertTrue(Engine.nextRound(s));
        Engine.selectBlind(s, Data.BlindType.BIG, false);
        assertEquals(7, s.handSizeBase, "ectoplasm 减量同样应跨回合存活");
        assertEquals(7, s.handSizeRound);
    }

    /** 重复使用的下限：handSizeRound 不低于 1（与 startRound 的 Math.max(1,·) 口径一致）。 */
    @Test
    void repeatedOuijaFloorsRoundHandSizeAtOne() {
        RunState s = Engine.createRun("red", 0, "OUJFLOOR", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        for (int i = 0; i < 12; i++) {
            s.consumables.clear();
            s.consumables.add(new Consumable("spectral", "ouija"));
            assertTrue(Consumables.use(s, 0, List.of()).ok);
        }
        assertEquals(-12, s.handSizePerm, "永久减量持续累计");
        assertEquals(1, s.handSizeRound, "回合内下限 1");
        Shop.openShop(s);
        Engine.nextRound(s);
        Engine.selectBlind(s, Data.BlindType.BIG, false);
        assertEquals(1, s.handSizeRound, "重建后同样下限 1（startRound 的 max(1,·)）");
    }
}
