package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Space 计分前升级的【每手恰一次】约束（R132 回归——R130 曾把计分前块误插逐卡×逐重触发
 * 循环内，5 张手牌会掷 5 次骰、一手可能升 2+ 级；Space Wiki："1 in 4 chance" 每手一次）。
 */
class SpaceOncePerHandTest {

    @Test
    void spaceUpgradesAtMostOncePerHandAcrossSeeds() {
        for (int i = 0; i < 300; i++) {
            RunState s = Engine.createRun("red", 0, "SPACE1PH" + i, null);
            s.jokers.add(JokerRegistry.create("space"));
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            int lvl = s.handLevel(Data.HandType.PAIR);
            Engine.playHand(s, List.of(s.hand.get(0).id(), s.hand.get(1).id()));
            int delta = s.handLevel(Data.HandType.PAIR) - lvl;
            assertTrue(delta <= 1, "单手升级不得 >1（seed=" + i + " 实际 +" + delta + "）");
        }
    }

    @Test
    void fiveCardHandStillAtMostOnce() {
        for (int i = 0; i < 300; i++) {
            RunState s = Engine.createRun("red", 0, "SPACE5PH" + i, null);
            s.jokers.add(JokerRegistry.create("space"));
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            Data.HandType t = Engine.evaluateHand(s, s.hand).type;
            int lvl = s.handLevel(t);
            int n = Math.min(5, s.hand.size());
            List<Integer> ids = new java.util.ArrayList<>();
            for (int k = 0; k < n; k++) ids.add(s.hand.get(k).id());
            Engine.playHand(s, ids);
            int delta = s.handLevel(t) - lvl;
            assertTrue(delta <= 1, "五张手牌同样每手至多 +1（seed=" + i + " 实际 +" + delta + "）");
        }
    }
}
