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
    /** R130 真版：是否有**计分的**（非 debuff）人头牌——乘公交重置口径
     *  （Ride the Bus Wiki："without a scoring face card"，debuff 人头牌不重置）。 */
    public final boolean hasScoringFace;
    public final boolean isMostPlayed;
    /** R130 真版 contains 口径（附加型小丑/Runner 族触发判定）。 */
    public final java.util.Set<Data.HandType> contains;
    private final RunState state;

    public PlayHandInfo(RunState state, Data.HandType handType, List<Card> playedCards, List<Card> scoredCards) {
        this(state, handType, playedCards, scoredCards, java.util.Set.of());
    }

    public PlayHandInfo(RunState state, Data.HandType handType, List<Card> playedCards, List<Card> scoredCards,
                        java.util.Set<Data.HandType> contains) {
        this.state = state;
        this.handType = handType;
        this.playedCards = playedCards;
        this.scoredCards = scoredCards;
        this.contains = contains;
        boolean face = false;
        for (Card c : playedCards) {
            if (state.isFace(c)) {
                face = true;
                break;
            }
        }
        this.hasFace = face;
        boolean sface = false;
        for (Card c : scoredCards) {
            if (!c.debuff() && state.isFace(c)) {
                sface = true;
                break;
            }
        }
        this.hasScoringFace = sface;
        this.isMostPlayed = state.mostPlayedType() == handType;
    }

    /** 手牌是否**包含**指定牌型（真版 contains 口径）。 */
    public boolean handContains(Data.HandType t) {
        return contains.contains(t);
    }

    /** 指定点数是否出现在**计分牌**中（R130 真版叠加态：A 必须**计分**）。 */
    public boolean scoredHasRank(int r) {
        for (Card c : scoredCards) {
            if (c.rank() == r) return true;
        }
        return false;
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
