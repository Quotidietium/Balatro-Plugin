package cn.quotidietium.balatro.engine.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import org.junit.jupiter.api.Test;

/**
 * R162：赌注贴纸生成率统计 fuzz——makeJokerItem 的 eternal（黑注 3+）/
 * perishable（橙注 6+）/rental（金注 7）各以 st.chance(0.3) 生成。
 * R124 逐档对照了效果开关，未做生成率统计验证；本测试大样本断言：
 * 对应档位贴纸率收敛于 30%±3%（N=4000，二项 σ≈0.72%，±3% ≈ ±4σ），
 * 白注（0 档）三种贴纸零生成（确定性，非统计）。
 */
class StakeStickerRateTest {

    private static final int N = 4000;

    private static long[] sample(int stake, String seed) {
        RunState s = Engine.createRun("red", stake, seed, null);
        long eternal = 0, perishable = 0, rental = 0, jokers = 0;
        for (int i = 0; i < N; i++) {
            s.jokers.clear(); // 清持有避免 owned 去重缩小池
            Shop.CardItem it = Shop.makeJokerItem(s, null, null);
            if (it.joker != null) {
                jokers++;
                if (it.joker.eternal) eternal++;
                if (it.joker.perishable) perishable++;
                if (it.joker.rental) rental++;
            }
        }
        // 全部生成应为小丑（red 标准局池非空，永不回落塔罗）
        assertEquals(N, jokers, "标准局应全部生成小丑（stake=" + stake + "）");
        return new long[]{eternal, perishable, rental};
    }

    private static void assertRate(long count, int stake, String kind) {
        double p = (double) count / N;
        assertTrue(p > 0.27 && p < 0.33,
                kind + " 贴纸率应收敛于 30%±3%（stake=" + stake + "，实测 " + String.format("%.1f%%", p * 100) + "）");
    }

    @Test
    void stickerRatesMatchStakeTiers() {
        long[] w = sample(0, "STKRATE0");
        assertEquals(0, w[0], "白注不生成永恒贴纸");
        assertEquals(0, w[1], "白注不生成易腐贴纸");
        assertEquals(0, w[2], "白注不生成租赁贴纸");

        long[] b = sample(3, "STKRATE3");
        assertRate(b[0], 3, "永恒");
        assertEquals(0, b[1], "黑注无易腐贴纸");
        assertEquals(0, b[2], "黑注无租赁贴纸");

        long[] o = sample(6, "STKRATE6");
        assertRate(o[0], 6, "永恒");
        assertRate(o[1], 6, "易腐");
        assertEquals(0, o[2], "橙注无租赁贴纸");

        long[] g = sample(7, "STKRATE7");
        assertRate(g[0], 7, "永恒");
        assertRate(g[1], 7, "易腐");
        assertRate(g[2], 7, "租赁");
    }
}
