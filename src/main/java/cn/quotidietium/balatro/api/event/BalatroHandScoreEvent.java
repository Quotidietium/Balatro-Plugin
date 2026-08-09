package cn.quotidietium.balatro.api.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 每次出牌计分后触发。供实时计分展示/分析扩展使用。
 */
public class BalatroHandScoreEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final String handType;   // 牌型 key（high/pair/...）
    private final long score;        // 本次出牌得分
    private final long roundScore;   // 本盲注累计分
    private final long blindTarget;  // 本盲注目标分
    private final int handsLeft;

    public BalatroHandScoreEvent(UUID playerId, String handType, long score, long roundScore, long blindTarget, int handsLeft) {
        this.playerId = playerId;
        this.handType = handType;
        this.score = score;
        this.roundScore = roundScore;
        this.blindTarget = blindTarget;
        this.handsLeft = handsLeft;
    }

    public UUID getPlayerId() { return playerId; }
    public String getHandType() { return handType; }
    public long getScore() { return score; }
    public long getRoundScore() { return roundScore; }
    public long getBlindTarget() { return blindTarget; }
    public int getHandsLeft() { return handsLeft; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
