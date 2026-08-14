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

    // ================= Boss 盲注（28） =================
    // 28 个 Boss 的 key/name/desc；效果实现见 Engine.java 各 Boss 分支（startRound/drawOne/
    // drawUpTo/playHand/blindTarget 等）+ RunState.disableBoss/sellJoker（leaf 解除）。
    public enum Boss {
        HOOK("hook", "钩子", "每次出牌后随机弃掉 2 张手牌"),
        OX("ox", "公牛", "打出本局最常用的牌型时，金钱归零"),
        HOUSE("house", "房子", "第一手牌全部面朝下"),
        WALL("wall", "高墙", "超大盲注（目标分 ×4）"),
        WHEEL("wheel", "车轮", "抽到的牌有 1/7 概率面朝下"),
        ARM("arm", "手臂", "每次出牌后，所出牌型等级 -1"),
        CLUB_BOSS("club", "梅花", "所有梅花牌失效"),
        GOAD("goad", "马刺", "所有黑桃牌失效"),
        HEAD("head", "头颅", "所有红桃牌失效"),
        WINDOW("window", "窗口", "所有方块牌失效"),
        FISH("fish", "鱼", "每次出牌后抽取的牌面朝下"),
        PSYCHIC("psychic", "通灵者", "每次必须出满 5 张牌"),
        SERPENT("serpent", "贪蛇", "出牌或弃牌后固定只抽 3 张牌"),
        PILLAR("pillar", "支柱", "本底注中已打出过的牌失效"),
        NEEDLE("needle", "缝衣针", "本回合只能出 1 次牌"),
        TOOTH("tooth", "牙齿", "每打出一张牌失去 $1"),
        FLINT("flint", "燧石", "基础筹码与基础倍率减半"),
        MARK("mark", "标记", "所有人头牌面朝下抽取"),
        ACORN("acorn", "琥珀橡子", "小丑牌被翻面并打乱顺序"),
        BELL("bell", "翠绿铃", "强制 1 张手牌始终处于选中状态"),
        HEART_BOSS("heart", "绯红之心", "每次出牌随机使 1 张小丑失效"),
        VESSEL("vessel", "紫罗兰之瓶", "巨大盲注（目标分 ×3）"),
        WATER("water", "水", "本回合没有弃牌次数"),
        MANACLE("manacle", "镣铐", "手牌上限 -1"),
        EYE("eye", "眼睛", "本回合每种牌型只能出一次"),
        MOUTH("mouth", "嘴", "本回合只能出一种牌型"),
        PLANT("plant", "植物", "所有人头牌失效"),
        LEAF("leaf", "翠绿之叶", "所有牌失效，直到出售 1 张小丑");

        public final String key;
        public final String name;
        public final String desc;

        Boss(String key, String name, String desc) {
            this.key = key;
            this.name = name;
            this.desc = desc;
        }

        private static final Map<String, Boss> BY_KEY = new HashMap<>();
        static {
            for (Boss b : values()) BY_KEY.put(b.key, b);
        }

        public static Boss byKey(String key) {
            Boss b = BY_KEY.get(key);
            if (b == null) throw new IllegalArgumentException("unknown boss: " + key);
            return b;
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

    // ================= 补充包类型 =================
    public enum PackType {
        ARCANA("arcana"), CELESTIAL("celestial"), STANDARD("standard"), BUFFOON("buffoon"), SPECTRAL("spectral");
        public final String key;
        PackType(String key) { this.key = key; }
        public static PackType byKey(String key) {
            for (PackType p : values()) if (p.key.equals(key)) return p;
            throw new IllegalArgumentException("unknown pack type: " + key);
        }
    }

    // ================= 塔罗牌（22） =================
    public enum Tarot {
        FOOL("fool", "愚人", "复制本局上一张使用的塔罗或星球牌"),
        MAGICIAN("magician", "魔术师", "至多 2 张手牌变为「幸运牌」"),
        PRIESTESS("priestess", "女祭司", "获得至多 2 张随机星球牌"),
        EMPRESS("empress", "皇后", "至多 2 张手牌变为「倍率牌」"),
        EMPEROR("emperor", "皇帝", "获得至多 2 张随机塔罗牌"),
        HIEROPHANT("hierophant", "教皇", "至多 2 张手牌变为「奖励牌」"),
        LOVERS("lovers", "恋人", "至多 1 张手牌变为「万能牌」"),
        CHARIOT("chariot", "战车", "至多 1 张手牌变为「钢铁牌」"),
        JUSTICE("justice", "正义", "至多 1 张手牌变为「玻璃牌」"),
        HERMIT("hermit", "隐者", "金钱翻倍（至多 +$20）"),
        WHEEL("wheel", "命运之轮", "1/4 概率为一张随机小丑附加版本"),
        STRENGTH("strength", "力量", "至多 2 张手牌点数 +1"),
        HANGED("hanged", "倒吊人", "销毁至多 2 张手牌"),
        DEATH("death", "死神", "选 2 张牌：左边那张变成右边那张"),
        TEMPERANCE("temperance", "节制", "获得全部小丑的总售价（至多 $50）"),
        DEVIL("devil", "恶魔", "至多 1 张手牌变为「黄金牌」"),
        TOWER("tower", "高塔", "至多 1 张手牌变为「石头牌」"),
        STAR("star", "星星", "至多 3 张手牌变为方块"),
        MOON("moon", "月亮", "至多 3 张手牌变为梅花"),
        SUN("sun", "太阳", "至多 3 张手牌变为红桃"),
        JUDGEMENT("judgement", "审判", "获得一张随机小丑牌"),
        WORLD("world", "世界", "至多 3 张手牌变为黑桃");
        public final String key, name, desc;
        Tarot(String k, String n, String d) { key = k; name = n; desc = d; }
        private static final Map<String, Tarot> BY_KEY = new HashMap<>();
        static { for (Tarot t : values()) BY_KEY.put(t.key, t); }
        public static Tarot byKey(String k) { Tarot t = BY_KEY.get(k); if (t == null) throw new IllegalArgumentException("unknown tarot: " + k); return t; }
    }

    // ================= 星球牌（12） =================
    public enum Planet {
        PLUTO("pluto", "冥王星", HandType.HIGH, "「高牌」升 1 级"),
        MERCURY("mercury", "水星", HandType.PAIR, "「对子」升 1 级"),
        URANUS("uranus", "天王星", HandType.TWOPAIR, "「两对」升 1 级"),
        VENUS("venus", "金星", HandType.THREE, "「三条」升 1 级"),
        SATURN("saturn", "土星", HandType.STRAIGHT, "「顺子」升 1 级"),
        JUPITER("jupiter", "木星", HandType.FLUSH, "「同花」升 1 级"),
        EARTH("earth", "地球", HandType.FULL, "「葫芦」升 1 级"),
        MARS("mars", "火星", HandType.FOUR, "「四条」升 1 级"),
        NEPTUNE("neptune", "海王星", HandType.SFLUSH, "「同花顺」升 1 级"),
        PLANETX("planetx", "X 行星", HandType.FIVE, "「五条」升 1 级"),
        CERES("ceres", "谷神星", HandType.FHOUSE, "「同花葫芦」升 1 级"),
        ERIS("eris", "阋神星", HandType.FFIVE, "「同花五条」升 1 级");
        public final String key, name, desc;
        public final HandType hand;
        Planet(String k, String n, HandType h, String d) { key = k; name = n; hand = h; desc = d; }
        private static final Map<String, Planet> BY_KEY = new HashMap<>();
        static { for (Planet p : values()) BY_KEY.put(p.key, p); }
        public static Planet byKey(String k) { Planet p = BY_KEY.get(k); if (p == null) throw new IllegalArgumentException("unknown planet: " + k); return p; }
        /** 按所升级的牌型查星球牌（无则 null）。蓝蜡封用。 */
        public static Planet byHand(HandType h) {
            for (Planet p : values()) if (p.hand == h) return p;
            return null;
        }
    }

    // ================= 幻灵牌（18） =================
    public enum Spectral {
        FAMILIAR("familiar", "妖精", "销毁 1 张随机手牌，获得 3 张随机增强人头牌"),
        GRIM("grim", "鬼魂", "销毁 1 张随机手牌，获得 2 张随机增强 A"),
        INCANTATION("incantation", "咒语", "销毁 1 张随机手牌，获得 4 张随机增强数字牌"),
        TALISMAN("talisman", "护符", "为 1 张手牌附加金色蜡封"),
        AURA("aura", "光环", "为 1 张手牌附加随机版本（闪膜/镭射/多彩）"),
        WRAITH("wraith", "幽灵", "获得一张随机稀有小丑，但金钱归零"),
        SIGIL("sigil", "印记", "全部手牌变为同一随机花色"),
        OUIJA("ouija", "占卜板", "全部手牌变为同一随机点数；手牌上限 -1"),
        HEX("hex", "妖术", "一张随机小丑变为负片，销毁其余小丑"),
        ANKH("ankh", "安魂曲", "复制一张随机小丑，销毁其余小丑"),
        DEJAVU("dejavu", "似曾相识", "为 1 张手牌附加红色蜡封"),
        TRANCE("trance", "恍惚", "为 1 张手牌附加蓝色蜡封"),
        MEDIUM("medium", "灵媒", "为 1 张手牌附加紫色蜡封"),
        CRYPTID("cryptid", "地穴生物", "选 1 张手牌，获得它的 2 张复制"),
        IMMOLATE("immolate", "献祭", "销毁 5 张随机手牌，获得 $20"),
        SOUL("soul", "灵魂", "获得一张随机传奇小丑"),
        BLACKHOLE("blackhole", "黑洞", "所有牌型各升 1 级"),
        ECTOPLASM("ectoplasm", "灵质", "一张随机小丑变为负片；手牌上限 -1");
        public final String key, name, desc;
        Spectral(String k, String n, String d) { key = k; name = n; desc = d; }
        private static final Map<String, Spectral> BY_KEY = new HashMap<>();
        static { for (Spectral s : values()) BY_KEY.put(s.key, s); }
        public static Spectral byKey(String k) { Spectral s = BY_KEY.get(k); if (s == null) throw new IllegalArgumentException("unknown spectral: " + k); return s; }
    }

    // ================= 补充包（13） =================
    public static final class Pack {
        public final String key, name;
        public final PackType type;
        public final int size, choose, cost;
        public Pack(String key, PackType type, String name, int size, int choose, int cost) {
            this.key = key; this.type = type; this.name = name; this.size = size; this.choose = choose; this.cost = cost;
        }
    }
    public static final java.util.List<Pack> PACKS = java.util.List.of(
            new Pack("arcana1", PackType.ARCANA, "奥术包", 3, 1, 4),
            new Pack("arcana2", PackType.ARCANA, "特大奥术包", 5, 1, 6),
            new Pack("arcana3", PackType.ARCANA, "巨型奥术包", 5, 2, 8),
            new Pack("celestial1", PackType.CELESTIAL, "天体包", 3, 1, 4),
            new Pack("celestial2", PackType.CELESTIAL, "特大天体包", 5, 1, 6),
            new Pack("celestial3", PackType.CELESTIAL, "巨型天体包", 5, 2, 8),
            new Pack("standard1", PackType.STANDARD, "标准包", 3, 1, 4),
            new Pack("standard2", PackType.STANDARD, "特大标准包", 5, 1, 6),
            new Pack("standard3", PackType.STANDARD, "巨型标准包", 5, 2, 8),
            new Pack("buffoon1", PackType.BUFFOON, "小丑包", 2, 1, 4),
            new Pack("buffoon2", PackType.BUFFOON, "特大小丑包", 4, 1, 6),
            new Pack("buffoon3", PackType.BUFFOON, "巨型小丑包", 4, 2, 8),
            new Pack("spectral1", PackType.SPECTRAL, "幻灵包", 2, 1, 4)
    );
    public static Pack packByKey(String key) {
        for (Pack p : PACKS) if (p.key.equals(key)) return p;
        throw new IllegalArgumentException("unknown pack: " + key);
    }

    // ================= 优惠券（32 = 16 对） =================
    public static final class Voucher {
        public final String key, name, desc;
        public final int base;
        public final String pair;     // 基础券：升级目标 key（升级券为 null）
        public final String requires; // 升级券：所依赖的基础券 key（基础券为 null）
        public Voucher(String key, String name, String desc, int base, String pair, String requires) {
            this.key = key; this.name = name; this.desc = desc; this.base = base; this.pair = pair; this.requires = requires;
        }
        public boolean isBase() { return pair != null; }
    }
    public static final java.util.List<Voucher> VOUCHERS = java.util.List.of(
            new Voucher("overstock", "多重库存", "商店卡牌位 +1", 10, "overstock2", null),
            new Voucher("overstock2", "多重库存+", "商店卡牌位再 +1", 10, null, "overstock"),
            new Voucher("clearance", "清仓特卖", "商店全部商品 75 折", 10, "liquidation", null),
            new Voucher("liquidation", "清仓大甩卖", "商店全部商品 5 折", 10, null, "clearance"),
            new Voucher("blank", "空白优惠券", "……什么也没有？", 10, "antimatter", null),
            new Voucher("antimatter", "反物质", "小丑槽 +1", 10, null, "blank"),
            new Voucher("tarotm", "塔罗商人", "商店塔罗牌出现率 ×2", 10, "tarott", null),
            new Voucher("tarott", "塔罗大亨", "商店塔罗牌出现率 ×4", 10, null, "tarotm"),
            new Voucher("planetm", "星球商人", "商店星球牌出现率 ×2", 10, "planett", null),
            new Voucher("planett", "星球大亨", "商店星球牌出现率 ×4", 10, null, "planetm"),
            new Voucher("hone", "磨刀石", "闪膜/镭射/多彩出现率 ×2", 10, "glowup", null),
            new Voucher("glowup", "光彩照人", "闪膜/镭射/多彩出现率 ×4", 10, null, "hone"),
            new Voucher("reroll1", "重掷红利", "商店重掷费用 -$2", 10, "reroll2", null),
            new Voucher("reroll2", "重掷狂欢", "商店重掷费用再 -$2", 10, null, "reroll1"),
            new Voucher("crystal", "水晶球", "消耗品槽 +1", 10, "omen", null),
            new Voucher("omen", "预言球", "幻灵牌可出现在商店", 10, null, "crystal"),
            new Voucher("telescope", "望远镜", "天体包必含你最常用牌型的星球牌", 10, "observatory", null),
            new Voucher("observatory", "天文台", "消耗品区的星球牌使其对应牌型倍率 ×1.5", 10, null, "telescope"),
            new Voucher("seedmoney", "种子基金", "利息上限提升至 $10", 10, "moneytree", null),
            new Voucher("moneytree", "摇钱树", "利息上限提升至 $20", 10, null, "seedmoney"),
            new Voucher("grabber", "补给手", "每回合出牌次数 +1", 10, "nacho", null),
            new Voucher("nacho", "顺手牵羊", "每回合出牌次数再 +1", 10, null, "grabber"),
            new Voucher("wasteful", "挥霍无度", "每回合弃牌次数 +1", 10, "recyclo", null),
            new Voucher("recyclo", "回收狂人", "每回合弃牌次数再 +1", 10, null, "wasteful"),
            new Voucher("magictrick", "魔术技巧", "商店可出现游戏牌", 10, "illusion", null),
            new Voucher("illusion", "幻觉", "商店的游戏牌可能带增强或版本", 10, null, "magictrick"),
            new Voucher("hieroglyph", "象形文字", "底注 -1；每回合出牌次数 -1", 10, "petroglyph", null),
            new Voucher("petroglyph", "岩画", "底注再 -1；每回合弃牌次数 -1", 10, null, "hieroglyph"),
            new Voucher("director", "导演剪辑版", "可花 $10 重掷 Boss 盲注", 10, "retcon", null),
            new Voucher("retcon", "翻拍", "重掷 Boss 盲注免费", 10, null, "director"),
            new Voucher("paintbrush", "油漆刷", "手牌上限 +1", 10, "palette", null),
            new Voucher("palette", "调色板", "手牌上限再 +1", 10, null, "paintbrush")
    );
    public static Voucher voucherByKey(String key) {
        for (Voucher v : VOUCHERS) if (v.key.equals(key)) return v;
        throw new IllegalArgumentException("unknown voucher: " + key);
    }

    // ================= 稀有度（4） =================
    public enum Rarity {
        COMMON("common", "普通", 70),
        UNCOMMON("uncommon", "罕见", 25),
        RARE("rare", "稀有", 5),
        LEGENDARY("legendary", "传奇", 0);
        public final String key, name;
        public final int weight;
        Rarity(String k, String n, int w) { key = k; name = n; weight = w; }
        public static Rarity byKey(String k) {
            for (Rarity r : values()) if (r.key.equals(k)) return r;
            throw new IllegalArgumentException("unknown rarity: " + k);
        }
    }

    // ================= 牌组（15）/ 赌注（8）/ 标签（24）/ 挑战（20）：名称与描述 =================
    public record Deck(String key, String name, String desc) {}
    public static final java.util.List<Deck> DECKS = java.util.List.of(
            new Deck("red", "红色牌组", "每回合弃牌次数 +1"),
            new Deck("blue", "蓝色牌组", "每回合出牌次数 +1"),
            new Deck("yellow", "黄色牌组", "开局额外获得 $10"),
            new Deck("green", "绿色牌组", "回合结束时每张剩余出牌 +$2、每张剩余弃牌 +$1；无利息"),
            new Deck("black", "黑色牌组", "小丑槽 +1；每回合出牌次数 -1"),
            new Deck("magic", "魔法牌组", "开局拥有「水晶球」优惠券与 2 张「愚人」"),
            new Deck("nebula", "星云牌组", "开局拥有「望远镜」优惠券；消耗品槽 -1"),
            new Deck("ghost", "幽灵牌组", "幻灵牌可出现在商店；开局拥有 1 张「妖术」"),
            new Deck("abandoned", "废弃牌组", "牌组中没有人头牌（共 40 张）"),
            new Deck("checkered", "棋盘牌组", "牌组由 26 张黑桃与 26 张红桃组成"),
            new Deck("zodiac", "黄道牌组", "开局拥有「塔罗商人」「星球商人」「多重库存」优惠券"),
            new Deck("painted", "涂鸦牌组", "手牌上限 +2；小丑槽 -1"),
            new Deck("anaglyph", "浮雕牌组", "每击败一个 Boss 盲注获得一个「翻倍标签」"),
            new Deck("plasma", "等离子牌组", "结算时筹码与倍率先取平均值再相乘；盲注目标分 ×2"),
            new Deck("erratic", "百变牌组", "牌组的点数与花色完全随机")
    );
    public static Deck deckByKey(String key) {
        for (Deck d : DECKS) if (d.key().equals(key)) return d;
        throw new IllegalArgumentException("unknown deck: " + key);
    }

    public record Stake(String key, String name, String desc) {}
    public static final java.util.List<Stake> STAKES = java.util.List.of(
            new Stake("white", "白注", "基础难度"),
            new Stake("red", "红注", "小盲注不提供奖励金"),
            new Stake("green", "绿注", "所需分数随底注加速增长（每底注 ×1.15）"),
            new Stake("black", "黑注", "商店可能出现「永恒」小丑（不可出售或摧毁）"),
            new Stake("blue", "蓝注", "每回合弃牌次数 -1"),
            new Stake("purple", "紫注", "所需分数随底注进一步加速增长（每底注 ×1.3）"),
            new Stake("orange", "橙注", "商店可能出现「易腐」小丑（5 回合后失效）"),
            new Stake("gold", "金注", "商店可能出现「租赁」小丑（每回合结束扣 $3）")
    );

    public record Tag(String key, String name, String desc) {}
    public static final java.util.List<Tag> TAGS = java.util.List.of(
            new Tag("double", "翻倍标签", "复制下一个获得的标签（不含本标签）"),
            new Tag("uncommon", "罕见标签", "下个商店必有一张罕见小丑"),
            new Tag("rare", "稀有标签", "下个商店必有一张稀有小丑"),
            new Tag("negative", "负片标签", "下个商店必有一张负片小丑"),
            new Tag("foil", "闪膜标签", "下个商店必有一张闪膜小丑"),
            new Tag("holo", "镭射标签", "下个商店必有一张镭射小丑"),
            new Tag("poly", "多彩标签", "下个商店必有一张多彩小丑"),
            new Tag("invest", "投资标签", "击败下一个 Boss 盲注后获得 $25"),
            new Tag("voucher", "优惠券标签", "下个商店出现一张优惠券"),
            new Tag("boss", "老板标签", "重新随机本底注的 Boss 盲注"),
            new Tag("standard", "标准标签", "立即免费开启一个标准补充包"),
            new Tag("charm", "魅力标签", "下个商店的塔罗牌免费"),
            new Tag("meteor", "流星标签", "下个商店的星球牌免费"),
            new Tag("buffoon", "小丑标签", "立即免费开启一个小丑补充包"),
            new Tag("handy", "手套标签", "立即获得 $1 × 本局已出牌次数"),
            new Tag("garbage", "垃圾标签", "立即获得 $1 × 本局未使用的弃牌次数"),
            new Tag("ethereal", "幽冥标签", "下个商店出现一个幻灵补充包"),
            new Tag("coupon", "折扣标签", "下个商店的卡牌与补充包免费"),
            new Tag("d6", "D6 标签", "下个商店重掷免费"),
            new Tag("topup", "补充标签", "立即获得至多 2 张随机普通小丑"),
            new Tag("speed", "速度标签", "立即获得 $5 × 本局已跳过的盲注数"),
            new Tag("orbital", "轨道标签", "随机一种牌型升 3 级"),
            new Tag("economy", "经济标签", "立即获得 $1 / 每 $5 持有资金（至多 $25）"),
            new Tag("juggle", "灵活标签", "下一回合手牌上限 +3")
    );

    public record Challenge(String key, String name, String desc) {}
    public static final java.util.List<Challenge> CHALLENGES = java.util.List.of(
            new Challenge("omelette", "煎蛋卷", "开局拥有 5 张「蛋」；盲注无奖励金、剩余出牌与利息均不产生金钱；经济类券/小丑禁入"),
            new Challenge("city15", "十五分钟城市", "所有人头牌翻倍（替换所有 A、2、3）；开局拥有永恒的「乘公交」与「捷径」"),
            new Challenge("rich", "富者愈富", "单手筹码不得超当前金钱；开局 $100 与「种子基金」「摇钱树」两券"),
            new Challenge("knife", "刀尖行走", "开局左位永恒「仪式匕首」：选盲注时吞噬右侧小丑，永久获得其售价×2 的倍率"),
            new Challenge("xray", "X 光视界", "抽到的牌有 1/4 概率面朝下"),
            new Challenge("madworld", "疯狂世界", "无利息与剩余出牌金；开局永恒负片「空想性错觉」与永恒「名片」；牌组仅 2~9 共 32 张；Boss「植物」禁现"),
            new Challenge("luxury", "奢侈品税", "手牌上限 10，每持有 $5 上限 -1（真版奢侈品税）"),
            new Challenge("nonperish", "永不过期", "所有小丑永恒；衰减/功能型小丑与 Boss「翠绿之叶」禁入"),
            new Challenge("medusa", "美杜莎", "所有人头牌变为石头牌；开局永恒「大理石小丑」"),
            new Challenge("double", "孤注一掷", "计分后的牌失效；全牌组红蜡封（各重触发一次）"),
            new Challenge("typecast", "刻板印象", "击败第 4 底注 Boss 后：全体小丑永恒且小丑槽归零；Boss「翠绿之叶」禁现"),
            new Challenge("inflation", "通货膨胀", "每次购买价格永久 +$1（持有物售价同步上涨，重掷不涨）；开局「信用卡」；折扣券禁入"),
            new Challenge("bram", "布拉姆扑克", "商店无小丑；开局永恒「吸血鬼」与「皇帝」「女皇」塔罗、「戏法」「幻觉」两券"),
            new Challenge("fragile", "易碎品", "开局两张永恒负片「全是 6！」；全牌组玻璃；非玻璃牌来源全数禁入"),
            new Challenge("monolith", "巨石阵", "开局永恒「方尖碑」与永恒负片「大理石小丑」；标准牌组"),
            new Challenge("blastoff", "点火升空", "每回合 2 出牌 2 弃牌、4 小丑槽；开局永恒「星座」「火箭」与双星球券；抓取/橙舌/夜贼禁入"),
            new Challenge("fivecard", "五张抽牌", "每回合 6 弃牌、手牌上限 5、7 小丑槽；开局「老千」与「小丑」；手牌上限类小丑禁入"),
            new Challenge("golden", "金针", "弃牌各花 $1；每回合 1 出牌 6 弃牌；开局 $10 与「信用卡」；夜贼/抓取/橙舌禁入"),
            new Challenge("cruelty", "残酷", "小盲与大盲注均无奖励金；仅 3 小丑槽"),
            new Challenge("jokerless", "无丑之地", "商店无小丑且 0 小丑槽；一切获得小丑的来源（审判/怨灵/灵魂/产出标签/丑牌包/反物质券）禁入")
    );
}
