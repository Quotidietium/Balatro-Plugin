package cn.quotidietium.balatro.engine;

import java.util.List;

/**
 * 计分上下文，对应 balatro playHand 内的 ctx 对象。
 * chips/mult 为可变双精度（对齐 JS Number，兼容 ×1.5 等分数倍率），由 Engine 在计分末尾取整。
 */
public final class ScoreContext {
    public final RunState state;
    public final Data.HandType handType;
    public final List<Card> playedCards;
    public final List<Card> heldCards;
    public final List<String> events;
    public int scoreIndex = -1;
    public JokerInstance joker;
    public boolean photoUsed;

    public double chips;
    public double mult;

    public ScoreContext(RunState state, Data.HandType handType, double baseChips, double baseMult,
                        List<Card> playedCards, List<Card> heldCards, List<String> events) {
        this.state = state;
        this.handType = handType;
        this.chips = baseChips;
        this.mult = baseMult;
        this.playedCards = playedCards;
        this.heldCards = heldCards;
        this.events = events;
    }

    public void addChips(long n) {
        chips += n;
    }

    public void addMult(long n) {
        mult += n;
    }

    public void xMult(double x) {
        mult *= x;
    }

    public void dollars(long n) {
        state.gainMoney(n);
        events.add("+$" + n);
    }

    public void msg(String t) {
        events.add(t);
    }

    public boolean prob(double p) {
        if (Boolean.TRUE.equals(state.flags.get("doubleProb"))) p = Math.min(1, p * 2);
        return state.stream("prob").chance(p);
    }

    public int rngInt(int a, int b) {
        return state.stream("prob").range(a, b);
    }

    public boolean isSuit(Card c, int s) {
        return state.isSuit(c, s);
    }

    public boolean isFace(Card c) {
        return state.isFace(c);
    }

    public boolean handIs(String key) {
        return handType.key.equals(key);
    }

    /** 获得随机消耗品（0.2.0 实现塔罗/星球/幻灵池；0.1.0 无调用方）。 */
    public void gainConsumable(String kind) {
        // TODO 0.2.0：按 kind 从 DATA.TAROT/PLANETS/SPECTRAL 取
    }
}
