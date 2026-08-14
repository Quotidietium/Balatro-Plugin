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

    /**
     * 出牌后钩子（带触发实例）。多副本累积器（runner/icecream/seltzer 等）必须经 {@code self}
     * 修改各自 extra——经 {@code info.findJoker(key)} 只取第一份会让副本1 被重复衰减、
     * 副本2+ 永不衰减（REF-inherited 缺陷，R111 修正；真版每副本独立计数）。
     * 默认委托旧两参形式（第三方 2 参实现无须改动）。
     */
    default void onPlayHand(RunState state, PlayHandInfo info, JokerInstance self) {
        onPlayHand(state, info);
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

    // ---- 商店/补充包/消耗品钩子（0.2.0 起调用；0.1.0 默认空） ----
    default void onUseTarot(RunState state, JokerInstance self) {
    }

    default void onUsePlanet(RunState state, JokerInstance self) {
    }

    default void onReroll(RunState state, JokerInstance self) {
    }

    default void onSell(RunState state, JokerInstance self) {
    }

    default void onAnySell(RunState state, JokerInstance self) {
    }

    default void onCardAdded(RunState state, Card card, JokerInstance self) {
    }

    default void onPackOpen(RunState state, JokerInstance self) {
    }

    default void onPackSkip(RunState state, JokerInstance self) {
    }

    /** 有人头牌被销毁时（幻灵牌等，0.2.0 起调用）。 */
    default void onFaceDestroyed(RunState state, JokerInstance self) {
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
