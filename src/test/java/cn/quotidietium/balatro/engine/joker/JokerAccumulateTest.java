package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Consumable;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.consumable.Consumables;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 累积型小丑钩子回归：constellation(每用星球牌 +0.1x) / hologram(每加入游戏牌 +0.25x)。
 * 两者曾漏掉累积钩子（只有 onScore），导致恒为 ×1.0 完全失效（审计 #13/#14）。
 */
class JokerAccumulateTest {

    @Test
    void constellationGainsOnPlanetUse() {
        RunState s = Engine.createRun("red", 0, "CONST");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        JokerInstance j = JokerRegistry.create("constellation");
        s.jokers.add(j);
        s.consumables.add(new Consumable("planet", "pluto"));

        Consumables.Result r = Consumables.use(s, 0, List.of());

        assertTrue(r.ok, "星球牌应可使用");
        assertEquals(0.1, (Double) j.extra.get("x"), 1e-9, "使用星球牌后应累积 +0.1");

        // 再用一张星球牌：继续累积
        s.consumables.add(new Consumable("planet", "mars"));
        Consumables.use(s, 0, List.of());
        assertEquals(0.2, (Double) j.extra.get("x"), 1e-9, "应继续累积");
    }

    @Test
    void hologramGainsOnCardAdded() {
        RunState s = Engine.createRun("red", 0, "HOLO");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        JokerInstance j = JokerRegistry.create("hologram");
        s.jokers.add(j);

        s.addCardToDeck(s.randomPlayingCard());
        assertEquals(0.25, (Double) j.extra.get("x"), 1e-9, "加入游戏牌后应累积 +0.25");

        s.addCardToDeck(s.randomPlayingCard());
        assertEquals(0.5, (Double) j.extra.get("x"), 1e-9, "应继续累积");
    }
}
