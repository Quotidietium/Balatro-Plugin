package cn.quotidietium.balatro.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 牌型判定，移植自 {@code engine.js} 的 {@code Engine.evaluateHand}（纯逻辑）。
 * 输入 1~5 张牌 + flags，返回最佳牌型与计分牌。
 *
 * <p><b>P3 性能重写</b>：原实现每次判定分配 TreeSet/LinkedHashMap/HashSet/Stream 管线
 * （基准实测 ~1.3KB/次，且该路径是全息牌桌「实时牌型评估」的热路径——每次选牌变化都会
 * 走这里，基准手牌枚举策略同样高频调用）。现改为平坦数组（int[15] 点数计数 /
 * boolean[15] 点数出现 / Card[n] 顺序暂存），输出对象与分支语义**逐字保持**：
 * 分支次序、计分牌选择顺序（组间点数降序、组内手牌原序）、contains 集合口径全部不变
 * （54 黄金 + HandEval 边界/混沌测试锁定）。
 */
public final class HandEval {

    private HandEval() {
    }

    public static final class Result {
        public final Data.HandType type;
        public final List<Card> scoring;
        /** R130 真版 contains 语义：打出的牌**包含**的牌型集合（附加型小丑触发口径，
         *  Jolly/Wily/Runner 等族； wiki Important Joker Terms "Contains"）。 */
        public final Set<Data.HandType> contains;

        public Result(Data.HandType type, List<Card> scoring) {
            this(type, scoring, Set.of());
        }

        public Result(Data.HandType type, List<Card> scoring, Set<Data.HandType> contains) {
            this.type = type;
            this.scoring = scoring;
            this.contains = contains;
        }

        /** 手牌是否**包含**指定牌型（真版 contains 口径）。 */
        public boolean contains(Data.HandType t) {
            return contains.contains(t);
        }
    }

