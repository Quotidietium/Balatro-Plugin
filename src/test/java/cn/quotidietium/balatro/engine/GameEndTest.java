package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 游戏结束相关测试：出牌数耗尽应判失败结束；无尽模式重置 won 标记。
 */
class GameEndTest {

    @Test
    void losingAllHandsEndsTheRun() {
        RunState s = Engine.createRun("red", 0, "LOSE");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.blindTarget = Long.MAX_VALUE; // 不可达，确保不会提前通关盲注
        int hands = s.handsLeft;

        for (int i = 0; i < hands; i++) {
            // 每次打出第一张手牌；出牌后手牌会补满，故 hand 始终非空
            Engine.playHand(s, List.of(s.hand.get(0).id()));
        }

        assertEquals(0, s.handsLeft, "出牌数应耗尽");
        assertEquals(Phase.END, s.phase, "耗尽出牌数后应进入结束阶段");
        assertTrue(s.lost, "应判失败");
        assertFalse(s.won);
    }

    @Test
    void continueEndlessResetsWonFlag() {
        // 模拟通关(ante 8)后 won=true；进入无尽应重置，避免后续重复触发 finishRun
        RunState s = Engine.createRun("red", 0, "ENDLESS");
        s.endlessPending = true;
        s.won = true;

        boolean ok = Engine.continueEndless(s);

        assertTrue(ok);
        assertTrue(s.endless, "应进入无尽模式");
        assertFalse(s.won, "无尽模式应重置 won 标记");
        assertFalse(s.endlessPending);
    }
}
