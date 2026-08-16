package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * R187：挑战 × 新种子交叉探索——FullMatrix 用固定种子矩阵，黄金挑战用例锁特定种子；
 * 本轮以**全新种子族**（CHFRESH-*）跑全部 20 挑战 × 各 2 种子的全循环
 *（盲注→商店→推进，最多 6 盲注），断言不崩 + 卡守恒 + 金钱下界。
 */
class ChallengeFreshSeedTest {

    private static boolean playFirst(RunState s) {
        if (s.handsLeft <= 0 || s.hand.isEmpty()) return false;
        int n = Math.min(5, s.hand.size());
        List<Integer> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(s.hand.get(i).id());
        return Engine.playHand(s, ids).ok;
    }

    private static void assertConservedAtRoundEntry(RunState s, String key, int k) {
        Map<Integer, Integer> piles = new HashMap<>();
        for (Card c : s.hand) piles.merge(c.id(), 1, Integer::sum);
        for (Card c : s.drawPile) piles.merge(c.id(), 1, Integer::sum);
        for (Card c : s.discardPile) piles.merge(c.id(), 1, Integer::sum);
        Map<Integer, Integer> deck = new HashMap<>();
        for (Card c : s.fullDeck) deck.merge(c.id(), 1, Integer::sum);
        // 大理石小丑的「已入组未发」修正：startRound 先洗牌后 onBlindStart 加牌——
        // 带大理石的回合恒有 marbleCount 张 pending（下回合才洗入，R158 精化不变量的
        // 第二实例）。差集恰等于大理石数即守恒成立。
        int marbles = 0;
        for (var j : s.jokers) if (j.def.key().equals("marble") && !j.debuff) marbles++;
        int diff = 0;
        for (var e : deck.entrySet()) diff += e.getValue() - piles.getOrDefault(e.getKey(), 0);
        for (var e : piles.entrySet()) diff += e.getValue() - deck.getOrDefault(e.getKey(), 0);
        assertTrue(diff == marbles,
                "卡守恒@回合入口（" + key + " seed#" + k + "）deck=" + deck.size()
                        + " piles=" + piles.size() + " diff=" + diff + " marbles=" + marbles);
        assertTrue(deck.keySet().containsAll(piles.keySet()),
                "三堆不得出现牌组没有的 id（" + key + "）");
    }

    @Test
    void allChallengesSurviveFreshSeeds() {
        for (Data.Challenge ch : Data.CHALLENGES) {
            for (int k = 0; k < 2; k++) {
                RunState s = Engine.createRun("red", k, "CHFRESH-" + ch.key() + "-" + k, ch.key());
                for (int blind = 0; blind < 6; blind++) {
                    if (s.phase == Phase.END) break;
                    if (s.phase == Phase.BLIND_SELECT) {
                        Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                    }
                    if (s.phase != Phase.ROUND) break;
                    // 守恒在洗牌后的回合入口断言（严格式）：medusa 的大理石小丑于
                    // onBlindStart 向 fullDeck 加石头牌，下回合洗牌才入三堆——
                    // BLIND_SELECT 时刻的 deck=piles+pending 属合法中间态（R158 同类）
                    assertConservedAtRoundEntry(s, ch.key(), k);
                    s.roundScore = s.blindTarget; // 脚本速胜（极限制牌 Boss 自然失败即止）
                    int guard = 0;
                    while (s.phase == Phase.ROUND && guard++ < 25) {
                        if (playFirst(s)) break;
                        if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                            Engine.discard(s, List.of(s.hand.get(0).id()));
                        } else break;
                    }
                    if (s.phase == Phase.SHOP) {
                        Engine.nextRound(s);
                    }
                }
                // 下界（无信用卡挑战 0；golden 挑战自带 creditcard 允许 -20）
                long floor = "golden".equals(ch.key()) ? -20 : 0;
                assertTrue(s.money >= floor,
                        "金钱下界（" + ch.key() + " seed#" + k + "）：" + s.money + " < " + floor);
            }
        }
    }
}
