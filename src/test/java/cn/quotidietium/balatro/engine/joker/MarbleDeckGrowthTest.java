package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * marble（大理石小丑）的牌组增长与产线一致性（R117）。
 *
 * <p>候选核验结论：牌组**无上限**为真版设计（fullDeck 可增长）；familiar/grim/incantation
 * 生成的牌已正确入 fullDeck（Consumables L242，与 REF L1709 一致，嫌疑否证）。本测试锁定
 * marble 链路：盲注开始 → 牌组 +1 石头牌（黑桃2壳）→ 经 startRound 重建进入抽牌堆。
 */
class MarbleDeckGrowthTest {

    @Test
    void marbleGrowsDeckEachBlindAndStonesReachDrawPile() {
        RunState s = Engine.createRun("red", 0, "MARB1", null);
        s.jokers.add(JokerRegistry.create("marble"));
        int base = s.fullDeck.size();

        Engine.selectBlind(s, Data.BlindType.SMALL, false); // 盲注开始 → +1 石头
        assertEquals(base + 1, s.fullDeck.size(), "牌组应 +1（无上限设计）");
        Card added = s.fullDeck.get(s.fullDeck.size() - 1);
        assertTrue(added.isStone(), "应为石头牌");
        assertEquals(2, added.rank(), "黑桃 2 壳（真版默认壳）");
        assertEquals(0, added.suit());

        // 赢盲 → 下回合重建：新石头应进入抽牌堆（在手牌/抽牌堆/弃牌堆三处之一可寻）
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertTrue(s.phase == Phase.SHOP);
        Engine.nextRound(s);
        Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
        // 语义（REF 同序 L439→L465）：onBlindStart 在重建+发牌之后触发——
        // 大盲开始新增的石头#2 本轮尚未入局（下一轮才进抽牌堆），三堆 = 牌组 - 1
        int inPlay = s.drawPile.size() + s.hand.size() + s.discardPile.size();
        assertEquals(s.fullDeck.size() - 1, inPlay, "最新石头下一轮才入局；其余牌不丢失");
        boolean found = s.drawPile.stream().anyMatch(c -> c.id() == added.id())
                || s.hand.stream().anyMatch(c -> c.id() == added.id())
                || s.discardPile.stream().anyMatch(c -> c.id() == added.id());
        assertTrue(found, "小盲开始添加的石头#1 已在大盲入局（可寻得）");
    }

    @Test
    void multipleMarblesStackPerBlind() {
        RunState s = Engine.createRun("red", 0, "MARB2", null);
        s.jokers.add(JokerRegistry.create("marble"));
        s.jokers.add(JokerRegistry.create("marble"));
        int base = s.fullDeck.size();
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        assertEquals(base + 2, s.fullDeck.size(), "双 marble 每盲注各 +1");
    }
}
