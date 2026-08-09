package cn.quotidietium.balatro.engine;

import java.util.List;
import java.util.Map;

/**
 * 小丑定义（钩子模型），对应 balatro {@code defJoker({...})} 注册的对象。
 * 所有钩子默认空实现；具体小丑（0.1.0-S10 起）按需覆写。
 *
 * <p>移植红线：钩子内的随机必须经 {@link ScoreContext#rngInt} / {@link RunState#stream}，
 * 且调用顺序须与原版一致，保证种子复现。
 */
public interface Joker {

    String key();

    /** 显示名（注意：不能用 name()，与 Enum.name() 冲突）。 */
    String displayName();

    /** 简介文本。 */
    default String desc() {
        return "";
    }

    // ---- 计分钩子 ----
    default void onScore(ScoreContext ctx) {
    }

    default void onScoreCard(ScoreContext ctx, Card card) {
    }

    default void onHeld(ScoreContext ctx, Card card) {
    }

    /** 返回额外重触发次数。 */
    default int retrigger(Card card, ScoreContext ctx) {
        return 0;
    }

    // ---- 流程钩子 ----
    default void onPlayHand(RunState state, PlayHandInfo info) {
    }

    default void onDiscard(RunState state, List<Card> cards, JokerInstance self) {
    }

    /** 回合结束：返回获得的金钱。 */
    default long onRoundEnd(RunState state, JokerInstance self) {
        return 0;
    }

    default void onSkip(RunState state, JokerInstance self) {
    }

    default void onLucky(RunState state, JokerInstance self) {
    }

    default void onGlassBreak(RunState state, JokerInstance self) {
    }

    default void onBlindSelect(RunState state, JokerInstance self, Data.BlindType blindType) {
    }

    default void onBlindStart(RunState state, JokerInstance self) {
    }

    default void onRoundStart(RunState state, JokerInstance self) {
    }

    default void onBossDefeated(RunState state, JokerInstance self) {
    }

    // ---- flags（影响牌型判定/手牌上限等） ----
    default Map<String, Object> flags() {
        return null;
    }

    default Map<String, Object> flagsFn(RunState state, JokerInstance self) {
        return null;
    }

    // ---- 复制类（蓝图/头脑风暴，0.4.0） ----
    default boolean blueprint() {
        return false;
    }

    default boolean brainstorm() {
        return false;
    }

    // ---- 商店元数据（0.2.0 起用） ----
    default int cost() {
        return 0;
    }
}
