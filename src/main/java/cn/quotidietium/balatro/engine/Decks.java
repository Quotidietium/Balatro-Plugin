package cn.quotidietium.balatro.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 标准 52 张牌组构建辅助（测试用）。id 从 {@code idStart} 起递增，顺序对齐原版：
 * 黑桃 2..A → 红桃 2..A → 梅花 2..A → 方块 2..A。
 *
 * <p>生产用牌组构建（含全部 15 牌组变体 erratic/checkered/abandoned/allStone/painted 等）
 * 在 {@link Engine#buildFullDeck}，本类仅供 {@code CardTest} 等单测构造标准牌组使用。
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
