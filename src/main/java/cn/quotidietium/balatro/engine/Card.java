package cn.quotidietium.balatro.engine;

import java.util.Objects;

/**
 * 一张游戏牌，对应 balatro {@code Engine.makeCard} 产出的对象。
 *
 * <p>字段全部可变（rank/suit 会被塔罗/幻灵改写；enh/edition/seal 会被附加；
 * chipBonus 为徒步者等永久加成；debuff 由 Boss/挑战置位；facedown 由 Boss 置位）。
 * 相等性以唯一 {@link #id} 为准——即便两张牌 rank+suit 相同也能区分。
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
    private boolean broken; // 玻璃牌破碎（计分后销毁，从牌组移除）

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

    public boolean isBroken() {
        return broken;
    }

    public void setBroken(boolean broken) {
        this.broken = broken;
    }

    /**
     * 石头牌：enh 为 STONE 即视为石头（对齐原版各处按 {@code enh==="stone"} 判定），
     * 兼容牌组构建/高塔转化的 0/-1 壳。marble 生成的石头牌为黑桃 2 壳 + STONE 增强，
     * 仅凭 rank/suit 会漏判（排序/渲染/持有效果都依赖本方法）。
     */
    public boolean isStone() {
        return enh == Data.Enhancement.STONE || rank == 0 || suit < 0;
    }

    /**
     * 设置牌的增强，正确处理石头牌转换（对齐真版）。
     *
     * <p>真版语义（[Reddit](https://www.reddit.com/r/balatro/comments/1bn9dpi/) +
     * [Stone cards Wiki](https://balatrowiki.org/w/Stone_cards)）：增强替换原增强；
     * 石头牌被转为非 stone 增强后不再是石头，须恢复合法 rank/suit。REF engine.js 此处未恢复
     * （REF bug：石头牌转其他增强/setEnh(null) 后 rank/suit 仍 0/-1，isStone 按 rank==0 仍判石头
     * 但 enh 已非 stone，HandEval/scoreOneCard 按 enh!=stone 当普通牌，rank=0/suit=-1 参与判定致混乱）。
     *
     * <p>消耗品（magician/empress/hierophant/lovers/chariot/justice/devil/tower）、
     * 小丑（vampire 移除增强、midas 变黄金）共用本方法保证石头牌转换的状态一致性。
     *
     * @param newEnh 新增强（含 STONE 与 null）
     */
    public void applyEnhancement(Data.Enhancement newEnh) {
        this.enh = newEnh;
        if (newEnh == Data.Enhancement.STONE) {
            this.rank = 0;
            this.suit = -1;
        } else if (this.rank == 0 || this.suit < 0) {
            // 从石头转为普通增强/无增强：恢复合法底层（无底层记录时用黑桃2，对齐 marble 石头壳默认）
            if (this.rank < 2) this.rank = 2;
            if (this.suit < 0) this.suit = 0;
        }
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
