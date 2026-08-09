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

        // 花色匹配（万能牌全适配；污渍合并）
        int need = fourFingers ? 4 : 5;
        int flushSuit = -1;
        for (int s = 0; s < 4; s++) {
            int cnt = 0;
            for (Card c : suited) if (suitMatch(c, s, smeared)) cnt++;
            if (cnt >= need && suited.size() >= need) {
                boolean ok = true;
                for (Card c : suited) if (!suitMatch(c, s, smeared)) { ok = false; break; }
                if (ok) { flushSuit = s; break; }
            }
        }
        boolean hasFlush = flushSuit >= 0;

        // 顺子判定
        int straightLen = suited.size() >= 5 ? 5 : (fourFingers ? 4 : 5);
        boolean hasStraight = isStraight(suited, straightLen, shortcut);

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
        else if (hasStraight && hasFlush) {
            List<Integer> rs = suited.stream().map(Card::rank).sorted().toList();
            type = (rs.contains(10) && rs.contains(14)) ? Data.HandType.ROYAL : Data.HandType.SFLUSH;
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

    private static boolean isStraight(List<Card> suited, int len, boolean shortcut) {
        if (suited.size() < len) return false;
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
            if (ok && span <= 4 * maxGap && span >= len - 1) return true;
        }
        return false;
    }
}
