package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 商店买丑压力模糊测试（轮次 R64）。
 *
 * <p>现有 {@code FullMatrixSimulationTest} 在商店阶段直接 nextRound（跳过购买），从不触发
 * makeJokerItem/buyCard/随机小丑计分路径。本测试在商店阶段**主动购买可负担的小丑**，
 * 跨多种子驱动完整流程，压力覆盖：
 * <ul>
 *   <li>商店小丑生成（全 150 小丑池 + 版本 + 永恒/租赁/易腐/黑红橙金注修饰）；</li>
 *   <li>buyCard → gainJoker → recomputeFlags；</li>
 *   <li>任意小丑组合下的计分 / onRoundEnd 发金 / 租赁扣款 / 易腐失效；</li>
 *   <li>多小丑 × Boss 约束下的 playHand 不崩溃、roundScore 不为负。</li>
 * </ul>
 *
 * <p>断言：无异常；每步后 roundScore ≥ 0（satAdd 保证）；money ≥ -20（信用卡下限）；
 * 终态合法（END/won/lost）。
 */
class FuzzJokerShopTest {

    private static final int SEEDS = 40;

    @Test
    void shopJokerFuzzNoCrashNoNegativeState() {
        int reachedEnd = 0;
        int jokersBought = 0;
        for (int i = 0; i < SEEDS; i++) {
            String seed = "FUZZ" + String.format("%03d", i);
            RunState s = Engine.createRun("red", i % 8, seed, null); // 轮换赌注以覆盖黑红橙金注修饰
            int safety = 3000;
            int it = 0;
            while (s.phase != Phase.END && it++ < safety) {
                assertInvariants(s, seed);
                switch (s.phase) {
                    case BLIND_SELECT -> { Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false); }
                    case SHOP -> { jokersBought += buyAffordableJokers(s); Engine.nextRound(s); }
                    case PACK -> { cn.quotidietium.balatro.engine.shop.Packs.skip(s); }
                    case ROUND -> { playBest(s); }
                    default -> { break; }
                }
            }
            assertInvariants(s, seed);
            if (s.phase == Phase.END || s.won || s.lost) reachedEnd++;
        }
        assertTrue(reachedEnd >= SEEDS * 0.8, "至少 80% 种子应到达终局，实际 " + reachedEnd + "/" + SEEDS);
        assertTrue(jokersBought > 0, "应至少买到 1 张小丑（否则未覆盖买丑路径），实际 " + jokersBought);
    }

    /** 在商店购买所有可负担且槽位允许的小丑（非小丑商品不买，聚焦买丑路径）。 */
    private static int buyAffordableJokers(RunState s) {
        int bought = 0;
        var shop = s.shop;
        if (shop == null) return 0;
        // 倒序购买：买后列表不变（sold 标记），正向亦可；倒序避免任何潜在索引漂移
        for (int i = shop.cards.size() - 1; i >= 0; i--) {
            var c = shop.cards.get(i);
            if (c.sold || !"joker".equals(c.kind)) continue;
            if (s.jokerSpace() <= 0) break; // 槽满不再买
            if (!cn.quotidietium.balatro.engine.shop.Shop.canAfford(s, c.price)) continue;
            if (cn.quotidietium.balatro.engine.shop.Shop.buyCard(s, i)) bought++;
        }
        return bought;
    }

    private static void playBest(RunState s) {
        if (s.handsLeft <= 0 || s.hand.isEmpty()) return;
        List<Integer> ids = new ArrayList<>();
        int n = Math.min(5, s.hand.size());
        for (int i = 0; i < n; i++) ids.add(s.hand.get(i).id());
        Engine.PlayResult r = Engine.playHand(s, ids);
        if (!r.ok && s.handsLeft > 0 && !s.hand.isEmpty()) {
            for (int tryN = 1; tryN <= 5; tryN++) {
                if (tryN > s.hand.size()) break;
                List<Integer> t = new ArrayList<>();
                for (int i = 0; i < tryN; i++) t.add(s.hand.get(i).id());
                if (Engine.playHand(s, t).ok) break;
            }
        }
    }

    /** 状态不变量：分数非负；金钱不低于信用卡下限（-$20）。 */
    private static void assertInvariants(RunState s, String seed) {
        assertTrue(s.roundScore >= 0, "roundScore 不应为负（种子 " + seed + "）：" + s.roundScore);
        assertTrue(s.money >= -20, "money 不应低于信用卡下限 -$20（种子 " + seed + "）：" + s.money);
        // jokers 列表无 null 元素（防毒数据）
        for (int i = 0; i < s.jokers.size(); i++) {
            assertTrue(s.jokers.get(i) != null, "jokers[" + i + "] 不应为 null（种子 " + seed + "）");
        }
    }
}
