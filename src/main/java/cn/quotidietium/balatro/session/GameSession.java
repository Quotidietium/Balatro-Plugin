package cn.quotidietium.balatro.session;

import cn.quotidietium.balatro.BalatroPlugin;
import cn.quotidietium.balatro.api.RunSummary;
import cn.quotidietium.balatro.api.event.BalatroAnteClearEvent;
import cn.quotidietium.balatro.api.event.BalatroBlindResultEvent;
import cn.quotidietium.balatro.api.event.BalatroHandScoreEvent;
import cn.quotidietium.balatro.api.event.BalatroRunEndEvent;
import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * 单个玩家的一局会话：包装 {@link RunState}，提供玩家可调用的动作（出牌/弃牌），
 * 并在关键节点（计分/盲注结算/底注通过/本局结束）触发自定义事件与调用扩展服务。
 *
 * <p>0.1.0：盲注选择自动推进（无手动选择/商店），胜出后直接进入下一盲注。
 */
public final class GameSession {

    private final BalatroPlugin plugin;
    private final Player player;
    private final RunState state;
    private boolean aborted;

    public GameSession(BalatroPlugin plugin, Player player, String deckKey, int stakeIdx, String seed) {
        this.plugin = plugin;
        this.player = player;
        this.state = Engine.createRun(deckKey, stakeIdx, seed);
    }

    public Player player() {
        return player;
    }

    public RunState state() {
        return state;
    }

    public boolean isAborted() {
        return aborted;
    }

    /** 开始本局：触发 RunStart 事件（可取消），未取消则进入第一个小盲注回合。 */
    public boolean start() {
        BalatroPlugin.RunStartDecision decision = plugin.fireRunStart(player.getUniqueId(), state.seed, state.deckKey, state.stakeIdx);
        if (decision.cancelled()) {
            aborted = true;
            return false;
        }
        autoAdvance();
        return true;
    }

    /** 出牌（cardIds 为手牌中的卡 id）。 */
    public Engine.PlayResult play(List<Integer> cardIds) {
        if (state.phase != Phase.ROUND) {
            return Engine.PlayResult.err("当前不在回合中");
        }
        Data.BlindType bt = state.blindType;
        int anteBefore = state.ante;
        long target = state.blindTarget;

        Engine.PlayResult r = Engine.playHand(state, cardIds);

        // 计分事件（用于实时展示）
        plugin.fireHandScore(player.getUniqueId(),
                r.type == null ? "-" : r.type.key,
                r.score, state.roundScore, target, state.handsLeft);

        if (r.ok && r.won) {
            plugin.fireBlindResult(player.getUniqueId(), anteBefore, bt.key, target, state.roundScore, true);
            plugin.services().reward().onBlindCleared(player.getUniqueId(), anteBefore, bt.key);
            if (bt == Data.BlindType.BOSS) {
                plugin.fireAnteClear(player.getUniqueId(), anteBefore);
                plugin.services().reward().onAnteCleared(player.getUniqueId(), anteBefore);
            }
            if (state.won) {
                finishRun(true, state.ante);
            } else {
                autoAdvance();
            }
        } else if (r.ok && r.lost) {
            plugin.fireBlindResult(player.getUniqueId(), anteBefore, bt.key, target, state.roundScore, false);
            finishRun(false, anteBefore);
        }
        return r;
    }

    /** 弃牌。 */
    public Engine.PlayResult discard(List<Integer> cardIds) {
        return Engine.discard(state, cardIds);
    }

    /** 继续无尽模式（通关后）。 */
    public boolean continueEndless() {
        if (Engine.continueEndless(state)) {
            autoAdvance();
            return true;
        }
        return false;
    }

    /** 盲注选择阶段自动推进到下一盲注。 */
    private void autoAdvance() {
        if (state.phase == Phase.BLIND_SELECT && !state.endlessPending) {
            Engine.selectBlind(state, Data.BlindType.byKey(state.nextBlind), false);
        }
    }

    private void finishRun(boolean won, int anteReached) {
        plugin.fireRunEnd(player.getUniqueId(), won, anteReached, state.seed, state.deckKey, state.stakeIdx);
        plugin.services().reward().onRunEnd(player.getUniqueId(), won, anteReached);
        plugin.services().stats().record(new RunSummary(
                player.getUniqueId(), won, anteReached, state.seed, state.deckKey, state.stakeIdx,
                System.currentTimeMillis()));
    }

    /** 调试用：手牌的可读简报。 */
    public String handDebug() {
        StringBuilder sb = new StringBuilder();
        sb.append("ante=").append(state.ante)
                .append(" blind=").append(state.blindType == null ? "-" : state.blindType.key)
                .append(" phase=").append(state.phase)
                .append(" score=").append(state.roundScore).append("/").append(state.blindTarget)
                .append(" hands=").append(state.handsLeft).append(" discards=").append(state.discardsLeft)
                .append(" $").append(state.money).append("\n手牌: ");
        List<String> cards = new ArrayList<>();
        for (int i = 0; i < state.hand.size(); i++) {
            Card c = state.hand.get(i);
            cards.add((i + 1) + ":" + cardLabel(c));
        }
        sb.append(String.join("  ", cards));
        return sb.toString();
    }

    private static String cardLabel(Card c) {
        if (c.isStone()) return "石头";
        return Data.Suit.byIndex(c.suit()).symbol + Data.rankName(c.rank());
    }
}
