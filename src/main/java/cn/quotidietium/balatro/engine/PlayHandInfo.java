package cn.quotidietium.balatro.engine;

import java.util.List;

/**
 * 出牌后传给小丑 {@code onPlayHand} 钩子的信息，对应 balatro playHand 内的 info 对象。
 */
public final class PlayHandInfo {
    public final Data.HandType handType;
    public final List<Card> playedCards;
    public final List<Card> scoredCards;
    public final boolean hasFace;
    public final boolean isMostPlayed;
    private final RunState state;

    public PlayHandInfo(RunState state, Data.HandType handType, List<Card> playedCards, List<Card> scoredCards) {
        this.state = state;
        this.handType = handType;
        this.playedCards = playedCards;
        this.scoredCards = scoredCards;
        boolean face = false;
        for (Card c : playedCards) {
            if (state.isFace(c)) {
                face = true;
                break;
            }
        }
        this.hasFace = face;
        this.isMostPlayed = state.mostPlayedType() == handType;
    }

    public boolean hasRank(int r) {
        for (Card c : playedCards) {
            if (c.rank() == r) return true;
        }
        return false;
    }

    public JokerInstance findJoker(String key) {
        for (JokerInstance j : state.jokers) {
            if (!j.debuff && j.def.key().equals(key)) return j;
        }
        return null;
    }
}
