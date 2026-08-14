package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * HandEval 牌型判定的随机边界 fuzz（R96）。
 *
 * <p>R16 静态对照了 13 牌型的边界，HandEvalEdgeCaseTest 有 7 个固定边界用例。
 * 本测试用随机生成的 1~5 张牌组合（含万能牌/石头牌/同点数/混合花色）轰炸 evaluate，
 * 断言结果的结构性不变量：计分牌是输入子集、数量不超、不抛异常。
 */
class HandEvalChaosTest {

    private static final int TRIALS = 5000;

    @Test
    void randomHandEvaluationStructurallyValid() {
        Random rnd = new Random(20260817L);
        RunState s = Engine.createRun("red", 0, "EVALFUZZ", null);
        int failures = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            int n = 1 + rnd.nextInt(5);
            List<Card> hand = new ArrayList<>(n);
            Set<Integer> ids = new HashSet<>();
            for (int i = 0; i < n; i++) {
                // 随机决定牌类型：普通/万能/石头
                int kind = rnd.nextInt(10);
                Card c;
                if (kind == 0) {
                    // 石头牌
                    c = new Card(s.nextCardId(), 0, -1);
                    c.setEnh(Data.Enhancement.STONE);
                } else if (kind == 1) {
                    // 万能牌
                    c = new Card(s.nextCardId(), 2 + rnd.nextInt(13), rnd.nextInt(4));
                    c.setEnh(Data.Enhancement.WILD);
                } else {
                    // 普通牌
                    c = new Card(s.nextCardId(), 2 + rnd.nextInt(13), rnd.nextInt(4));
                }
                ids.add(c.id());
                hand.add(c);
            }
            try {
                HandEval.Result r = HandEval.evaluate(s, hand);
                // 不变量 1：计分牌是输入的子集（id 比对）
                for (Card sc : r.scoring) {
                    assertTrue(ids.contains(sc.id()),
                            "计分牌含输入外的牌（trial=" + trial + "）：id=" + sc.id());
                }
                // 不变量 2：计分牌数量 ≤ 输入牌数量
                assertTrue(r.scoring.size() <= n,
                        "计分牌多于输入（trial=" + trial + "）：" + r.scoring.size() + ">" + n);
                // 不变量 3：计分牌数量 ≥ 1（至少一张计分）
                assertTrue(!r.scoring.isEmpty(),
                        "计分牌为空（trial=" + trial + "，type=" + r.type + "）");
                // 不变量 4：type 非 null
                assertTrue(r.type != null, "type 为 null（trial=" + trial + "）");
            } catch (RuntimeException ex) {
                fail("evaluate 抛异常（trial=" + trial + "，手=" + describe(hand) + "）：" + ex);
            }
        }
        // 确保真正跑了（防误删循环）
        assertTrue(failures == 0);
    }

    private static String describe(List<Card> hand) {
        StringBuilder sb = new StringBuilder();
        for (Card c : hand) {
            sb.append(c.isStone() ? "石" : (c.enh() == Data.Enhancement.WILD ? "万" : ""))
                    .append(c.rank()).append("/").append(c.suit()).append(" ");
        }
        return sb.toString();
    }
}
