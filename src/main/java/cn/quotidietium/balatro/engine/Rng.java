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
        String str = String.valueOf(s);
        for (int i = 0; i < str.length(); i++) {
            h ^= str.charAt(i);
            h *= 0x01000193; // 16777619
        }
        return h;
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

    /** 生成 8 位随机种子字符串（仅当用户留空种子时使用一次；不属于可复现流）。 */
    public static String randomSeedString() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }

    /** 创建一个命名随机流（对应 JS 的 {@code makeStream(runSeed, stream)}）。 */
    public static Stream makeStream(String runSeed, String stream) {
        return new Stream(seedHash(runSeed + "::" + stream));
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

        /** [min,max] 整数（含两端）。 */
        public int range(int min, int max) {
            return min + (int) Math.floor(next() * (max - min + 1));
        }

        /** 从列表中等概率取一个元素；空列表返回 null。 */
        public <T> T pick(List<T> arr) {
            if (arr == null || arr.isEmpty()) {
                return null;
            }
            return arr.get((int) Math.floor(next() * arr.size()));
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
                int j = (int) Math.floor(next() * (i + 1));
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
