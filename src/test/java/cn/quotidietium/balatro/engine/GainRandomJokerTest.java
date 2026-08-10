package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import org.junit.jupiter.api.Test;

/**
 * gainRandomJoker 对齐原版 engine.js 的回归测试（此前移植错误：误用 jokergrant 流、
 * null 稀有度分段掷骰、满槽仍消耗流；已修正为 randomjoker 流 + 混合池均匀抽取 + 满槽先返回）。
 */
class GainRandomJokerTest {

    @Test
    void nullRarityIsDeterministicAndBelowLegendary() {
        RunState a = Engine.createRun("red", 0, "GRJNULL");
        RunState b = Engine.createRun("red", 0, "GRJNULL");

        assertTrue(a.gainRandomJoker(null));
        assertTrue(b.gainRandomJoker(null));
        // 同种子同调用序列 ⇒ 同结果（种子复现基石）
        assertEquals(a.jokers.get(0).def.key(), b.jokers.get(0).def.key());
        int r = JokerRegistry.rarityOf(a.jokers.get(0).def.key());
        assertTrue(r >= 0 && r < 3, "null 稀有度不应出传奇");
    }

    @Test
    void fullSlotsRejectWithoutGrant() {
        RunState s = Engine.createRun("red", 0, "GRJFULL");
        for (int i = 0; i < 5; i++) {
            s.jokers.add(JokerRegistry.create("joker"));
        }
        assertEquals(0, s.jokerSpace());
        assertFalse(s.gainRandomJoker(null), "满槽应拒绝");
        assertFalse(s.gainRandomJoker(0), "满槽应拒绝（指定稀有度）");
        assertEquals(5, s.jokers.size(), "不应新增小丑");
    }

    @Test
    void explicitRarityPicksFromThatPool() {
        RunState s = Engine.createRun("red", 0, "GRJLEG");
        assertTrue(s.gainRandomJoker(3), "传奇池非空应可获得");
        assertEquals(3, JokerRegistry.rarityOf(s.jokers.get(0).def.key()), "应为传奇小丑");
    }

    @Test
    void gainJokerRecomputesFlags() {
        // 对齐原版 gainJoker：加入小丑后立即重算 flags（影响后续判定，如 fourFingers）
        RunState s = Engine.createRun("red", 0, "GRJFLAG");
        assertFalse(Boolean.TRUE.equals(s.flags.get("fourFingers")));
        s.gainJoker("fourfingers", null);
        assertTrue(Boolean.TRUE.equals(s.flags.get("fourFingers")), "加入后 flags 应立即生效");
    }
}
