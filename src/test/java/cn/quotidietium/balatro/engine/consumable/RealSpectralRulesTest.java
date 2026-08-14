package cn.quotidietium.balatro.engine.consumable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 幽灵牌真版规则回归（R128，第 58 处修复族；Spectral Cards Wiki）。
 *
 * <p>①妖术=附加**多彩**并摧毁其他小丑（REF 误为负片——R17 逐行对 REF 的经典盲区）；
 * ②灵魂/黑洞仅在幽灵补充包以 ~0.3% 出现，商店与小丑产出的随机幽灵池排除二者。
 */
class RealSpectralRulesTest {

    @Test
    void hexAddsPolychromeNotNegative() {
        RunState s = Engine.createRun("red", 0, "HEX1", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false); // hex 需在回合内使用
        s.jokers.add(JokerRegistry.create("joker"));
        s.jokers.add(JokerRegistry.create("greedy"));
        s.consumables.add(new cn.quotidietium.balatro.engine.Consumable("spectral", "hex"));
        var r = Consumables.use(s, 0, List.of());
        assertTrue(r.ok, r.err);
        assertEquals(1, s.jokers.size(), "摧毁其他小丑");
        assertEquals(Data.Edition.POLY, s.jokers.get(0).edition, "妖术应附加多彩（非负片）");
    }

    @Test
    void shopAndGrantedSpectralsNeverIncludeSoulOrBlackHole() {
        // 商店：幽灵牌组（幽灵牌进商店）反复开店收集
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 80; i++) {
            RunState s = Engine.createRun("ghost", 0, "SPSHOP" + i, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            s.roundScore = s.blindTarget;
            Engine.playHand(s, List.of(s.hand.get(0).id()));
            assertTrue(s.phase == Phase.SHOP);
            for (var c : s.shop.cards) {
                if ("spectral".equals(c.kind)) seen.add(c.key);
            }
        }
        assertFalse(seen.contains("soul"), "商店不得出售灵魂（真版排除）");
        assertFalse(seen.contains("blackhole"), "商店不得出售黑洞");
        assertTrue(seen.size() >= 5, "其余幽灵应正常出现（防过滤误伤全池）：" + seen.size());

        // 小丑产出路径（vagabond 类经 gainConsumable(spectral)）：直接调用验证
        for (int i = 0; i < 300; i++) {
            RunState s = Engine.createRun("red", 0, "SPGEN" + i, null);
            s.gainConsumable("spectral");
            for (var c : s.consumables) {
                assertFalse(Data.SPECIAL_SPECTRALS.contains(c.key),
                        "随机产出不得给出灵魂/黑洞：" + c.key);
            }
        }
    }

    @Test
    void spectralPacksMayRarelyContainSpecials() {
        // 统计：开 3000 个幽灵包，灵魂/黑洞应罕见（~0.3%/张）而非均匀 1/18≈5.6%
        int soul = 0, blackhole = 0, cards = 0;
        for (int i = 0; i < 3000; i++) {
            RunState s = Engine.createRun("red", 0, "SPPACK" + i, null);
            Data.Pack def = null;
            for (Data.Pack p : Data.PACKS) if (p.key.equals("spectral1")) def = p;
            cn.quotidietium.balatro.engine.shop.Packs.open(s, def);
            for (var c : s.pack.cards) {
                cards++;
                if ("soul".equals(c.key)) soul++;
                if ("blackhole".equals(c.key)) blackhole++;
            }
        }
        // 0.3%/张 × 6000 张 ≈ 18 期望；上界远低于均匀（6000/18≈333）
        assertTrue(soul + blackhole < 120, "特殊幽灵应罕见（实际 " + (soul + blackhole) + "/" + cards + "）");
        assertTrue(cards >= 6000);
    }
}
