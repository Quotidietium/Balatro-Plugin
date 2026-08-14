package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 产物授予与销毁时序锁定（R119）。
 *
 * <p>①sixthsense：首手单张 6 → onPlayHand 销毁（removeCardFromDeck）→ drawUpTo 在其后执行
 * （L708）补回手牌；fullDeck 同步收缩；获得一张幻灵。第二手不再触发。
 * <p>②vagabond（产物×满槽）：资金 ≤$4 出牌获塔罗；满槽时 addConsumableKey 静默拒收
 * （R17 核验与 REF L202-205 一致）——不崩溃、不部分入列。
 */
class ProductGrantAndSixthSenseTest {

    private static RunState inRound(String seed) {
        RunState s = Engine.createRun("red", 0, seed, null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        return s;
    }

    @Test
    void sixthSenseDestroysRefillsAndGrantsSpectral() {
        RunState s = inRound("SS1");
        s.jokers.add(JokerRegistry.create("sixthsense"));
        int deckBase = s.fullDeck.size();

        // 首手单张 6：把第一张手牌改为 6 后单打
        var six = s.hand.get(0);
        six.setRank(6);
        int sixId = six.id();
        int handSize = s.handSizeRound;
        Engine.playHand(s, List.of(sixId));

        // 销毁：不在手牌/fullDeck；补牌：手牌回到 handSizeRound；产物：一张幻灵
        assertFalse(s.hand.stream().anyMatch(c -> c.id() == sixId), "6 应被销毁");
        assertEquals(deckBase - 1, s.fullDeck.size(), "牌组应收缩 1");
        assertEquals(handSize, s.hand.size(), "drawUpTo 在销毁后执行，手牌补回");
        assertEquals(1, s.consumables.size(), "获得一张消耗品");
        assertEquals("spectral", s.consumables.get(0).kind, "种类应为幻灵");
        assertEquals(Phase.ROUND, s.phase, "不应赢盲干扰断言（单 6 得分远低于目标）");

        // 第二手再单打 6：不再触发（每回合仅首手）
        int deckNow = s.fullDeck.size();
        var six2 = s.hand.get(0);
        six2.setRank(6);
        Engine.playHand(s, List.of(six2.id()));
        assertEquals(deckNow, s.fullDeck.size(), "第二手不再销毁");
        assertEquals(1, s.consumables.size(), "第二手不再获得");
    }

    @Test
    void vagabondGrantsTarotWhenPoorAndSilentlyRejectsWhenFull() {
        RunState s = inRound("VAG1");
        s.jokers.add(JokerRegistry.create("vagabond"));
        s.money = 3; // ≤ $4

        // 有空槽：出牌获塔罗
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertEquals(1, s.consumables.size(), "贫穷出牌应获一张塔罗");
        assertEquals("tarot", s.consumables.get(0).kind);

        // 填满普通槽（2/2）后再出牌：静默拒收（不崩溃、不入列）
        assertTrue(s.addConsumableKey("tarot", "fool"));
        int deckBefore = s.fullDeck.size();
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertEquals(2, s.consumables.size(), "满槽应静默拒收（不部分入列）");
        assertEquals(Phase.ROUND, s.phase, "满槽拒收不得崩溃");
        assertEquals(deckBefore, s.fullDeck.size());
    }
}
