package cn.quotidietium.balatro.api.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 盲注结算时触发（通过或耗尽出牌次数失败）。
 * <b>过关奖励</b>扩展点：监听此事件按盲注类型/底注发放奖励。
 */
public class BalatroBlindResultEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final int ante;
    private final String blindType;   // small/big/boss
    private final long target;
    private final long score;         // 该盲注累计得分
    private final boolean cleared;    // 是否通过

    public BalatroBlindResultEvent(UUID playerId, int ante, String blindType, long target, long score, boolean cleared) {
        this.playerId = playerId;
        this.ante = ante;
        this.blindType = blindType;
        this.target = target;
        this.score = score;
        this.cleared = cleared;
    }

    public UUID getPlayerId() { return playerId; }
    public int getAnte() { return ante; }
    public String getBlindType() { return blindType; }
    public long getTarget() { return target; }
    public long getScore() { return score; }
    public boolean isCleared() { return cleared; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
