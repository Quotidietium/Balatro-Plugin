package cn.quotidietium.balatro.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 牌型判定，移植自 {@code engine.js} 的 {@code Engine.evaluateHand}（纯逻辑）。
 * 输入 1~5 张牌 + flags，返回最佳牌型与计分牌。
 */
public final class HandEval {

    private HandEval() {
    }

    public static final class Result {
        public final Data.HandType type;
        public final List<Card> scoring;

        public Result(Data.HandType type, List<Card> scoring) {
            this.type = type;
            this.scoring = scoring;
        }
    }

    public static Result evaluate(RunState state, List<Card> cards) {
        Map<String, Object> f = state.flags != null ? state.flags : new java.util.HashMap<>();
        if (cards.isEmpty()) return null;

        // 有点数花色的牌（排除石头牌）
        List<Card> suited = new ArrayList<>();
        for (Card c : cards) {
            if (c.enh() != Data.Enhancement.STONE) suited.add(c);
        }

        // 点数统计
        Map<Integer, List<Card>> byRank = new LinkedHashMap<>();
        for (Card c : suited) {
            byRank.computeIfAbsent(c.rank(), k -> new ArrayList<>()).add(c);
        }
        List<Integer> counts = new ArrayList<>(byRank.values().stream().map(List::size).toList());
        counts.sort(Comparator.reverseOrder());

        boolean fourFingers = Boolean.TRUE.equals(f.get("fourFingers"));
        boolean smeared = Boolean.TRUE.equals(f.get("smeared"));
        boolean shortcut = Boolean.TRUE.equals(f.get("shortcut"));
        boolean splash = Boolean.TRUE.equals(f.get("splash"));

        // 花色匹配（万能牌全适配；污渍合并）。
        // 四指(Four Fingers)：同花只需 need(4) 张同花色，第 5 张可为任意花色（不阻止同花）。
        int need = fourFingers ? 4 : 5;
        int flushSuit = -1;
        for (int s = 0; s < 4; s++) {
            int cnt = 0;
            for (Card c : suited) if (suitMatch(c, s, smeared)) cnt++;
            if (cnt >= need) { flushSuit = s; break; }
        }
        boolean hasFlush = flushSuit >= 0;

        // 同花色牌集（同花顺/皇家判定与计分用）
        List<Card> flushCards = new ArrayList<>();
        if (hasFlush) {
            for (Card c : suited) if (suitMatch(c, flushSuit, smeared)) flushCards.add(c);
        }

        // 顺子判定（四指：4 连续即可，即使打了 5 张）
        int straightLen = fourFingers ? 4 : 5;
        int[] straightWin = straightWindow(suited, straightLen, shortcut);
        boolean hasStraight = straightWin != null;

        // 同花顺/皇家判定：hasStraight && hasFlush（独立判定，对齐真版 Four Fingers 行为：
        // 第 5 张异花牌不阻止同花顺——只要分别满足 4+ 连续顺子和 4+ 同花即可）。
        // 联网核实：Four Fingers 下 9♠8♠7♥6♠3♠ 判为同花顺（7♥ 参与顺子），
        // 全部 5 张计分（[Poker Hands](https://balatrogame.fandom.com/wiki/Poker_Hands) /
        // [Four Fingers](https://balatrogame.fandom.com/wiki/Four_Fingers)）。
        // 皇家 = 同花顺且顺子窗口内所有点数 ≥10（10/J/Q/K/A）。
        Data.HandType sfType = null;
        if (hasStraight && hasFlush) {
            boolean allTenPlus = true;
            for (int rk : straightWin) if (rk < 10) { allTenPlus = false; break; }
            sfType = allTenPlus ? Data.HandType.ROYAL : Data.HandType.SFLUSH;
        }

        int c0 = counts.isEmpty() ? 0 : counts.get(0);
        int c1 = counts.size() < 2 ? 0 : counts.get(1);
        boolean isFive = c0 == 5;
        boolean isFour = c0 == 4;
        boolean isFull = c0 == 3 && c1 >= 2;
        boolean isThree = c0 == 3;
        boolean isTwoPair = c0 == 2 && c1 == 2;
        boolean isPair = c0 == 2;

        Data.HandType type;
        if (isFive && hasFlush) type = Data.HandType.FFIVE;
        else if (isFull && hasFlush) type = Data.HandType.FHOUSE;
        else if (isFive) type = Data.HandType.FIVE;
        else if (sfType != null) {
            type = sfType; // 同花顺/皇家（顺子已确认完全落在同花花色内）
        } else if (isFour) type = Data.HandType.FOUR;
        else if (isFull) type = Data.HandType.FULL;
        else if (hasFlush) type = Data.HandType.FLUSH;
        else if (hasStraight) type = Data.HandType.STRAIGHT;
        else if (isThree) type = Data.HandType.THREE;
        else if (isTwoPair) type = Data.HandType.TWOPAIR;
        else if (isPair) type = Data.HandType.PAIR;
        else type = Data.HandType.HIGH;

        // 计分牌选择
        List<Card> scoring = new ArrayList<>();
        if (type == Data.HandType.HIGH) {
            Card best = null;
            for (Card c : cards) if (best == null || c.rank() > best.rank()) best = c;
            scoring.add(best);
        } else if (type == Data.HandType.PAIR || type == Data.HandType.TWOPAIR
                || type == Data.HandType.THREE || type == Data.HandType.FOUR || type == Data.HandType.FIVE) {
            int needCount = (type == Data.HandType.PAIR || type == Data.HandType.TWOPAIR) ? 2
                    : (type == Data.HandType.THREE ? 3 : (type == Data.HandType.FOUR ? 4 : 5));
            List<Integer> rankKeys = new ArrayList<>(byRank.keySet());
            rankKeys.sort(Comparator.reverseOrder());
            for (int rk : rankKeys) {
                List<Card> grp = byRank.get(rk);
                if (grp.size() >= needCount || (type == Data.HandType.TWOPAIR && grp.size() >= 2)) {
                    scoring.addAll(grp);
                }
            }
        } else if (type == Data.HandType.STRAIGHT) {
            // 顺子计分牌：仅构成顺子的牌（四指时异点牌不计分；同点数重复均计分）
            for (Card c : suited) if (inStraight(c, straightWin)) scoring.add(c);
        } else if (type == Data.HandType.FLUSH) {
            // 同花计分牌：仅同花色的牌（四指时非同花色牌不计分）
            for (Card c : suited) if (suitMatch(c, flushSuit, smeared)) scoring.add(c);
        } else if (type == Data.HandType.SFLUSH || type == Data.HandType.ROYAL) {
            // 同花顺/皇家计分牌：顺子牌 ∪ 同花牌（对齐真版 Four Fingers——异花牌参与顺子则计分）。
            // 例：四指 5♠6♠7♥8♠9♠ → 顺子牌含 7♥，同花牌含 4♠；并集 = 全 5 张，7♥ 计分。
            // 但四指 5♠6♠7♠8♠K♥ → K♥ 不参与顺子也不参与同花，不计分。
            for (Card c : suited) {
                if (inStraight(c, straightWin) || suitMatch(c, flushSuit, smeared)) scoring.add(c);
            }
        } else {
            scoring.addAll(suited);
        }
        // 石头牌永远计分
        for (Card c : cards) {
            if (c.enh() == Data.Enhancement.STONE && !scoring.contains(c)) scoring.add(c);
        }
        // 水花：全部计分
        if (splash) {
            scoring.clear();
            scoring.addAll(cards);
        }
        return new Result(type, scoring);
    }

