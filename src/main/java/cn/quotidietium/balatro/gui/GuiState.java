package cn.quotidietium.balatro.gui;

import cn.quotidietium.balatro.engine.Data;

/**
 * GUI 开局选择状态（纯逻辑，零 Bukkit 依赖，可单测）。
 *
 * <p>所有下标直接引用 {@link Data} 静态表，杜绝 key 拼写/大小写错误。
 * 任何时刻状态均处于「可开局」的合法态（默认 红牌组/白注/随机种子/普通模式）。
 */
public final class GuiState {

    /** 开局模式：标准局 / 挑战局。 */
    public enum Mode { NORMAL, CHALLENGE }

    private Mode mode = Mode.NORMAL;
    private int deckIdx = 0;
    private int stakeIdx = 0;
    private int challengeIdx = 0;
    private String seed; // null = 随机种子

    public Mode mode() {
        return mode;
    }

    public int deckIdx() {
        return deckIdx;
    }

    public int stakeIdx() {
        return stakeIdx;
    }

    public int challengeIdx() {
        return challengeIdx;
    }

    /** 当前种子；null 表示随机。 */
    public String seed() {
        return seed;
    }

    public Data.Deck deck() {
        return Data.DECKS.get(deckIdx);
    }

    public Data.Stake stake() {
        return Data.STAKES.get(stakeIdx);
    }

    public Data.Challenge challenge() {
        return Data.CHALLENGES.get(challengeIdx);
    }

    /** 设置模式；null 视为普通模式。 */
    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.NORMAL : mode;
    }

    /** 选择牌组；下标越界返回 false（状态不变）。 */
    public boolean setDeckIdx(int idx) {
        if (idx < 0 || idx >= Data.DECKS.size()) return false;
        deckIdx = idx;
        return true;
    }

    /** 选择赌注；下标越界返回 false（状态不变）。 */
    public boolean setStakeIdx(int idx) {
        if (idx < 0 || idx >= Data.STAKES.size()) return false;
        stakeIdx = idx;
        return true;
    }

    /** 选择挑战；下标越界返回 false（状态不变）。 */
    public boolean setChallengeIdx(int idx) {
        if (idx < 0 || idx >= Data.CHALLENGES.size()) return false;
        challengeIdx = idx;
        return true;
    }

    /** 设置种子（调用方须先用 {@code Rng.isValidSeed} 校验）。 */
    public void setSeed(String seed) {
        this.seed = seed;
    }

    /** 恢复随机种子。 */
    public void clearSeed() {
        this.seed = null;
    }

    /**
     * 组装开局参数。普通模式下不带挑战（即使曾选过挑战也排除）。
     * 挑战模式下使用当前选中的挑战（默认第 1 个，保证可开局）。
     */
    public StartRequest toStartRequest() {
        return new StartRequest(deck().key(), stakeIdx, seed,
                mode == Mode.CHALLENGE ? challenge().key() : null);
    }

    /** 开局参数（deckKey / stakeIdx / seed(null=随机) / challengeKey(null=普通局)）。 */
    public record StartRequest(String deckKey, int stakeIdx, String seed, String challengeKey) {
    }
}
