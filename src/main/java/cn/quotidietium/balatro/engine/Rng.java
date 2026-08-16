package cn.quotidietium.balatro.engine;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

/**
 * 种子随机数模块，逐 bit 移植自 {@code REF/balatro/js/rng.js}。
 *
 * <p>同一种子 + 同一调用序列 ⇒ 完全相同的随机结果（可复现 / 可分享种子）。
 * 这是整个引擎移植正确性的基石：所有随机性必须经由本类的命名流，禁止裸随机。
 *
 * <p>移植要点（与 JS 语义对齐）：
 * <ul>
 *   <li>32 位运算用 Java {@code int}（位模式与 JS 的无符号 32 位一致）；
 *       {@code >>>} 在 Java 中同样是无符号右移。</li>
 *   <li>{@code Math.imul(x,y)} 等价于 Java {@code int} 乘法（取低 32 位）。</li>
 *   <li>{@code >>> 0} 的"转无符号"语义仅影响最终展示，位模式不变，故全程用 {@code int} 即可。</li>
 * </ul>
 */
public final class Rng {
    private Rng() {
    }

    /** 字符串 → 32 位哈希（FNV-1a 变体）。返回 int，位模式同 JS 的无符号 32 位结果。 */
    public static int seedHash(String s) {
        int h = 0x811C9DC5; // 2166136261（位模式；最高位为 1，Java 中为负数，但位模式一致）
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193; // 16777619
        }
        return h;
    }

    /** 生成 8 位随机种子字符串的字母表（仅当用户留空种子时使用一次；不属于可复现流）。 */
    private static final String SEED_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** 生成 8 位随机种子字符串（仅当用户留空种子时使用一次；不属于可复现流）。 */
    public static String randomSeedString() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(SEED_CHARS.charAt(ThreadLocalRandom.current().nextInt(SEED_CHARS.length())));
        }
        return sb.toString();
    }

    /** 种子最大长度（防止超长用户输入进入 RNG/统计文件/聊天）。 */
    public static final int MAX_SEED_LEN = 32;

    /**
     * 合法用户种子：长度 1~{@value #MAX_SEED_LEN}，仅限字母/数字/下划线/连字符。
     *
     * <p>种子来自客户端输入，不可信：限制字符集与长度，避免控制字符/分隔符（破坏
     * 统计文件格式）与超长串（存储/展示/RNG 哈希成本）。功能上 FNV-1a 可哈希任意串，
     * 此校验纯属输入卫生策略。
     */
    public static boolean isValidSeed(String s) {
        if (s == null || s.isEmpty() || s.length() > MAX_SEED_LEN) return false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean ok = (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-';
            if (!ok) return false;
        }
        return true;
    }

    /** 创建一个命名随机流（对应 JS 的 {@code makeStream(runSeed, stream)}）。 */
    public static Stream makeStream(String runSeed, String stream) {
        return new Stream(seedHash(runSeed + "::" + stream));
    }

    // ---- P11 性能：流创建零字符串化 ----
    // FNV-1a 是左到右折叠：hash(runSeed + "::" + name) ≡ 先折叠 runSeed 再折叠 "::" 得到
    // 前缀态，再从该态继续折叠 name。StreamSource 每局预计算一次前缀态，之后每个新流
    // 的创建只折叠流名——与 makeStream 的拼接哈希**逐位等价**（等价性由
    // RngGoldenTest.streamFromPrefixEquivalence 逐值断言锁定）。

    /** 折叠 {@code runSeed + "::"} 的 FNV-1a 前缀态（每局一次）。 */
    static int prefixHash(String runSeed) {
        int h = 0x811C9DC5;
        for (int i = 0; i < runSeed.length(); i++) {
            h ^= runSeed.charAt(i);
            h *= 0x01000193;
        }
        h ^= ':'; h *= 0x01000193;
        h ^= ':'; h *= 0x01000193;
        return h;
    }

    /** 从前缀态折叠流名并建流（与 {@link #makeStream} 逐位等价）。 */
    static Stream streamFrom(int prefix, String stream) {
        int h = prefix;
        for (int i = 0; i < stream.length(); i++) {
            h ^= stream.charAt(i);
            h *= 0x01000193;
        }
        return new Stream(h);
    }

    // ---- P14 性能：分段折叠（免字符串物化的一次性流）----
    // FNV-1a 左到右折叠可分段：hash(A+B) ≡ 从 hash(A) 态继续折叠 B。
    // 一次性流名（内嵌递增序号永不复现）无需进缓存 Map，也无需把名字物化成 String——
    // 直接从前缀态逐段折叠，与 streamFrom(prefix, 拼接串) 逐位等价。

    /** 折叠一个字符串段。 */
    static int foldStr(int h, String s) {
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return h;
    }

    /** 折叠一个字符。 */
    static int foldChar(int h, char c) {
        h ^= c;
        return h * 0x01000193;
    }

    /**
     * 折叠一个 int 的十进制表示（与折叠 {@code Integer.toString(v)} 的字符逐位一致；
     * 负数先折叠 '-'，用 long 幅度规避 MIN_VALUE 取反溢出）。
     */
    static int foldInt(int h, int v) {
        if (v < 0) {
            h = foldChar(h, '-');
            long m = -(long) v;
            // 位数探测后从高位折起（免字符串/数组）
            long pow = 1;
            while (pow * 10 <= m) pow *= 10;
            while (pow > 0) {
                // 先取模再转型：商≥2^31 时 (int) 商会溢出为负（MIN_VALUE 末位事故）
                h = foldChar(h, (char) ('0' + (int) ((m / pow) % 10)));
                pow /= 10;
            }
            return h;
        }
        long m = v;
        long pow = 1;
        while (pow * 10 <= m) pow *= 10;
        while (pow > 0) {
            h = foldChar(h, (char) ('0' + (int) ((m / pow) % 10)));
            pow /= 10;
        }
        return h;
    }

    /** 建一次性流：从前缀态折叠 {@code "use:"+key+':'+roundCount+':'+seq}（不进缓存）。 */
    static Stream streamUse(int prefix, String key, int roundCount, int seq) {
        int h = foldStr(prefix, "use:");
        h = foldStr(h, key);
        h = foldChar(h, ':');
        h = foldInt(h, roundCount);
        h = foldChar(h, ':');
        h = foldInt(h, seq);
        return new Stream(h);
    }

    /** 建一次性流：从前缀态折叠 {@code prefix+roundCount}（如 "shuffle3"/"shopgen7"，不进缓存）。 */
    static Stream streamRound(int prefix, String roundPrefix, int roundCount) {
        int h = foldStr(prefix, roundPrefix);
        h = foldInt(h, roundCount);
        return new Stream(h);
    }

    /** 建一次性流：从前缀态折叠 {@code "pack"+roundCount+':'+key+':'+seq}（不进缓存）。 */
    static Stream streamPack(int prefix, int roundCount, String key, int seq) {
        int h = foldStr(prefix, "pack");
        h = foldInt(h, roundCount);
        h = foldChar(h, ':');
        h = foldStr(h, key);
        h = foldChar(h, ':');
        h = foldInt(h, seq);
        return new Stream(h);
    }

    /** 单条命名随机流（mulberry32）。每次调用 {@link #next()} 推进内部状态。 */
    public static final class Stream {
        private int a;

        Stream(int seed) {
            this.a = seed;
        }

        /** [0,1) 浮点（mulberry32 核心）。 */
        public double next() {
            a = a + 0x6D2B79F5;
            int t = a;
            t = (t ^ (t >>> 15)) * (t | 1);
            t = t ^ (t + ((t ^ (t >>> 7)) * (t | 61)));
            return Integer.toUnsignedLong(t ^ (t >>> 14)) / 4294967296.0;
        }

        /** [min,max] 整数（含两端）。
         * P2 性能：{@code (int) Math.floor(x)} 与 {@code (int) x} 在 x≥0 且非 NaN 时逐位等价
         * （截断即向下取整）；next()∈[0,1) 保证 x≥0，行为不变。 */
        public int range(int min, int max) {
            return min + (int) (next() * (max - min + 1));
        }

        /** 从列表中等概率取一个元素；空列表返回 null。 */
        public <T> T pick(List<T> arr) {
            if (arr == null || arr.isEmpty()) {
                return null;
            }
            return arr.get((int) (next() * arr.size()));
        }

        /** 以 p 概率返回 true（p 为 0..1）。 */
        public boolean chance(double p) {
            return next() < p;
        }

        /** 原地洗牌（Fisher–Yates），返回同一列表引用。 */
        public <T> List<T> shuffle(List<T> arr) {
            if (arr == null) {
                return null;
            }
            for (int i = arr.size() - 1; i > 0; i--) {
                int j = (int) (next() * (i + 1));
                T tmp = arr.get(i);
                arr.set(i, arr.get(j));
                arr.set(j, tmp);
            }
            return arr;
        }

        /**
         * 按权重取一个元素（权重由 {@code weightOf} 提供；权重 ≤0 不参与）。
         * 全部非正或空列表返回 null。
         */
        public <T> T weighted(List<T> items, ToIntFunction<T> weightOf) {
            if (items == null || items.isEmpty()) {
                return null;
            }
            double total = 0;
            for (T it : items) {
                total += Math.max(0, weightOf.applyAsInt(it));
            }
            if (total <= 0) {
                return null;
            }
            double r = next() * total;
            for (T it : items) {
                r -= Math.max(0, weightOf.applyAsInt(it));
                if (r < 0) {
                    return it;
                }
            }
            return items.get(items.size() - 1);
        }
    }
}
