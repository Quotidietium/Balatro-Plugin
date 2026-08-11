package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Rng 算子边界契约回归（轮次 R69）。
 *
 * <p>{@code RngGoldenTest} 锁定与原版逐值一致的「正常值」路径；本测试补其未固化的
 * 边界契约——这些边界被商店权重抽选、概率小丑、消耗品等依赖，但 golden 文件只测了
 * 特定权重 {@code [1,2,3,0]} 与 {@code chance(0.25)} 的单点。边界行为一旦漂移
 * （例如 weighted 空池返回值、chance 钳制方向），会引发静默逻辑错误而非崩溃，故在此锁定。
 */
class RngBoundaryTest {

    // ---- weighted 边界 ----

    @Test
    void weightedEmptyListReturnsNull() {
        Rng.Stream s = Rng.makeStream("B", "w");
        assertNull(s.weighted(Collections.emptyList(), x -> 1), "空列表必须返回 null");
        assertNull(s.weighted(null, x -> 1), "null 列表必须返回 null");
    }

    @Test
    void weightedAllNonPositiveWeightsReturnsNull() {
        Rng.Stream s = Rng.makeStream("B", "w");
        List<Integer> items = List.of(10, 20, 30);
        assertNull(s.weighted(items, x -> 0), "全零权重必须返回 null（total<=0）");
        assertNull(s.weighted(items, x -> -5), "全负权重必须返回 null");
    }

    @Test
    void weightedSinglePositiveAmongZerosAlwaysPicksIt() {
        // 多个种子下，唯一正权重元素必须恒被选中（r 落在它身上）
        for (String seed : new String[] {"A", "B", "C", "SEED1", "SEED2"}) {
            Rng.Stream s = Rng.makeStream(seed, "w");
            List<String> items = List.of("zeroA", "PICK", "zeroB");
            String got = s.weighted(items, x -> x.equals("PICK") ? 5 : 0);
            assertEquals("PICK", got, "唯一正权重必须恒被选中 seed=" + seed);
        }
    }

    @Test
    void weightedNegativeWeightsTreatedAsZero() {
        // 负权重按 0 处理（Math.max(0,...)）：只有正权重元素可被选中
        Rng.Stream s = Rng.makeStream("NEG", "w");
        List<String> items = List.of("neg", "pos");
        for (int i = 0; i < 50; i++) {
            String got = s.weighted(items, x -> x.equals("pos") ? 3 : -100);
            assertEquals("pos", got, "负权重应视为 0，永不选中 iteration=" + i);
        }
    }

    // ---- chance 边界（钳制语义）----

    @Test
    void chanceZeroOrNegativeAlwaysFalse() {
        Rng.Stream s = Rng.makeStream("C", "p");
        for (int i = 0; i < 100; i++) {
            assertFalse(s.chance(0.0), "chance(0) 必须恒 false");
            assertFalse(s.chance(-0.5), "chance(负数) 必须恒 false（next()>=0）");
        }
    }

    @Test
    void chanceOneOrMoreAlwaysTrue() {
        Rng.Stream s = Rng.makeStream("C", "p");
        for (int i = 0; i < 100; i++) {
            assertTrue(s.chance(1.0), "chance(1) 必须恒 true（next()<1）");
            assertTrue(s.chance(2.0), "chance(>1) 必须恒 true");
        }
    }

    // ---- range 边界 ----

    @Test
    void rangeMinEqualsMaxReturnsThatValue() {
        Rng.Stream s = Rng.makeStream("R", "g");
        for (int i = 0; i < 50; i++) {
            assertEquals(7, s.range(7, 7), "range(x,x) 必须恒返回 x");
        }
    }

    @Test
    void rangeAlwaysWithinInclusiveBounds() {
        Rng.Stream s = Rng.makeStream("R", "g");
        for (int i = 0; i < 1000; i++) {
            int v = s.range(2, 14);
            assertTrue(v >= 2 && v <= 14, "range 越界: " + v);
        }
    }

    // ---- pick 边界 ----

    @Test
    void pickEmptyOrNullReturnsNull() {
        Rng.Stream s = Rng.makeStream("P", "k");
        assertNull(s.pick(Collections.emptyList()));
        assertNull(s.pick(null));
    }

    @Test
    void pickSingleElementAlwaysReturnsIt() {
        Rng.Stream s = Rng.makeStream("P", "k");
        List<String> one = List.of("only");
        for (int i = 0; i < 20; i++) {
            assertSame("only", s.pick(one));
        }
    }

    // ---- 命名流独立性（不可串扰——种子复现基石）----

    @Test
    void distinctStreamNamesAreIndependent() {
        // 同种子不同流名 → 不同序列（否则跨流复现失败）
        Rng.Stream a = Rng.makeStream("SEED", "shop");
        Rng.Stream b = Rng.makeStream("SEED", "joker");
        boolean differ = false;
        for (int i = 0; i < 10; i++) {
            if (Double.compare(a.next(), b.next()) != 0) { differ = true; break; }
        }
        assertTrue(differ, "不同命名流必须产生不同序列");
    }

    @Test
    void streamDoesNotAffectSibling() {
        // 推进流 A 不应改变流 B 的确定性重放
        Rng.Stream a1 = Rng.makeStream("SEED", "a");
        Rng.Stream b1 = Rng.makeStream("SEED", "b");
        a1.next(); a1.next(); a1.next(); // 推进 A
        Rng.Stream b2 = Rng.makeStream("SEED", "b");
        for (int i = 0; i < 20; i++) {
            assertEquals(b2.next(), b1.next(), 0.0, "推进兄弟流不得影响本流重放");
        }
    }

    // ---- shuffle 不变量 ----

    @Test
    void shuffleIsPermutationOfInput() {
        Rng.Stream s = Rng.makeStream("S", "h");
        List<Integer> arr = new ArrayList<>();
        for (int i = 0; i < 52; i++) arr.add(i);
        List<Integer> before = new ArrayList<>(arr);
        List<Integer> ret = s.shuffle(arr);
        assertSame(arr, ret, "shuffle 返回同一引用（原地）");
        List<Integer> sorted = new ArrayList<>(arr);
        Collections.sort(sorted);
        assertEquals(before, sorted, "shuffle 后必须是原列表的一个排列（无增删）");
    }
}
