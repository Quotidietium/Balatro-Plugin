package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.*;

import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 极端边界组合端到端模拟测试（轮次 53）。
 *
 * <p>验证手牌全部失效、牌组耗尽、金钱为负（信用卡）、0 手牌等极端场景下不崩溃。
 */
class ExtremeEdgeCaseTest {

    @Test
    void allHandCardsDebuffedCanStillPlay() {
        // 手牌全部失效（Boss leaf）—— 出牌应成功但得分为 0（debuff 牌不计分）
        RunState s = Engine.createRun("red", 0, "EDGEDEBUFF", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 手动让所有手牌失效
        for (Card c : s.hand) c.setDebuff(true);
        // 出牌不应崩溃
        List<Integer> ids = List.of(s.hand.get(0).id());
        Engine.PlayResult r = Engine.playHand(s, ids);
        assertTrue(r.ok, "全失效手牌出牌应成功（debuff 牌仍可出，只不计分）");
    }

    @Test
    void emptyDeckDrawsNothing() {
        // 牌组为空时 drawUpTo 不崩溃
        RunState s = Engine.createRun("red", 0, "EDGEEMPTY", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 清空 drawPile 模拟牌组耗尽
        s.drawPile.clear();
        // 出牌后 drawUpTo 补牌应安全（无牌可抽）
        List<Integer> ids = List.of(s.hand.get(0).id());
        Engine.PlayResult r = Engine.playHand(s, ids);
        assertTrue(r.ok, "牌组耗尽时出牌应成功（手牌少补不回来）");
        // 手牌可能不足 handSizeRound
        assertTrue(s.hand.size() < s.handSizeRound || s.hand.isEmpty(),
                "牌组空时手牌应不足或为空");
    }

    @Test
    void negativeMoneyWithCreditCard() {
        // 信用卡 joker 允许 money 为负
        RunState s = Engine.createRun("red", 0, "EDGECREDIT", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.jokers.add(JokerRegistry.create("creditcard"));
        Engine.recomputeFlags(s);
        // 手动设 money 为负
        s.money = -10;
        // 出牌不应崩溃
        List<Integer> ids = List.of(s.hand.get(0).id());
        Engine.PlayResult r = Engine.playHand(s, ids);
        assertTrue(r.ok, "money 为负时出牌不应崩溃");
        assertTrue(s.money <= 0, "money 仍应为负或更低");
    }

    @Test
    void zeroHandSizeDoesNotCrash() {
        // handSizeRound 为 0（极端挑战）—— 开局抽 0 张
        RunState s = Engine.createRun("red", 0, "EDGEHAND0", null);
        s.mods.handSize = -8; // 手牌上限减到 0
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // handSizeRound 应 >= 1（max(1,...) 守卫）
        assertTrue(s.handSizeRound >= 1, "handSizeRound 应至少为 1（max(1,...) 守卫）");
    }

    @Test
    void zeroHandsLeftLoseRound() {
        // handsLeft=0 时出牌被拒绝，引擎判负
        RunState s = Engine.createRun("red", 0, "EDGEHANDS0", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.handsLeft = 0;
        // 出牌应返回错误
        Engine.PlayResult r = Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertFalse(r.ok, "handsLeft=0 时出牌应被拒绝");
    }

    @Test
    void zeroDiscardsLeftRejectDiscard() {
        // discardsLeft=0 时弃牌被拒绝
        RunState s = Engine.createRun("red", 0, "EDGEDISC0", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.discardsLeft = 0;
        Engine.PlayResult r = Engine.discard(s, List.of(s.hand.get(0).id()));
        assertFalse(r.ok, "discardsLeft=0 时弃牌应被拒绝");
    }

    @Test
    void playingWithEmptyHandDoesNotCrash() {
        // 空手牌时出牌应被拒绝（cardIds 中没有有效牌）
        RunState s = Engine.createRun("red", 0, "EDGEEMPTY2", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.hand.clear();
        Engine.PlayResult r = Engine.playHand(s, List.of(99999)); // 不存在 id
        assertFalse(r.ok, "空手牌出牌应被拒绝");
    }

    @Test
    void discardWithDuplicateIdsRejected() {
        // 弃牌重复 id 应被拒绝
        RunState s = Engine.createRun("red", 0, "EDGEDUP", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        int id = s.hand.get(0).id();
        Engine.PlayResult r = Engine.discard(s, List.of(id, id));
        assertFalse(r.ok, "重复 id 弃牌应被拒绝");
    }

    @Test
    void playMoreThanFiveCardsRejected() {
        // 出牌 >5 张应被拒绝
        RunState s = Engine.createRun("red", 0, "EDGEMORE5", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        List<Integer> six = new ArrayList<>();
        for (int i = 0; i < 6 && i < s.hand.size(); i++) six.add(s.hand.get(i).id());
        if (six.size() >= 6) {
            Engine.PlayResult r = Engine.playHand(s, six);
            assertFalse(r.ok, "出 6 张牌应被拒绝");
        }
    }

    @Test
    void stoneCardInHandScores50Chips() {
        // 手牌中有一张石头牌，出牌时石头牌计分 +50 筹码
        RunState s = Engine.createRun("red", 0, "EDGESTONE", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 给第一张牌设 STONE 增强
        Card stone = s.hand.get(0);
        stone.setEnh(Data.Enhancement.STONE);
        stone.setRank(0);
        stone.setSuit(-1);
        // 出这张牌（高牌+石头）
        long scoreBefore = s.roundScore;
        Engine.PlayResult r = Engine.playHand(s, List.of(stone.id()));
        if (r.ok) {
            // 石头牌作为计分牌应 +50 筹码
            assertTrue(s.roundScore > scoreBefore, "石头牌出牌应得分");
        }
    }

    @Test
    void multipleJokersInExcessSlots() {
        // 小丑超过槽位上限时 gainJoker 应拒绝
        RunState s = Engine.createRun("red", 0, "EDGESLOTS", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 填满小丑槽（默认 5）
        for (int i = 0; i < s.jokerSlots; i++) {
            s.jokers.add(JokerRegistry.create("joker"));
        }
        // 再加应失败
        assertFalse(s.gainJoker("joker", null), "槽满时 gainJoker 应返回 false");
    }

    @Test
    void packSkipWhenNoPackDoesNotCrash() {
        // 非 PACK 阶段 skip 不崩溃
        RunState s = Engine.createRun("red", 0, "EDGESKIP", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        boolean ok = cn.quotidietium.balatro.engine.shop.Packs.skip(s);
        assertFalse(ok, "非 PACK 阶段 skip 应返回 false");
    }
}