    public static Result evaluate(RunState state, List<Card> cards) {
        Map<String, Object> f = state.flags != null ? state.flags : new java.util.HashMap<>();
        int n = cards.size();
        if (n == 0) return null;

        boolean fourFingers = Boolean.TRUE.equals(f.get("fourFingers"));
        boolean smeared = Boolean.TRUE.equals(f.get("smeared"));
        boolean shortcut = Boolean.TRUE.equals(f.get("shortcut"));
        boolean splash = Boolean.TRUE.equals(f.get("splash"));

        // 有点数花色的牌（排除石头牌），保持手牌原序
        Card[] suited = new Card[n];
        int m = 0;
        for (Card c : cards) {
            if (c.enh() != Data.Enhancement.STONE) suited[m++] = c;
        }

        // 点数计数（rank 2..14；石头已排除，wild 保留原 rank）
        int[] cnt = new int[15];
        for (int i = 0; i < m; i++) cnt[suited[i].rank()]++;
        // 前两大计数（降序），对应原 counts.sort(reverseOrder()) 的 c0/c1
        int c0 = 0, c1 = 0;
        for (int r = 2; r <= 14; r++) {
            if (cnt[r] > c0) { c1 = c0; c0 = cnt[r]; }
            else if (cnt[r] > c1) { c1 = cnt[r]; }
        }

        // 花色匹配（万能牌全适配；污渍合并）。
        // 四指(Four Fingers)：同花只需 need(4) 张同花色，第 5 张可为任意花色（不阻止同花）。
        int need = fourFingers ? 4 : 5;
        int flushSuit = -1;
        for (int s = 0; s < 4; s++) {
            int c = 0;
            for (int i = 0; i < m; i++) if (suitMatch(suited[i], s, smeared)) c++;
            if (c >= need) { flushSuit = s; break; }
        }
        boolean hasFlush = flushSuit >= 0;

        // 顺子判定（四指：4 连续即可，即使打了 5 张）
        int straightLen = fourFingers ? 4 : 5;
        int[] straightWin = straightWindow(suited, m, straightLen, shortcut);
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
        List<Card> scoring = new ArrayList<>(n);
        if (type == Data.HandType.HIGH) {
            Card best = null;
            for (Card c : cards) if (best == null || c.rank() > best.rank()) best = c;
            scoring.add(best);
        } else if (type == Data.HandType.PAIR || type == Data.HandType.TWOPAIR
                || type == Data.HandType.THREE || type == Data.HandType.FOUR || type == Data.HandType.FIVE) {
            int needCount = (type == Data.HandType.PAIR || type == Data.HandType.TWOPAIR) ? 2
                    : (type == Data.HandType.THREE ? 3 : (type == Data.HandType.FOUR ? 4 : 5));
            // 组间点数降序（对齐原 rankKeys.sort(reverseOrder())）；组内保持手牌原序
            for (int rk = 14; rk >= 2; rk--) {
                int size = cnt[rk];
                if (size >= needCount || (type == Data.HandType.TWOPAIR && size >= 2)) {
                    for (int i = 0; i < m; i++) {
                        if (suited[i].rank() == rk) scoring.add(suited[i]);
                    }
                }
            }
        } else if (type == Data.HandType.STRAIGHT) {
            // 顺子计分牌：仅构成顺子的牌（四指时异点牌不计分；同点数重复均计分）
            for (int i = 0; i < m; i++) if (inStraight(suited[i], straightWin)) scoring.add(suited[i]);
        } else if (type == Data.HandType.FLUSH) {
            // 同花计分牌：仅同花色的牌（四指时非同花色牌不计分）
            for (int i = 0; i < m; i++) if (suitMatch(suited[i], flushSuit, smeared)) scoring.add(suited[i]);
        } else if (type == Data.HandType.SFLUSH || type == Data.HandType.ROYAL) {
            // 同花顺/皇家计分牌：顺子牌 ∪ 同花牌（对齐真版 Four Fingers——异花牌参与顺子则计分）。
            // 例：四指 5♠6♠7♥8♠9♠ → 顺子牌含 7♥，同花牌含 4♠；并集 = 全 5 张，7♥ 计分。
            // 但四指 5♠6♠7♠8♠K♥ → K♥ 不参与顺子也不参与同花，不计分。
            for (int i = 0; i < m; i++) {
                if (inStraight(suited[i], straightWin) || suitMatch(suited[i], flushSuit, smeared)) {
                    scoring.add(suited[i]);
                }
            }
        } else {
            for (int i = 0; i < m; i++) scoring.add(suited[i]);
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
        // R130 真版 contains 集合（wiki Important Joker Terms）：
        // pair=任一秩≥2 张；twopair=至少两个秩各≥2 张（四条不含两对）；three/four/five=对应张数；
        // straight/flush=独立判定（四指/捷径放宽口径，与主判定一致）；
        // full=三条+对子；sflush/royal=顺子∪同花（附加型只需 contains straight 与 flush）。
        Set<Data.HandType> contains = EnumSet.noneOf(Data.HandType.class);
        // 计数语义（真版 contains）：c0/c1 为降序计数——对子=任一秩≥2（c0>=2，含三条/四条/葫芦）、
        // 两对=至少两个秩各≥2（c1>=2，含葫芦）、三条/四条/五条同理放宽。
        if (c0 >= 2) contains.add(Data.HandType.PAIR);
        if (c1 >= 2) contains.add(Data.HandType.TWOPAIR);
        if (c0 >= 3) contains.add(Data.HandType.THREE);
        if (c0 >= 4) contains.add(Data.HandType.FOUR);
        if (c0 >= 5) contains.add(Data.HandType.FIVE);
        if (isFull) contains.add(Data.HandType.FULL);
        if (hasStraight) contains.add(Data.HandType.STRAIGHT);
        if (hasFlush) contains.add(Data.HandType.FLUSH);
        contains.add(type); // 手牌本身必然被包含
        return new Result(type, scoring, contains);
    }

    private static boolean suitMatch(Card c, int s, boolean smeared) {
        if (c.enh() == Data.Enhancement.WILD) return true;
        if (smeared) {
            if (s == 1 || s == 3) return c.suit() == 1 || c.suit() == 3;
            return c.suit() == 0 || c.suit() == 2;
        }
        return c.suit() == s;
    }

    /** 返回构成顺子的窗口点数（升序，A 低时含 1），无顺子返回 null。
     *  P3：distinct 点数用 boolean[15] 收集后按 1..14 升序展开——与原 TreeSet 的
     *  去重+升序语义一致（A(14) 额外映射到 1）。 */
    private static int[] straightWindow(Card[] suited, int m, int len, boolean shortcut) {
        if (m < len) return null;
        boolean[] present = new boolean[15];
        for (int i = 0; i < m; i++) {
            present[suited[i].rank()] = true;
            if (suited[i].rank() == 14) present[1] = true; // A 可低
        }
        int d = 0;
        for (int r = 1; r <= 14; r++) if (present[r]) d++;
        int[] arr = new int[d];
        for (int r = 1, k = 0; r <= 14; r++) if (present[r]) arr[k++] = r; // 升序
        int maxGap = shortcut ? 2 : 1;
        for (int i = 0; i + len <= arr.length; i++) {
            boolean ok = true;
            for (int j = i + 1; j < i + len; j++) {
                int dd = arr[j] - arr[j - 1];
                if (dd < 1 || dd > maxGap) { ok = false; break; }
            }
            int span = arr[i + len - 1] - arr[i];
            if (ok && span <= 4 * maxGap && span >= len - 1) {
                return Arrays.copyOfRange(arr, i, i + len);
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
