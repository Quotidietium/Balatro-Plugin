package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cn.quotidietium.balatro.engine.consumable.Consumables;
import cn.quotidietium.balatro.engine.shop.Packs;
import cn.quotidietium.balatro.engine.shop.Shop;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 引擎公开 API 对抗性模糊测试（轮次 R101）。
 *
 * <p>动机：所有公开入口都不信任调用方输入（客户端可篡改命令/点击包），契约是「非法输入返回
 * err/false，绝不抛异常、绝不破坏状态不变量」。逐路径人工核查已在历轮完成，本测试用确定性随机
 * （固定种子 {@code new Random(20260812L)}，可复现）在任意阶段下以垃圾输入轰炸全部公开变异入口，
 * 覆盖人工核查难以穷举的「阶段 × 输入」组合空间：
 * <ul>
 *   <li>null / 空表 / 超长表 / 重复 id / 越界 id / 负下标的出牌、弃牌、消耗品、买卖、补充包；</li>
 *   <li>错误阶段调用（ROUND 中买牌、SHOP 中出牌、END 后推进等）；</li>
 *   <li>跨 ante 长跑中的随机交错（含无尽模式）。</li>
 * </ul>
 *
 * <p>每一步后断言引擎级不变量：handsLeft/discardsLeft/ante/roundScore 非负、ROUND 中目标分恒正、
 * 金钱不环绕（宽松下界防 satAdd 失效）、手牌/小丑数量有界。任一不变量破坏或异常逃逸即失败。
 */
class EngineApiFuzzTest {

    private static final int TRIALS = 200;
    private static final int STEPS = 300;

    /** 混合垃圾与合法 id：null(10%)、0~8 个元素、半数取自真实手牌、半数越界/负数（可重复）。 */
    private static List<Integer> randomIds(Random rnd, RunState s) {
        if (rnd.nextInt(10) == 0) return null;
        int n = rnd.nextInt(9);
        List<Integer> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (!s.hand.isEmpty() && rnd.nextBoolean()) {
                ids.add(s.hand.get(rnd.nextInt(s.hand.size())).id());
            } else {
                ids.add(rnd.nextInt(120) - 10);
            }
        }
        return ids;
    }

    @Test
    void publicApisNeverThrowOnAdversarialInputs() {
        Random rnd = new Random(20260812L);
        for (int trial = 0; trial < TRIALS; trial++) {
            RunState s = Engine.createRun("red", rnd.nextInt(8), "FUZZ" + trial, null);
            for (int step = 0; step < STEPS; step++) {
                int action = rnd.nextInt(16);
                try {
                    switch (action) {
                        case 0 -> Engine.playHand(s, randomIds(rnd, s));
                        case 1 -> Engine.discard(s, randomIds(rnd, s));
                        case 2 -> Engine.selectBlind(s, rnd.nextBoolean()
                                ? Data.BlindType.byKey(s.nextBlind) // 半数概率用正确类型以推进状态机
                                : Data.BlindType.values()[rnd.nextInt(3)], rnd.nextBoolean());
                        case 3 -> Engine.nextRound(s);
                        case 4 -> Shop.buyCard(s, rnd.nextInt(20) - 5);
                        case 5 -> Shop.buyPack(s, rnd.nextInt(10) - 3);
                        case 6 -> Shop.buyVoucher(s, rnd.nextInt(5) - 2);
                        case 7 -> Shop.reroll(s);
                        case 8 -> Consumables.use(s, rnd.nextInt(10) - 5, randomIds(rnd, s));
                        case 9 -> Packs.pick(s, rnd.nextInt(12) - 4);
                        case 10 -> Packs.skip(s);
                        case 11 -> s.sellJoker(rnd.nextInt(12) - 4);
                        case 12 -> s.sellConsumable(rnd.nextInt(10) - 5);
                        case 13 -> Engine.continueEndless(s);
                        case 14 -> s.gainMoney(rnd.nextInt(100)); // 注资让购买路径真正执行
                        default -> {
                            // 预置达标分：下一次合法出牌即胜，驱动状态机进入 SHOP/PACK/无尽等深阶段
                            if (s.phase == Phase.ROUND) s.roundScore = s.blindTarget;
                        }
                    }
                } catch (RuntimeException ex) {
                    fail("公开 API 抛异常（trial=" + trial + " step=" + step + " action=" + action
                            + " phase=" + s.phase + "）：" + ex);
                }
                assertInvariants(s, trial, step);
                if (s.phase == Phase.END && !s.endlessPending) break; // 本局结束，下一试验
            }
        }
    }

    /** 每一步后必须成立的引擎级不变量（宽松但足以捕获环绕/泄漏/负计数类回归）。 */
    private static void assertInvariants(RunState s, int trial, int step) {
        String where = "trial=" + trial + " step=" + step + " phase=" + s.phase;
        assertTrue(s.handsLeft >= 0, "handsLeft 为负（" + where + "）：" + s.handsLeft);
        assertTrue(s.discardsLeft >= 0, "discardsLeft 为负（" + where + "）：" + s.discardsLeft);
        assertTrue(s.ante >= 1, "ante 异常（" + where + "）：" + s.ante);
        assertTrue(s.roundScore >= 0, "roundScore 为负（satAdd 环绕？）（" + where + "）：" + s.roundScore);
        if (s.phase == Phase.ROUND) {
            assertTrue(s.blindTarget > 0, "ROUND 中目标分非正（必败软锁风险）（" + where + "）：" + s.blindTarget);
        }
        // 金钱合法负值有界（租赁 -3/小丑/回合，数百步内不可能低于 -10 万；低于此值即环绕证据）
        assertTrue(s.money >= -100_000, "金钱环绕成巨大负值（" + where + "）：" + s.money);
        // 手牌/小丑数量宽松上界（手牌上限 8+修正，小丑 5+修正；数量级泄漏即可被捕）
        assertTrue(s.hand.size() <= 40, "手牌数量泄漏（" + where + "）：" + s.hand.size());
        assertTrue(s.jokers.size() <= 40, "小丑数量泄漏（" + where + "）：" + s.jokers.size());
    }
}
