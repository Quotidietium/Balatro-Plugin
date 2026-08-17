package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R152：endRound 经济面混沌 fuzz——20 挑战 × 多种子 × 完整「赢得回合」路径的收入不变量。
 *
 * <p>既有经济测试为点测（RentalEconomy/R105 价格公式/R127 经济标签），无跨全部挑战
 * 的混沌覆盖。本 fuzz 走引擎完整胜利路径（预置 roundScore → 合法出牌 → endRound 全链
 * 含奖励/剩余出牌/利息/蜡封/小丑钩子/租赁/易腐），每步断言：
 * <ul>
 *   <li>金钱不为负——除非局内有「信用卡」（credit flag 允许欠款，inflation/golden 挑战
 *       自带 creditcard 小丑）且下界不低于 -credit；</li>
 *   <li>连胜多回合后仍稳定（不抛异常、状态可推进）。</li>
 * </ul>
 */
class EndRoundEconomyFuzzTest {

    private static boolean playAnyValid(RunState s) {
        if (s.handsLeft <= 0 || s.hand.isEmpty()) return false;
        int sz = s.hand.size();
        if (sz >= 5) {
            for (int st = 0; st + 5 <= sz; st++) {
                List<Integer> ids = new ArrayList<>();
                for (int i = st; i < st + 5; i++) ids.add(s.hand.get(i).id());
                if (Engine.playHand(s, ids).ok) return true;
            }
        }
        for (int n = 1; n <= Math.min(5, sz); n++) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) ids.add(s.hand.get(i).id());
            if (Engine.playHand(s, ids).ok) return true;
        }
        return false;
    }

    /** 完整赢得当前回合（含 endRound 全部收入结算），返回是否推进成功。 */
    private static boolean winRound(RunState s) {
        if (s.phase == Phase.BLIND_SELECT) {
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
        }
        if (s.phase != Phase.ROUND) return false;
        s.roundScore = s.blindTarget;
        int guard = 0;
        while (s.phase == Phase.ROUND && guard++ < 20) {
            if (playAnyValid(s)) continue;
            if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                List<Integer> disc = new ArrayList<>();
                for (int i = 0; i < Math.min(5, s.hand.size()); i++) disc.add(s.hand.get(i).id());
                if (Engine.discard(s, disc).ok) continue;
            }
            return false;
        }
        return s.phase != Phase.ROUND;
    }

    /** 允许的金钱下界：无信用卡为 0；有信用卡（credit flag 或持有 creditcard 小丑）为 -credit。 */
    private static long moneyFloor(RunState s) {
        Object credit = s.flags != null ? s.flags.get("credit") : null;
        long c = credit instanceof Number ? ((Number) credit).longValue() : 0;
        boolean holdsCard = s.jokers.stream().anyMatch(j -> j.def.key().equals("creditcard") && !j.debuff);
        return -(Math.max(c, holdsCard ? 20 : 0));
    }

    @Test
    void allChallengesKeepMoneyAboveFloorAcrossWonRounds() {
        for (Data.Challenge ch : Data.CHALLENGES) {
            for (int seed = 0; seed < 3; seed++) {
                RunState s = Engine.createRun("red", 0, "ECO2-" + ch.key() + "-" + seed, ch.key());
                for (int round = 0; round < 4; round++) {
                    if (!winRound(s)) break; // 极端 Boss 限制下无法推进即停（非本测目标）
                    assertTrue(s.money >= moneyFloor(s),
                            ch.key() + " 经济下界被击穿 seed=" + seed + " round=" + round
                                    + " money=" + s.money + " floor=" + moneyFloor(s));
                    if (s.phase == Phase.SHOP && !Engine.nextRound(s)) break;
                }
            }
        }
    }

    /** R219：挑战 × 赌注交叉经济下界——每挑战 × 赌注 {3, 7}（黑注贴纸/金注租赁经济最高压）
     *  各 1 新种子，赢 2 回合断言下界。 */
    @Test
    void challengeTimesStakeCrossKeepsMoneyAboveFloor() {
        for (Data.Challenge ch : Data.CHALLENGES) {
            for (int stake : new int[] {3, 7}) {
                RunState s = Engine.createRun("red", stake, "ECX-" + ch.key() + "-" + stake, ch.key());
                for (int round = 0; round < 2; round++) {
                    if (!winRound(s)) break;
                    assertTrue(s.money >= moneyFloor(s),
                            ch.key() + "@" + stake + " 交叉下界被击穿 round=" + round
                                    + " money=" + s.money + " floor=" + moneyFloor(s));
                    if (s.phase == Phase.SHOP && !Engine.nextRound(s)) break;
                }
            }
        }
    }

    @Test
    void standardStakesZeroToSevenAllKeepMoneyNonNegative() {
        for (int stake = 0; stake <= 7; stake++) {
            RunState s = Engine.createRun("red", stake, "ECOSTK2-" + stake, null);
            for (int round = 0; round < 3; round++) {
                if (!winRound(s)) break;
                assertTrue(s.money >= 0,
                        "赌注 " + stake + " 标准局金钱不应为负 round=" + round + " money=" + s.money);
                if (s.phase == Phase.SHOP && !Engine.nextRound(s)) break;
            }
        }
    }
}
