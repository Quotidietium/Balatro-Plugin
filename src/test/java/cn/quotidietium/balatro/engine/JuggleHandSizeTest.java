package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * juggle（灵活标签）手牌上限语义（R118）。
 *
 * <p>①候选澄清：mailin 是金钱小丑、certificate 产牌（入 hand+fullDeck）——均不涉消耗品槽；
 * 真正「产物×满槽静默拒收」类（vagabond/蓝蜡封/priestess 等）经 addConsumableKey，
 * R17 已逐行核验与 REF L202-205 一致。本测试锁定 juggle 语义：
 * 一次性消费（startRound 读取即 remove，无跨阶段残留）+ 双标签不叠加（boolean 覆盖，REF 同）。
 */
class JuggleHandSizeTest {

    @Test
    void juggleGrantsPlus3OnceNextRoundThenConsumed() {
        RunState s = Engine.createRun("red", 0, "JUG1", null);
        s.gainTag("juggle"); // 模拟跳过获得灵活标签
        assertTrue(Engine.selectBlind(s, Data.BlindType.SMALL, false), "开始小盲");
        assertEquals(11, s.handSizeRound, "下一回合手牌上限 8+3");
        // 消费即除：赢盲后再下一回合应回到 8
        s.roundScore = s.blindTarget;
        Engine.playHand(s, java.util.List.of(s.hand.get(0).id()));
        Engine.nextRound(s);
        Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
        assertEquals(8, s.handSizeRound, "标签一次性消费，下回合回落 8（无残留）");
    }

    @Test
    void doubleJuggleDoesNotStack() {
        RunState s = Engine.createRun("red", 0, "JUG2", null);
        s.gainTag("juggle");
        s.gainTag("juggle"); // 连续两个灵活标签（小盲+大盲都跳过的场景）
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        assertEquals(11, s.handSizeRound, "双标签 boolean 覆盖：+3 一次而非 +6（REF 同）");
    }
}
