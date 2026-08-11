package cn.quotidietium.balatro.engine.joker;

import cn.quotidietium.balatro.engine.Consumable;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 天文台（Observatory）券效果回归：消耗品区每张与所出牌型匹配的星球牌使该牌型 ×1.5，
 * 多张累乘（对齐 Balatro wiki）。深审确认计分路径正确（playHand 第 629-637 行，
 * 在独立小丑结算之后施加），此处显式锁定防回归。
 *
 * <p>锚点：单张黑桃 A（高牌）。高牌 Lv1 = 5 筹码 × 1 倍；A = rankChips(14)=11 筹码。
 * 故筹码 = 5 + 11 = 16，倍率 = 1。
 * <ul>
 *   <li>无天文台：16 × 1 = 16；</li>
 *   <li>天文台 + 1 张冥王星（匹配高牌）：16 × 1.5 = 24；</li>
 *   <li>天文台 + 2 张冥王星：16 × 1.5 × 1.5 = 36。</li>
 * </ul>
 * <p>非匹配星球牌（如水星=对子）不触发 ×1.5。
 */
class ObservatoryTest {

    /** 开小盲注（无 Boss），手牌替换单张黑桃 A，加入指定星球消耗品后打出高牌。 */
    private static RunState playHighCard(String seed, boolean observatory, String... planetKeys) {
        RunState s = Engine.createRun("red", 0, seed);
        Engine.selectBlind(s, Data.BlindType.SMALL, false); // ROUND，无 Boss
        if (observatory) s.vouchers.add("observatory");
        s.hand.clear();
        cn.quotidietium.balatro.engine.Card ace = s.makeCard(14, 0); // 黑桃 A
        s.hand.add(ace);
        for (String pk : planetKeys) s.consumables.add(new Consumable("planet", pk));
        Engine.playHand(s, List.of(ace.id()));
        return s;
    }

    @Test
    void noObservatoryBaseline() {
        RunState s = playHighCard("OBS_BASE", false, "pluto");
        assertEquals(16, s.roundScore, "无天文台：16 × 1 = 16");
    }

    @Test
    void observatoryOneMatchingPlanetGivesX15() {
        RunState s = playHighCard("OBS_1P", true, "pluto"); // 冥王星匹配高牌
        assertEquals(24, s.roundScore, "天文台 + 1 张匹配星球：16 × 1.5 = 24");
    }

    @Test
    void observatoryTwoMatchingPlanetsStack() {
        RunState s = playHighCard("OBS_2P", true, "pluto", "pluto");
        assertEquals(36, s.roundScore, "天文台 + 2 张匹配星球：16 × 1.5 × 1.5 = 36（累乘）");
    }

    @Test
    void observatoryIgnoresNonMatchingPlanet() {
        RunState s = playHighCard("OBS_NOMATCH", true, "mercury"); // 水星=对子，不匹配高牌
        assertEquals(16, s.roundScore, "非匹配星球牌不触发 ×1.5：16 × 1 = 16");
    }
}
