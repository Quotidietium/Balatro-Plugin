package cn.quotidietium.balatro.api.event;

import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 玩家开始一局小丑牌时触发。<b>可取消</b>（取消则不开始本局）。
 *
 * <p>预留扩展点：其他插件可监听以记录/拦截开局。
 */
public class BalatroRunStartEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final UUID playerId;
    private final String seed;
    private final String deckKey;
    private final int stakeIdx;

    public BalatroRunStartEvent(UUID playerId, String seed, String deckKey, int stakeIdx) {
        this.playerId = playerId;
        this.seed = seed;
        this.deckKey = deckKey;
        this.stakeIdx = stakeIdx;
    }

    public UUID getPlayerId() { return playerId; }
    public String getSeed() { return seed; }
    public String getDeckKey() { return deckKey; }
    public int getStakeIdx() { return stakeIdx; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
