package cn.quotidietium.balatro.api.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 通过一个底注（击败该底注 Boss）时触发。
 * <b>过关奖励</b>扩展点：监听此事件按底注等级发放奖励。
 */
public class BalatroAnteClearEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final int ante;          // 刚通过的底注序号

    public BalatroAnteClearEvent(UUID playerId, int ante) {
        this.playerId = playerId;
        this.ante = ante;
    }

    public UUID getPlayerId() { return playerId; }
    public int getAnte() { return ante; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
