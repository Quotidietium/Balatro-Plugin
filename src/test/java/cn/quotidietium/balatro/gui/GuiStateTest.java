package cn.quotidietium.balatro.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import org.junit.jupiter.api.Test;

/**
 * GUI 开局选择状态（{@link GuiState}）的纯逻辑测试：
 * 默认值、合法/越界选择、模式与挑战的包含关系、种子设置、开局参数组装。
 */
class GuiStateTest {

    @Test
    void defaultsAreStartable() {
        GuiState st = new GuiState();
        GuiState.StartRequest req = st.toStartRequest();
        assertEquals("red", req.deckKey(), "默认牌组应为 red");
        assertEquals(0, req.stakeIdx(), "默认赌注应为 0（白注）");
        assertNull(req.seed(), "默认应为随机种子");
        assertNull(req.challengeKey(), "默认应为普通模式（无挑战）");
    }

    @Test
    void selectDeckWithinBounds() {
        GuiState st = new GuiState();
        for (int i = 0; i < Data.DECKS.size(); i++) {
            assertTrue(st.setDeckIdx(i), "下标 " + i + " 应合法");
            assertEquals(Data.DECKS.get(i).key(), st.toStartRequest().deckKey());
        }
        assertFalse(st.setDeckIdx(-1));
        assertFalse(st.setDeckIdx(Data.DECKS.size()));
        assertEquals(Data.DECKS.get(Data.DECKS.size() - 1).key(), st.toStartRequest().deckKey(),
                "越界选择不应改变已选牌组");
    }

    @Test
    void selectStakeWithinBounds() {
        GuiState st = new GuiState();
        assertTrue(st.setStakeIdx(7));
        assertEquals(7, st.toStartRequest().stakeIdx());
        assertFalse(st.setStakeIdx(8));
        assertFalse(st.setStakeIdx(-1));
        assertEquals(7, st.toStartRequest().stakeIdx(), "越界选择不应改变已选赌注");
    }

    @Test
    void challengeModeIncludesChallenge() {
        GuiState st = new GuiState();
        st.setMode(GuiState.Mode.CHALLENGE);
        // 未显式选择时默认第 1 个挑战，保证任何时刻均可开局
        assertEquals(Data.CHALLENGES.get(0).key(), st.toStartRequest().challengeKey());
        int idx = Data.CHALLENGES.size() - 1;
        assertTrue(st.setChallengeIdx(idx));
        assertEquals(Data.CHALLENGES.get(idx).key(), st.toStartRequest().challengeKey());
        assertFalse(st.setChallengeIdx(Data.CHALLENGES.size()));
        assertFalse(st.setChallengeIdx(-5));
        assertEquals(Data.CHALLENGES.get(idx).key(), st.toStartRequest().challengeKey(),
                "越界选择不应改变已选挑战");
    }

    @Test
    void normalModeExcludesChallenge() {
        GuiState st = new GuiState();
        st.setMode(GuiState.Mode.CHALLENGE);
        assertTrue(st.setChallengeIdx(3));
        st.setMode(GuiState.Mode.NORMAL);
        assertNull(st.toStartRequest().challengeKey(), "普通模式应排除挑战");
        // 切回挑战模式后保留此前的选择（用户体验：不丢已选项）
        st.setMode(GuiState.Mode.CHALLENGE);
        assertEquals(Data.CHALLENGES.get(3).key(), st.toStartRequest().challengeKey());
    }

    @Test
    void nullModeFallsBackToNormal() {
        GuiState st = new GuiState();
        st.setMode(null);
        assertEquals(GuiState.Mode.NORMAL, st.mode());
        assertNull(st.toStartRequest().challengeKey());
    }

    @Test
    void seedSetAndClear() {
        GuiState st = new GuiState();
        st.setSeed("ABC-123_x");
        assertEquals("ABC-123_x", st.toStartRequest().seed());
        st.clearSeed();
        assertNull(st.toStartRequest().seed());
    }

    @Test
    void fullCustomCombination() {
        GuiState st = new GuiState();
        st.setMode(GuiState.Mode.CHALLENGE);
        assertTrue(st.setDeckIdx(14));   // erratic
        assertTrue(st.setStakeIdx(6));   // 橙注
        assertTrue(st.setChallengeIdx(19)); // jokerless
        st.setSeed("seed42");
        GuiState.StartRequest req = st.toStartRequest();
        assertEquals(Data.DECKS.get(14).key(), req.deckKey());
        assertEquals(6, req.stakeIdx());
        assertEquals("seed42", req.seed());
        assertEquals(Data.CHALLENGES.get(19).key(), req.challengeKey());
    }
}
