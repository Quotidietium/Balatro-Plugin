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
    void marbleStoneUsesTwoOfSpadesShell() {
        // 对齐 jokers.js marble + engine.js makeCard 默认壳：rank=2/suit=0 + STONE。
        // 壳点数在增强被移除（吸血鬼）或纯点数匹配（邮寄返利目标 2）时暴露，须与原版一致。
        RunState s = Engine.createRun("red", 0, "MARBLE");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.jokers.add(JokerRegistry.create("marble"));
        int deckBefore = s.fullDeck.size();

        // onBlindStart 由 startRound 派发；直接重进一个盲注触发
        s.phase = cn.quotidietium.balatro.engine.Phase.BLIND_SELECT;
        s.nextBlind = "big";
        Engine.selectBlind(s, Data.BlindType.BIG, false);

        assertEquals(deckBefore + 1, s.fullDeck.size(), "应加入 1 张石头牌");
        cn.quotidietium.balatro.engine.Card stone = s.fullDeck.get(s.fullDeck.size() - 1);
        assertTrue(stone.isStone() && stone.enh() == Data.Enhancement.STONE);
        assertEquals(2, stone.rank(), "大理石石头牌壳应为 rank=2（对齐原版）");
        assertEquals(0, stone.suit(), "大理石石头牌壳应为 suit=0 黑桃（对齐原版）");
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
