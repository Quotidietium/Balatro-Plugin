package cn.quotidietium.balatro.engine;

import java.util.Objects;

/**
 * 一张游戏牌，对应 balatro {@code Engine.makeCard} 产出的对象。
 *
 * <p>字段全部可变（rank/suit 会被塔罗/幻灵改写；enh/edition/seal 会被附加；
 * chipBonus 为徒步者等永久加成；debuff 由 Boss/挑战置位；facedown 由 Boss 置位）。
 * 相等性以唯一 {@link #id} 为准——即便两张牌 rank+suit 相同也能区分（对齐 doudizhu Card.id 思路）。
 *
 * <p>rank：2..14（11=J 12=Q 13=K 14=A），石头牌为 0；
 * suit：0..3（黑桃/红桃/梅花/方块），石头牌为 -1。
 */
public final class Card {
    private final int id;
    private int rank;
    private int suit;
    private Data.Enhancement enh;
    private Data.Edition edition;
    private Data.Seal seal;
    private long chipBonus;
    private boolean debuff;
    private boolean facedown;

    public Card(int id, int rank, int suit) {
        this.id = id;
        this.rank = rank;
        this.suit = suit;
    }

    public int id() {
        return id;
    }

    public int rank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public int suit() {
        return suit;
    }

    public void setSuit(int suit) {
        this.suit = suit;
    }

    public Data.Enhancement enh() {
        return enh;
    }

    public void setEnh(Data.Enhancement enh) {
        this.enh = enh;
    }

    public Data.Edition edition() {
        return edition;
    }

    public void setEdition(Data.Edition edition) {
        this.edition = edition;
    }

    public Data.Seal seal() {
        return seal;
    }

    public void setSeal(Data.Seal seal) {
        this.seal = seal;
    }

    public long chipBonus() {
        return chipBonus;
    }

    public void addChipBonus(long bonus) {
        this.chipBonus += bonus;
    }

    public boolean debuff() {
        return debuff;
    }

    public void setDebuff(boolean debuff) {
        this.debuff = debuff;
    }

    public boolean facedown() {
        return facedown;
    }

    public void setFacedown(boolean facedown) {
        this.facedown = facedown;
    }

    /** 石头牌：无点数/花色。 */
    public boolean isStone() {
        return rank == 0 || suit < 0;
    }

    /** 人头牌 J/Q/K。 */
    public boolean isFace() {
        return rank >= 11 && rank <= 13;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card c)) return false;
        return this.id == c.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        if (isStone()) return "石头";
        return Data.Suit.byIndex(suit).symbol + Data.rankName(rank);
    }
}
