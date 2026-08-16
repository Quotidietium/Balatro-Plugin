package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R137：ScoreContext.gainConsumable 与 RunState.gainConsumable 池口径统一。
 *
 * <p>缺陷：ctx 版（计分钩子内公共 API，第三方小丑可用）此前用全量 SPECTRALS/TAROTS——
 * 幻灵分支违反 R128 产出规则（灵魂/黑洞仅幽灵包 ~0.3% 产出，不得经随机发放路径出现），
 * 塔罗分支不过滤禁入清单（R108/R123）。修复：两版共用 RunState.tarotGrantPool/
 * spectralGrantPool。本测试以批量抽取统计锁定 ctx 路径的池边界。
 */
class ScoreContextGrantPoolTest {

    private static ScoreContext ctx(RunState s) {
        return new ScoreContext(s, Data.HandType.HIGH, 0, 0, List.of(), List.of(), new ArrayList<>());
    }

    /** ctx 幻灵分支：大量随机发放中灵魂/黑洞绝不出现在产出池。 */
    @Test
    void ctxSpectralGrantNeverYieldsSpecials() {
        RunState s = Engine.createRun("red", 0, "CTXSPEC1", null);
        s.consumableSlots = 400;
        s.consumables.clear();
        ScoreContext c = ctx(s);
        for (int i = 0; i < 400; i++) c.gainConsumable("spectral");
        // 旧实现：每次 1/18 抽中灵魂，400 次全避开的概率 ≈ e^-22（必失败）；新实现结构上不可能
        for (Consumable got : s.consumables) {
            assertFalse(Data.SPECIAL_SPECTRALS.contains(got.key),
                    "ctx 随机发放不得产出 SPECIAL 幻灵：" + got.key);
        }
        assertTrue(s.consumables.size() > 300, "应有足量发放（槽位已放宽）：" + s.consumables.size());
        // 共享池单元断言：池本身即排除 SPECIAL
        for (Data.Spectral sp : s.spectralGrantPool()) {
            assertFalse(Data.SPECIAL_SPECTRALS.contains(sp.key));
        }
    }

    /** ctx 塔罗分支：禁入清单（真版煎蛋卷/易碎品）生效。 */
    @Test
    void ctxTarotGrantRespectsBans() {
        RunState s = Engine.createRun("red", 0, "CTXTAROT1", null);
        s.mods.bannedTarots.add("fool");
        s.consumableSlots = 300;
        s.consumables.clear();
        ScoreContext c = ctx(s);
        for (int i = 0; i < 300; i++) c.gainConsumable("tarot");
        for (Consumable got : s.consumables) {
            assertFalse("fool".equals(got.key), "禁入塔罗不得经 ctx 发放");
        }
        assertTrue(s.consumables.size() > 200, "其余塔罗应正常发放：" + s.consumables.size());
        // 池单元断言
        for (Data.Tarot t : s.tarotGrantPool()) {
            assertFalse(s.mods.bannedTarots.contains(t.key));
        }
    }

    /** 池被禁空时不消耗 consumable 流、不发放（与 state 版一致）。 */
    @Test
    void emptyPoolsSkipGrant() {
        RunState s = Engine.createRun("red", 0, "CTXEMPTY1", null);
        s.mods.bannedSpectrals.addAll(Data.SPECTRALS.stream().map(sp -> sp.key)
                .filter(k -> !Data.SPECIAL_SPECTRALS.contains(k)).toList());
        s.consumableSlots = 10;
        s.consumables.clear();
        long consumedBefore = 0;
        // 取一次 consumable 流基线（探测其推进：用 planet 分支发一张，再断言幽灵空池分支零新增）
        ScoreContext c = ctx(s);
        c.gainConsumable("planet");
        consumedBefore = s.consumables.size();
        for (int i = 0; i < 10; i++) c.gainConsumable("spectral");
        assertTrue(s.spectralGrantPool().isEmpty(), "全禁后池应为空");
        assertEqualsCount(consumedBefore, s.consumables.size());
    }

    private static void assertEqualsCount(long expected, long actual) {
        assertTrue(expected == actual, "空池分支不得发放（expected=" + expected + " actual=" + actual + "）");
    }
}
