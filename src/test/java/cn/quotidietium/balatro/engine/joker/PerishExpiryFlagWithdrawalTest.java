package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 易腐到期后 flag 撤除的关键路径锁定（R115）。
 *
 * <p>endRound 的易腐到期只设 debuff=true 不重算 flags——正确性**完全依赖** startRound 开头的
 * 无条件 computeFlags（Engine L335）。本测试锁定「到期 → 零购买商店 → 下一回合」路径：
 * 过期海龟豆（handSize flag）不得再贡献手牌上限；若未来移除 startRound 的重算即回归失败。
 */
class PerishExpiryFlagWithdrawalTest {

    @Test
    void expiredPerishableJokerStopsContributingFlagsNextRoundWithoutAnyPurchase() {
        RunState s = Engine.createRun("red", 0, "PERISH1", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);

        // 海龟豆：size 巨大（不会因 size 归零自毁），易腐 1 回合后到期
        JokerInstance turtle = JokerRegistry.create("turtle");
        turtle.extra.put("size", 90);
        turtle.perishable = true;
        turtle.perishCount = 1;
        s.jokers.add(turtle);
        Engine.recomputeFlags(s);

        // 赢下本盲注：endRound 内 onRoundEnd(size 90→89) → 易腐倒计时归零 → debuff=true（不重算）
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertTrue(s.phase == Phase.SHOP, "应进商店");
        assertTrue(turtle.debuff, "易腐应已到期失效");

        // 零购买路径：直接 next → go（商店内无任何会触发 recompute 的操作）
        Engine.nextRound(s);                                  // small 完成 → big 选择
        assertTrue(Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false), "go 开始大盲");

        // 关键断言：startRound 的无条件 computeFlags 撤除了过期海龟的 handSize 贡献
        assertEquals(8, s.handSizeRound, "过期海龟豆不得再贡献手牌上限（应为基数 8，非 8+89）");
        assertEquals(1, s.jokers.size(), "海龟仍在列表（易腐=失效非销毁）");
    }

    @Test
    void activeTurtleStillContributesWithinItsLifetime() {
        // 对照：未到期的海龟照常贡献（防撤除逻辑误伤）
        RunState s = Engine.createRun("red", 0, "PERISH2", null);
        JokerInstance turtle = JokerRegistry.create("turtle");
        turtle.extra.put("size", 2);
        s.jokers.add(turtle);
        Engine.recomputeFlags(s);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        assertEquals(10, s.handSizeRound, "活跃海龟 +2 手牌上限");
    }
}
