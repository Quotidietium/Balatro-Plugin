package cn.quotidietium.balatro.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 牌组构建，移植自 {@code engine.js} 的 {@code buildFullDeck}（标准牌组分支）。
 *
 * <p>0.1.0 仅标准 52 张牌组（花色 0..3 × 点数 2..14），id 从 {@code idStart} 起递增，
 * 顺序对齐原版：黑桃 2..A → 红桃 2..A → 梅花 2..A → 方块 2..A。
 * 牌组变体（erratic/checkered/abandoned/allStone 等）随 0.5.0 牌组特性补齐。
 */
public final class Decks {
    private Decks() {
    }

    /** 标准 52 张牌组。id 从 idStart 起，按原版顺序。 */
    public static List<Card> standard52(int idStart) {
        List<Card> deck = new ArrayList<>(52);
        int id = idStart;
        for (int s = 0; s < 4; s++) {
            for (int r = 2; r <= 14; r++) {
                deck.add(new Card(id++, r, s));
            }
        }
        return deck;
    }
}
