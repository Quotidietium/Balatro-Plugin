package cn.quotidietium.balatro.engine.shop;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Joker;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 小丑包（BUFFOON）在小丑池耗尽时的回退验证（轮次 55）。
 *
 * <p>makeJokerItem 池空时回退为塔罗商品（joker 字段为 null）。此前 BUFFOON 分支
 * 无条件写 kind="joker"，产生 kind=joker + joker=null 的毒数据；pick 时会把 null
 * 塞进 jokers 列表，后续 computeFlags 遍历 NPE（会话软锁）。
 * 修复后：回退商品按其真实 kind（tarot）入包，pick 走消耗品分支。
 */
class BuffoonEmptyPoolTest {

    private static Data.Pack buffoonPack() {
        for (Data.Pack p : Data.PACKS) {
            if (p.type == Data.PackType.BUFFOON) return p;
        }
        throw new IllegalStateException("无 buffoon 包定义");
    }

    /** 让本局拥有全部小丑（绕过槽位直接塞列表，模拟负片小丑不限槽的极端局面）。 */
    private static void ownAllJokers(RunState s) {
        for (Joker j : JokerRegistry.allJokersOrdered()) {
            s.jokers.add(new JokerInstance(j));
        }
    }

    @Test
    void buffoonPackNeverYieldsNullJokerCard() {
        for (int seed = 0; seed < 50; seed++) {
            RunState s = Engine.createRun("red", 0, "bp" + seed);
            ownAllJokers(s);
            Packs.open(s, buffoonPack());
            assertNotNull(s.pack);
            for (Packs.PackCard c : s.pack.cards) {
                if ("joker".equals(c.kind)) {
                    assertNotNull(c.joker, "kind=joker 的卡必须携带非空 joker 实例");
                } else {
                    // 池空回退：塔罗卡必须有 key
                    assertNotNull(c.key, "回退商品必须有 key（kind=" + c.kind + "）");
                }
            }
        }
    }

    @Test
    void pickFallbackCardDoesNotCorruptJokers() {
        RunState s = Engine.createRun("red", 0, "bpf1");
        ownAllJokers(s);
        int jokersBefore = s.jokers.size();
        Packs.open(s, buffoonPack());
        // 逐张尝试选取：任何路径都不得向 jokers 塞 null、不得抛异常
        for (int i = 0; s.pack != null && i < s.pack.cards.size(); i++) {
            Packs.pick(s, i);
        }
        for (JokerInstance j : s.jokers) {
            assertNotNull(j, "jokers 列表不得含 null");
            assertNotNull(j.def, "jokers 列表不得含 def 为 null 的实例");
        }
        // 拥有全部小丑后回退卡是塔罗：消耗品槽满时 pick 返回 false，jokers 数不变
        assertTrue(s.jokers.size() >= jokersBefore);
    }

    @Test
    void normalBuffoonPackStillYieldsJokers() {
        // 不拥有任何小丑时，小丑包照常产出真小丑（回归：修复不影响正常路径）
        RunState s = Engine.createRun("red", 0, "bpn1");
        Packs.open(s, buffoonPack());
        assertNotNull(s.pack);
        boolean anyJoker = false;
        for (Packs.PackCard c : s.pack.cards) {
            if ("joker".equals(c.kind)) {
                anyJoker = true;
                assertNotNull(c.joker);
            }
        }
        assertTrue(anyJoker, "正常局面小丑包应产出小丑");
    }

    @Test
    void pickRejectsForgedIndices() {
        RunState s = Engine.createRun("red", 0, "bpf2");
        Packs.open(s, buffoonPack());
        assertFalse(Packs.pick(s, -1));
        assertFalse(Packs.pick(s, s.pack.cards.size()));
        assertFalse(Packs.pick(s, Integer.MAX_VALUE));
    }
}