    private static boolean suitMatch(Card c, int s, boolean smeared) {
        if (c.enh() == Data.Enhancement.WILD) return true;
        if (smeared) {
            if (s == 1 || s == 3) return c.suit() == 1 || c.suit() == 3;
            return c.suit() == 0 || c.suit() == 2;
        }
        return c.suit() == s;
    }

    /** 返回构成顺子的窗口点数（升序，A 低时含 1），无顺子返回 null。 */
    private static int[] straightWindow(List<Card> suited, int len, boolean shortcut) {
        if (suited.size() < len) return null;
        TreeSet<Integer> set = new TreeSet<>();
        for (Card c : suited) {
            set.add(c.rank());
            if (c.rank() == 14) set.add(1); // A 可低
        }
        int[] arr = set.stream().mapToInt(Integer::intValue).toArray(); // 升序
        int maxGap = shortcut ? 2 : 1;
        for (int i = 0; i + len <= arr.length; i++) {
            boolean ok = true;
            for (int j = i + 1; j < i + len; j++) {
                int d = arr[j] - arr[j - 1];
                if (d < 1 || d > maxGap) { ok = false; break; }
            }
            int span = arr[i + len - 1] - arr[i];
            if (ok && span <= 4 * maxGap && span >= len - 1) {
                int[] win = new int[len];
                System.arraycopy(arr, i, win, 0, len);
                return win;
            }
        }
        return null;
    }

    /** 该牌的点数是否落在顺子窗口内（A 既可作 14 也可作 1）。 */
    private static boolean inStraight(Card c, int[] window) {
        int mapped = (c.rank() == 14) ? 1 : c.rank();
        for (int w : window) {
            if (w == mapped) return true;
            if (c.rank() == 14 && w == 14) return true; // A 也作 14 参与 10-J-Q-K-A
        }
        return false;
    }
}
