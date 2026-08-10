package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 跳过盲注流程测试：selectBlind(skip=true) 推进 nextBlind 并获标签；Boss 不可跳过。
 * 0.3.0 将此路径接入会话层（/balatro go · /balatro skip），单独锁定其引擎语义。
 */
class BlindSkipTest {

    @Test
    void skipSmallAdvancesToBigAndGainsTag() {
        RunState s = Engine.createRun("red", 0, "SKIP");
        assertEquals(Phase.BLIND_SELECT, s.phase);
        assertEquals("small", s.nextBlind);

        boolean ok = Engine.selectBlind(s, Data.BlindType.SMALL, true);
        assertTrue(ok);
        assertEquals("big", s.nextBlind, "跳过小盲注 → 下一盲注为大盲注");
        assertEquals(1, s.tags.size(), "应获得 1 个跳过标签");
        assertEquals(Phase.BLIND_SELECT, s.phase, "跳过后仍在盲注选择阶段");
    }

    @Test
    void skipBigAdvancesToBoss() {
        RunState s = Engine.createRun("red", 0, "SKIP2");
        s.nextBlind = "big";
        boolean ok = Engine.selectBlind(s, Data.BlindType.BIG, true);
        assertTrue(ok);
        assertEquals("boss", s.nextBlind);
        assertEquals(1, s.tags.size());
    }

    @Test
    void bossBlindCannotBeSkipped() {
        RunState s = Engine.createRun("red", 0, "SKIPB");
        s.nextBlind = "boss";
        boolean ok = Engine.selectBlind(s, Data.BlindType.BOSS, true);
        assertFalse(ok, "Boss 盲注不可跳过");
        assertTrue(s.tags.isEmpty(), "未跳过则无标签");
    }
}
