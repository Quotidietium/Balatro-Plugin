package cn.quotidietium.balatro.api.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 一局结束（通关 ante 8 或失败）时触发。
 * <b>得分排名</b>扩展点：监听此事件把本局结果写入统计/排行榜。
 */
public class BalatroRunEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final boolean won;
    private final int anteReached;   // 到达的底注序号
    private final String seed;
    private final String deckKey;
    private final int stakeIdx;

    public BalatroRunEndEvent(UUID playerId, boolean won, int anteReached, String seed, String deckKey, int stakeIdx) {
        this.playerId = playerId;
        this.won = won;
        this.anteReached = anteReached;
        this.seed = seed;
        this.deckKey = deckKey;
        this.stakeIdx = stakeIdx;
    }

    public UUID getPlayerId() { return playerId; }
    public boolean isWon() { return won; }
    public int getAnteReached() { return anteReached; }
    public String getSeed() { return seed; }
    public String getDeckKey() { return deckKey; }
    public int getStakeIdx() { return stakeIdx; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
