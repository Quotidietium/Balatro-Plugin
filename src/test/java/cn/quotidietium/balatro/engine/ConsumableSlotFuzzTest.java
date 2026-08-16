package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * R157：消耗品槽位守恒 fuzz——守恒模板（R155 卡牌 / R156 金钱）第三应用。
 *
 * <p>逐操作断言：
 * <ul>
 *   <li>**占用不变量**：任意时刻 {@code consumables.size() ≤ consumableSlots + negative 计数}
 *       （negative 版本每张 +1 容量）；</li>
 *   <li>**add 精确性**：addConsumableKey 成功 ⇔ 有空位；失败时列表**逐字不变**；</li>
 *   <li>**精确移除**：使用（ok）恰移除所用的那条；出售恰移除指定序号；
 *       失败操作（非法目标/越界）列表逐字不变；</li>
 *   <li>**条目合法性**：每条 kind ∈ {tarot,planet,spectral} 且 key 命中对应静态表
 *       （无毒数据/无 null）。</li>
 * </ul>
 *
 * <p>动作池选**不新增消耗品**的效果（magician/strength/ouija/sigil/hanged/temperance/
 * hermit），使「精确移除」可断言；negative 扩容路径经注入 negative 版本消耗品验证。
 */
class ConsumableSlotFuzzTest {

    private static final String[][] POOL = {
            {"tarot", "magician"}, {"tarot", "strength"}, {"spectral", "ouija"},
            {"spectral", "sigil"}, {"tarot", "hanged"}, {"tarot", "temperance"},
            {"tarot", "hermit"}};

    private static int capacity(RunState s) {
        int neg = 0;
        for (Consumable c : s.consumables) if (c.edition == Data.Edition.NEGATIVE) neg++;
        return s.consumableSlots + neg;
    }

    private static void assertInvariants(RunState s, String where) {
        assertTrue(s.consumables.size() <= capacity(s),
                "占用超容量（" + where + "）：" + s.consumables.size() + " > " + capacity(s));
        for (Consumable c : s.consumables) {
            boolean valid = switch (c.kind) {
                case "tarot" -> Data.Tarot.byKey(c.key) != null;
                case "planet" -> Data.Planet.byKey(c.key) != null;
                case "spectral" -> Data.Spectral.byKey(c.key) != null;
                default -> false;
            };
            assertTrue(valid, "毒消耗品条目（" + where + "）：" + c.kind + ":" + c.key);
        }
    }

    private static List<String> snapshot(RunState s) {
        List<String> out = new ArrayList<>();
        for (Consumable c : s.consumables) out.add(c.kind + ":" + c.key + ":" + c.edition);
        return out;
    }

    @Test
    void slotOccupancyAndExactRemovalHoldUnderRandomOps() {
        Random rnd = new Random(20260823L); // R216 扩展
        for (int trial = 0; trial < 160; trial++) {
            RunState s = Engine.createRun("red", 0, "CSLOT2-" + trial, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            s.consumableSlots = 2; // 闭包：无券无挑战，槽位恒 2（negative 注入才扩容）
            for (int step = 0; step < 40; step++) {
                int act = rnd.nextInt(10);
                List<String> before = snapshot(s);
                if (act <= 2) {
                    // add：成功 ⇔ 有空位；失败时逐字不变
                    String[] pick = POOL[rnd.nextInt(POOL.length)];
                    boolean expectOk = s.consumables.size() < capacity(s);
                    boolean ok = s.addConsumableKey(pick[0], pick[1]);
                    assertEquals(expectOk, ok, "add 成功性应 ⇔ 有空位（trial=" + trial + " step=" + step + "）");
                    if (!ok) assertEquals(before, snapshot(s), "add 失败列表应逐字不变");
                    if (ok) assertEquals(before.size() + 1, s.consumables.size(), "add 成功恰增一条");
                } else if (act <= 4 && !s.consumables.isEmpty()) {
                    // 出售：恰移除指定序号
                    int idx = rnd.nextInt(s.consumables.size());
                    String victim = before.get(idx);
                    assertTrue(s.sellConsumable(idx), "出售应成功");
                    List<String> after = snapshot(s);
                    assertEquals(before.size() - 1, after.size(), "出售恰减一条");
                    assertFalse(after.contains(victim) && before.stream().filter(v -> v.equals(victim)).count() == 1
                            && after.stream().filter(v -> v.equals(victim)).count() != before.stream().filter(v -> v.equals(victim)).count() - 1,
                            "出售应恰移除该条目");
                } else if (act <= 8 && !s.consumables.isEmpty()) {
                    // 使用：ok 恰移除该条；失败（非法目标）逐字不变
                    int idx = rnd.nextInt(s.consumables.size());
                    String victim = before.get(idx);
                    boolean badTargets = rnd.nextInt(3) == 0;
                    List<Integer> targets = List.of(badTargets ? -999 : s.hand.get(0).id());
                    var r = cn.quotidietium.balatro.engine.consumable.Consumables.use(s, idx, targets);
                    List<String> after = snapshot(s);
                    if (r.ok) {
                        assertEquals(before.size() - 1, after.size(), "使用 ok 恰减一条（trial=" + trial + " step=" + step + "）");
                        assertEquals(before.stream().filter(v -> v.equals(victim)).count() - 1,
                                after.stream().filter(v -> v.equals(victim)).count(),
                                "使用应恰移除所用条目");
                    } else {
                        assertEquals(before, after, "使用失败列表应逐字不变");
                    }
                } else {
                    // negative 注入 → 容量 +1 立即可验证（下一次 add 的成功性由 capacity() 反映）
                    Consumable neg = new Consumable("tarot", "fool");
                    neg.edition = Data.Edition.NEGATIVE;
                    s.consumables.add(neg);
                }
                assertInvariants(s, "trial=" + trial + " step=" + step);
            }
        }
    }
}
