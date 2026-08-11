package cn.quotidietium.balatro.engine;

import cn.quotidietium.balatro.engine.joker.BasicJoker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数值溢出饱和验证（轮次 54）：无尽模式极端 build 下 long 环绕防护。
 *
 * <p>原版 JS 的 Number 是 double，不会环绕；Java long 会环绕成负数，
 * 导致：负 roundScore 永远达不到目标分（必败软锁）、负金钱→负利息恶性循环。
 * {@link RunState#satAdd} 饱和到极值，对齐 double 不环绕语义。
 */
class OverflowSaturationTest {

    @Test
    void satAddNormal() {
        assertEquals(5, RunState.satAdd(2, 3));
        assertEquals(-5, RunState.satAdd(-2, -3));
        assertEquals(0, RunState.satAdd(3, -3));
        assertEquals(Long.MAX_VALUE - 1, RunState.satAdd(Long.MAX_VALUE - 1, 0));
    }

    @Test
    void satAddPositiveOverflowSaturates() {
        assertEquals(Long.MAX_VALUE, RunState.satAdd(Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE, RunState.satAdd(Long.MAX_VALUE - 5, 10));
        assertEquals(Long.MAX_VALUE, RunState.satAdd(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void satAddNegativeOverflowSaturates() {
        assertEquals(Long.MIN_VALUE, RunState.satAdd(Long.MIN_VALUE, -1));
        assertEquals(Long.MIN_VALUE, RunState.satAdd(Long.MIN_VALUE + 5, -10));
        assertEquals(Long.MIN_VALUE, RunState.satAdd(Long.MIN_VALUE, Long.MIN_VALUE));
    }

    @Test
    void satAddMixedSignsNeverOverflow() {
        assertEquals(Long.MAX_VALUE - 5, RunState.satAdd(Long.MAX_VALUE, -5));
        assertEquals(-6, RunState.satAdd(Long.MIN_VALUE, Long.MAX_VALUE - 5));
    }

    @Test
    void gainMoneyNeverWrapsNegative() {
        RunState s = Engine.createRun("red", 0, "ovf1");
        s.money = Long.MAX_VALUE - 10;
        s.gainMoney(100);
        assertEquals(Long.MAX_VALUE, s.money, "金钱饱和到 Long.MAX，不环绕成负数");
        s.gainMoney(50);
        assertEquals(Long.MAX_VALUE, s.money, "已饱和后继续加仍保持 Long.MAX");
    }

    @Test
    void gainMoneyNormalAccumulationUnaffected() {
        RunState s = Engine.createRun("red", 0, "ovf2");
        long before = s.money;
        s.gainMoney(7);
        assertEquals(before + 7, s.money);
        s.gainMoney(-3);
        assertEquals(before + 4, s.money);
    }

    @Test
    void toTheMoonPayoutAtMaxMoneyDoesNotOverflowGain() {
        // 奔月 payout = money/5（金钱极大时 ~1.8e18）；多张奔月叠加时 gain 累加不得环绕
        RunState s = Engine.createRun("red", 0, "ovf3");
        s.money = Long.MAX_VALUE;
        long payout = BasicJoker.TOTHEMOON.onRoundEnd(s, new JokerInstance(BasicJoker.TOTHEMOON));
        assertEquals(Long.MAX_VALUE / 5, payout);
        // 6 张奔月 payout 累加（6 × 1.8e18 > Long.MAX）：饱和后不环绕成负数
        long gain = 0;
        for (int i = 0; i < 6; i++) gain = RunState.satAdd(gain, payout);
        assertTrue(gain > 0, "多张奔月 payout 累加饱和，不为负");
        assertEquals(Long.MAX_VALUE, gain);
    }

    @Test
    void scoreAccumulationSaturationKeepsWinDeterminable() {
        // 模拟 roundScore 已接近上限时再得一手满分：不环绕成负，胜负判定不被破坏
        long roundScore = Long.MAX_VALUE - 100;
        long score = Long.MAX_VALUE; // Math.round 饱和后的单手极端分
        long total = RunState.satAdd(roundScore, score);
        assertEquals(Long.MAX_VALUE, total);
        long blindTarget = Long.MAX_VALUE; // blindTarget 同为 Math.round 饱和上限
        assertTrue(total >= blindTarget, "饱和后得分仍可达标，不会必败软锁");
    }
}
