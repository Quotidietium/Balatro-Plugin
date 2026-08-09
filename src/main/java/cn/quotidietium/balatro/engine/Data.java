package cn.quotidietium.balatro.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * 静态数据表，移植自 {@code REF/balatro/js/data.js}。
 *
 * <p>0.1.0 范围：花色、点数（名称/牌面筹码）、13 种牌型（基础筹码/倍率 + 每级成长 + 顺序）、
 * 盲注目标分（底注表 + 倍率 + 奖励）。其余数据表（牌组/赌注/Boss/标签/优惠券/塔罗/星球/幻灵/
 * 增强/版本/蜡封/补充包/挑战/稀有度）随后续版本补齐。
 *
 * <p>数值类型约定（与 balatro 的 JS Number 对齐，整数域优先 long）：
 * <ul>
 *   <li>chips/mult/money/score/target 用 {@code long}（整数域，正常对局内远在 long 范围内）。</li>
 *   <li>深无尽模式（底注 16+，目标分 8.6e20 超 long 上限）不可达，{@link #blindBase} 对超出表/超 long 的值钳制为 {@link Long#MAX_VALUE}。</li>
 * </ul>
 */
public final class Data {
    private Data() {
    }

    // ================= 花色 =================
    // 0 黑桃 1 红桃 2 梅花 3 方块
    public enum Suit {
        SPADE(0, "spade", "黑桃", "♠", "dark"),
        HEART(1, "heart", "红桃", "♥", "red"),
        CLUB(2, "club", "梅花", "♣", "dark"),
        DIAMOND(3, "diamond", "方块", "♦", "red");

        public final int index;
        public final String key;
        public final String name;
        public final String symbol;
        public final String color;

        Suit(int index, String key, String name, String symbol, String color) {
            this.index = index;
            this.key = key;
            this.name = name;
            this.symbol = symbol;
            this.color = color;
        }

        public boolean isRed() {
            return "red".equals(color);
        }

        private static final Map<String, Suit> BY_KEY = new HashMap<>();
        static {
            for (Suit s : values()) BY_KEY.put(s.key, s);
        }

        public static Suit byIndex(int index) {
            for (Suit s : values()) if (s.index == index) return s;
            throw new IllegalArgumentException("suit index out of range: " + index);
        }

        public static Suit byKey(String key) {
            Suit s = BY_KEY.get(key);
            if (s == null) throw new IllegalArgumentException("unknown suit: " + key);
            return s;
        }
    }

    // ================= 点数 ================= 2..14（11=J 12=Q 13=K 14=A）
    /** 点数显示名。 */
    public static String rankName(int r) {
        if (r >= 2 && r <= 10) return Integer.toString(r);
        switch (r) {
            case 11: return "J";
            case 12: return "Q";
            case 13: return "K";
            case 14: return "A";
            default: return "?";
        }
    }

    /** 牌面筹码：2~10 面值，J/Q/K=10，A=11。 */
    public static int rankChips(int r) {
        if (r >= 2 && r <= 10) return r;
        if (r == 14) return 11;
        return 10;
    }

    // ================= 牌型（13 种） =================
    public enum HandType {
        HIGH("high", "高牌", 5, 1, 10, 1, 1),
        PAIR("pair", "对子", 10, 2, 15, 1, 2),
        TWOPAIR("twopair", "两对", 20, 2, 20, 1, 3),
        THREE("three", "三条", 30, 3, 20, 2, 4),
        STRAIGHT("straight", "顺子", 30, 4, 30, 3, 5),
        FLUSH("flush", "同花", 35, 4, 15, 2, 6),
        FULL("full", "葫芦", 40, 4, 25, 2, 7),
        FOUR("four", "四条", 60, 7, 30, 3, 8),
        SFLUSH("sflush", "同花顺", 100, 8, 40, 4, 9),
        ROYAL("royal", "皇家同花顺", 100, 8, 40, 4, 10),
        FIVE("five", "五条", 120, 12, 35, 3, 11),
        FHOUSE("fhouse", "同花葫芦", 140, 14, 40, 4, 12),
        FFIVE("ffive", "同花五条", 160, 16, 50, 5, 13);

        public final String key;
        public final String name;
        public final int chips;   // 1 级基础筹码
        public final int mult;    // 1 级基础倍率
        public final int lchips;  // 每升 1 级 +筹码
        public final int lmult;   // 每升 1 级 +倍率
        public final int order;   // 同级判定时的排序（大者胜）

        HandType(String key, String name, int chips, int mult, int lchips, int lmult, int order) {
            this.key = key;
            this.name = name;
            this.chips = chips;
            this.mult = mult;
            this.lchips = lchips;
            this.lmult = lmult;
            this.order = order;
        }

        private static final Map<String, HandType> BY_KEY = new HashMap<>();
        static {
            for (HandType h : values()) BY_KEY.put(h.key, h);
        }

        public static HandType byKey(String key) {
            HandType h = BY_KEY.get(key);
            if (h == null) throw new IllegalArgumentException("unknown hand: " + key);
            return h;
        }

        /** 升级后的基础筹码（level >= 1）：chips + lchips*(level-1)。 */
        public long chipsAtLevel(int level) {
            return (long) chips + (long) lchips * Math.max(0, level - 1);
        }

        /** 升级后的基础倍率（level >= 1）：mult + lmult*(level-1)。 */
        public long multAtLevel(int level) {
            return (long) mult + (long) lmult * Math.max(0, level - 1);
        }

        /** 出牌优先级比较（同级按 order 大者胜，对应 data.js 的 handOrder）。返回 <0/0/>0。 */
        public static int compareOrder(HandType a, HandType b) {
            return Integer.compare(a.order, b.order);
        }
    }

    // ================= 盲注目标分 =================
    // 底注 1~8 的基础目标分；下标 0 占位。
    private static final long[] ANTE_BASE = {0, 300, 800, 2000, 5000, 11000, 20000, 35000, 50000};
    // 无尽模式（底注 9+）基础目标分；下标 0 占位。[8]=8.6e20 超 long → 钳制为 MAX。
    private static final long[] ANTE_ENDLESS = {
            0, 110_000L, 560_000L, 7_200_000L, 300_000_000L,
            47_000_000_000L, 29_000_000_000_000L, 77_000_000_000_000_000L, Long.MAX_VALUE
    };

    /** 底注 ante（>=1）的基础目标分（不含盲注倍率/赌注修饰）。 */
    public static long blindBase(int ante) {
        if (ante <= 8) return ANTE_BASE[ante];
        int idx = ante - 8;
        if (idx <= 8) return ANTE_ENDLESS[idx];
        return Long.MAX_VALUE; // 超出表格，不可达
    }

    // ================= 盲注类型（倍率 + 奖励） =================
    public enum BlindType {
        SMALL("small", 1.0, 3),
        BIG("big", 1.5, 4),
        BOSS("boss", 2.0, 5);

        public final String key;
        public final double mult;   // BLIND_MULT
        public final int reward;    // BLIND_REWARD（$）

        BlindType(String key, double mult, int reward) {
            this.key = key;
            this.mult = mult;
            this.reward = reward;
        }

        private static final Map<String, BlindType> BY_KEY = new HashMap<>();
        static {
            for (BlindType b : values()) BY_KEY.put(b.key, b);
        }

        public static BlindType byKey(String key) {
            BlindType b = BY_KEY.get(key);
            if (b == null) throw new IllegalArgumentException("unknown blind: " + key);
            return b;
        }
    }

    // ================= 增强（8） =================
    public enum Enhancement {
        BONUS("bonus", "奖励牌", "+30 筹码"),
        MULT("mult", "倍率牌", "+4 倍率"),
        WILD("wild", "万能牌", "可视为任意花色"),
        GLASS("glass", "玻璃牌", "×2 倍率；计分后 1/4 概率破碎"),
        STEEL("steel", "钢铁牌", "持有在手时 ×1.5 倍率"),
        STONE("stone", "石头牌", "+50 筹码；无点数与花色"),
        GOLD("gold", "黄金牌", "回合结束时若仍在手中 +$3"),
        LUCKY("lucky", "幸运牌", "1/5 概率 +20 倍率，1/15 概率 +$20");

        public final String key;
        public final String name;
        public final String desc;

        Enhancement(String key, String name, String desc) {
            this.key = key;
            this.name = name;
            this.desc = desc;
        }

        private static final Map<String, Enhancement> BY_KEY = new HashMap<>();
        static {
            for (Enhancement e : values()) BY_KEY.put(e.key, e);
        }

        public static Enhancement byKey(String key) {
            Enhancement e = BY_KEY.get(key);
            if (e == null) throw new IllegalArgumentException("unknown enhancement: " + key);
            return e;
        }
    }

    // ================= 版本（4，不含原版） =================
    public enum Edition {
        FOIL("foil", "闪膜", "+50 筹码", 0.50),
        HOLO("holo", "镭射", "+10 倍率", 0.35),
        POLY("poly", "多彩", "×1.5 倍率", 0.15),
        NEGATIVE("negative", "负片", "槽位 +1（小丑/消耗品）", 0.0);

        public final String key;
        public final String name;
        public final String desc;
        public final double chance; // 商店出现权重（相对）

        Edition(String key, String name, String desc, double chance) {
            this.key = key;
            this.name = name;
            this.desc = desc;
            this.chance = chance;
        }

        private static final Map<String, Edition> BY_KEY = new HashMap<>();
        static {
            for (Edition e : values()) BY_KEY.put(e.key, e);
        }

        public static Edition byKey(String key) {
            Edition e = BY_KEY.get(key);
            if (e == null) throw new IllegalArgumentException("unknown edition: " + key);
            return e;
        }
    }

    // ================= 蜡封（4） =================
    public enum Seal {
        GOLD("gold", "金色蜡封", "该牌计分时 +$3"),
        RED("red", "红色蜡封", "该牌重新触发一次"),
        BLUE("blue", "蓝色蜡封", "回合结束时若仍在手中，获得对应星球牌"),
        PURPLE("purple", "紫色蜡封", "弃掉该牌时获得一张塔罗牌");

        public final String key;
        public final String name;
        public final String desc;

        Seal(String key, String name, String desc) {
            this.key = key;
            this.name = name;
            this.desc = desc;
        }

        private static final Map<String, Seal> BY_KEY = new HashMap<>();
        static {
            for (Seal s : values()) BY_KEY.put(s.key, s);
        }

        public static Seal byKey(String key) {
            Seal s = BY_KEY.get(key);
            if (s == null) throw new IllegalArgumentException("unknown seal: " + key);
            return s;
        }
    }
}
