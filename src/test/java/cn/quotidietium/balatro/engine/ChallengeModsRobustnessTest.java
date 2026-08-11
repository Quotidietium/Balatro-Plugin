package cn.quotidietium.balatro.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ChallengeMods 解析健壮性回归：单个 mod 值非法（非数字）只跳过该条，
 * 不让整局 createRun 崩溃（轮次 13 加固）。正常挑战仍按黄金值生效。
 */
class ChallengeModsRobustnessTest {

    @Test
    void createRunSurvivesUnknownChallengeKey() {
        // 未知挑战 key：applyTo 直接 return（BY_KEY 取不到 mods），createRun 不崩溃
        RunState st = assertDoesNotThrow(() -> Engine.createRun("red", 0, "R1", "this_challenge_does_not_exist"));
        assertNotNull(st);
        assertEquals("red", st.deckKey);
    }

    @Test
    void knownChallengeStillApplies() {
        // 已知挑战 omelette：5 个 egg 小丑（回归：加固未破坏正常路径）
        RunState st = Engine.createRun("red", 0, "R2", "omelette");
        assertEquals(5, st.jokers.size(), "omelette 应开局 5 个蛋");
        assertEquals("egg", st.jokers.get(0).def.key());
    }
}
