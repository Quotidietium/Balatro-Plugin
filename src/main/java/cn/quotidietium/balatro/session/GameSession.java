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
import cn.quotidietium.balatro.render.RoundBoard;
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
    private RoundBoard board;
    private boolean aborted;

    public GameSession(BalatroPlugin plugin, Player player, String deckKey, int stakeIdx, String seed) {
        this(plugin, player, deckKey, stakeIdx, seed, null);
    }

    public GameSession(BalatroPlugin plugin, Player player, String deckKey, int stakeIdx, String seed, String challenge) {
        this.plugin = plugin;
        this.player = player;
        this.state = Engine.createRun(deckKey, stakeIdx, seed, challenge);
    }

    public BalatroPlugin plugin() {
        return plugin;
    }

    public Player player() {
        return player;
    }

    public RunState state() {
        return state;
    }

    public RoundBoard board() {
        return board;
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
        if (state.phase == Phase.ROUND) {
            board = new RoundBoard(this);
            board.spawn(state);
        }
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

        // 计分事件（用于实时展示）——仅在实际发生计分时发出；
        // 被拒绝的出牌（选牌非法/Boss 限制等）不产生计分，不应发事件。
        if (r.ok) {
            plugin.fireHandScore(player.getUniqueId(),
                    r.type == null ? "-" : r.type.key,
                    r.score, state.roundScore, target, state.handsLeft);
        }

        if (r.ok && r.won) {
            plugin.fireBlindResult(player.getUniqueId(), anteBefore, bt.key, target, state.roundScore, true);
            plugin.services().reward().onBlindCleared(player.getUniqueId(), anteBefore, bt.key);
            if (bt == Data.BlindType.BOSS) {
                plugin.fireAnteClear(player.getUniqueId(), anteBefore);
                plugin.services().reward().onAnteCleared(player.getUniqueId(), anteBefore);
            }
            if (state.won) {
                finishRun(true, state.ante);
            }
            // else：phase 已为 SHOP（endRound→openShop），等待 /balatro next 推进
        } else if (r.ok && r.lost) {
            plugin.fireBlindResult(player.getUniqueId(), anteBefore, bt.key, target, state.roundScore, false);
            finishRun(false, anteBefore);
        }
        if (board != null) {
            board.clearSelection();
            board.update(state);
        }
        return r;
    }

    /** 弃牌。 */
    public Engine.PlayResult discard(List<Integer> cardIds) {
        Engine.PlayResult r = Engine.discard(state, cardIds);
        if (board != null) {
            board.clearSelection();
            board.update(state);
        }
        return r;
    }

    /** 销毁牌桌实体（会话结束时调用）。 */
    public void despawnBoard() {
        if (board != null) {
            board.despawn();
            board = null;
        }
    }

    /** 继续无尽模式（通关后）。 */
    public boolean continueEndless() {
        if (Engine.continueEndless(state)) {
            autoAdvance();
            if (board != null) board.update(state);
            return true;
        }
        return false;
    }

    /** 离开商店，进入下一盲注的【选择阶段】（不自动开始；用 go/skip 选择）。 */
    public boolean nextRound() {
        if (state.phase != Phase.SHOP) return false;
        Engine.nextRound(state);
        if (state.won) {
            finishRun(true, state.ante);
            return true;
        }
        // 停在 BLIND_SELECT，等待玩家 go（开始）/ skip（跳过获标签）
        if (board != null) board.update(state);
        return true;
    }

    /**
     * 在盲注选择阶段：开始当前盲注（{@code skip=false}）或跳过并获标签（{@code skip=true}）。
     * Boss 盲注不可跳过。返回是否成功推进。
     */
    public boolean chooseBlind(boolean skip) {
        if (state.phase != Phase.BLIND_SELECT) return false;
        boolean ok = Engine.selectBlind(state, Data.BlindType.byKey(state.nextBlind), skip);
        if (!ok) return false;
        if (board != null) board.update(state); // 开始→回合；跳过→下一盲注选择
        return true;
    }

    /** 购买商店第 idx 张商品（卡牌行）。 */
    public boolean buyCard(int idx) {
        if (state.phase != Phase.SHOP) return false;
        boolean ok = cn.quotidietium.balatro.engine.shop.Shop.buyCard(state, idx);
        if (board != null) board.update(state);
        return ok;
    }

    /** 购买第 idx 个补充包。 */
    public boolean buyPack(int idx) {
        if (state.phase != Phase.SHOP) return false;
        boolean ok = cn.quotidietium.balatro.engine.shop.Shop.buyPack(state, idx);
        if (board != null) board.update(state);
        return ok;
    }

    /** 购买第 idx 张优惠券（0-based）。 */
    public boolean buyVoucher(int idx) {
        if (state.phase != Phase.SHOP) return false;
        boolean ok = cn.quotidietium.balatro.engine.shop.Shop.buyVoucher(state, idx);
        if (board != null) board.update(state);
        return ok;
    }

    /** 商店重掷；返回本次费用，-1 表示失败。 */
    public long reroll() {
        if (state.phase != Phase.SHOP) return -1;
        long cost = cn.quotidietium.balatro.engine.shop.Shop.reroll(state);
        if (cost >= 0 && board != null) board.update(state);
        return cost;
    }

    /** 使用消耗品 idx；cardIds 为目标手牌卡 id（可空）。 */
    public cn.quotidietium.balatro.engine.consumable.Consumables.Result useConsumable(int idx, List<Integer> cardIds) {
        var r = cn.quotidietium.balatro.engine.consumable.Consumables.use(state, idx, cardIds);
        if (r.ok && board != null) board.update(state);
        return r;
    }

    /** 从当前补充包选第 idx 张。 */
    public boolean pickPack(int idx) {
        boolean ok = cn.quotidietium.balatro.engine.shop.Packs.pick(state, idx);
        if (board != null) board.update(state);
        return ok;
    }

    /** 跳过当前补充包。 */
    public boolean skipPack() {
        boolean ok = cn.quotidietium.balatro.engine.shop.Packs.skip(state);
        if (board != null) board.update(state);
        return ok;
    }

    /** 出售第 idx 张小丑。 */
    public boolean sellJoker(int idx) {
        boolean ok = state.sellJoker(idx);
        if (ok && board != null) board.update(state);
        return ok;
    }

    /** 出售第 idx 个消耗品。 */
    public boolean sellConsumable(int idx) {
        boolean ok = state.sellConsumable(idx);
        if (ok && board != null) board.update(state);
        return ok;
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
        sendRunStats(won, anteReached);
        if (!won) {
            // 失败：销毁牌桌并移除会话，玩家可立刻 /balatro play 再来一局
            plugin.sessionManager().end(player);
        }
        // 通关(won)：保留会话与牌桌，玩家可选 /endless 继续或 /quit 结束
    }

    /** 向玩家发送本局统计（任何结束情况都发）。 */
    private void sendRunStats(boolean won, int anteReached) {
        player.sendMessage("§6━━ 本局结束 ━━");
        player.sendMessage((won ? "§a§l通关！" : "§c§l本局失败")
                + "§r  §e到达底注 " + anteReached + " / 8");
        String deckName = state.deckKey;
        try {
            deckName = Data.deckByKey(state.deckKey).name();
        } catch (IllegalArgumentException ignored) {
        }
        String stakeName = (state.stakeIdx >= 0 && state.stakeIdx < Data.STAKES.size())
                ? Data.STAKES.get(state.stakeIdx).name() : String.valueOf(state.stakeIdx);
        StringBuilder mode = new StringBuilder("§e种子 ").append(state.seed)
                .append(" §7·§e 牌组 ").append(deckName)
                .append(" §7·§e 赌注 ").append(stakeName);
        if (state.challenge != null) {
            for (Data.Challenge c : Data.CHALLENGES) {
                if (c.key().equals(state.challenge)) {
                    mode.append(" §7·§e 挑战 ").append(c.name());
                    break;
                }
            }
        }
        player.sendMessage(mode.toString());
        player.sendMessage("§7打出 §f" + state.statsHandsPlayed + " §7手牌 · 持有 §f"
                + state.jokers.size() + " §7张小丑 · 剩余 §f$" + state.money);
        if (won) {
            player.sendMessage("§e/balatro endless §7继续无尽模式  §e/balatro quit §7结束本局");
        } else {
            player.sendMessage("§e/balatro play §7再来一局");
        }
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
