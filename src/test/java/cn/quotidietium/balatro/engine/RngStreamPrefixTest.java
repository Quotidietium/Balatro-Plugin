package cn.quotidietium.balatro.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P11 等价性锁定：{@link Rng#prefixHash} + {@link Rng#streamFrom} 的分段折叠
 * 与 {@link Rng#makeStream} 的整串拼接哈希**逐位一致**（FNV-1a 左到右折叠可分段）。
 *
 * <p>StreamSource 未命中建流已改走前缀路径——本测试是「流创建零字符串化」优化的
 * 行为等价红线：任何分叉都会直接破坏种子复现，必须在黄金测试之前挡住。
 */
class RngStreamPrefixTest {

    @Test
    void prefixFoldEqualsConcatHash() {
        String[] seeds = {"", "A", "ABC123", "BENCHSL", "BENCHFR42",
                "0123456789abcdef0123456789abcdef01", "Z-Z_zz"};
        String[] names = {"core", "prob", "shuffle17", "shopgen12", "deckbuild",
                "xray", "acorn", "wheel", "hook", "rpc", "randomjoker", "illusion"};
        for (String seed : seeds) {
            int prefix = Rng.prefixHash(seed);
            for (String name : names) {
                Rng.Stream a = Rng.makeStream(seed, name);
                Rng.Stream b = Rng.streamFrom(prefix, name);
                for (int i = 0; i < 64; i++) {
                    assertEquals(a.next(), b.next(), 0.0,
                            "prefix/concat diverged: seed=" + seed + " name=" + name + " i=" + i);
                }
            }
        }
    }

    @Test
    void prefixStateMatchesConcatOfDelimiter() {
        // 前缀态本身 == FNV(seedHash 终态继续折叠 "::")
        for (String seed : new String[]{"", "A", "SEED-42", "0123456789abcdef0123456789abcdef01"}) {
            int h = Rng.seedHash(seed);
            h ^= ':'; h *= 0x01000193;
            h ^= ':'; h *= 0x01000193;
            assertEquals(h, Rng.prefixHash(seed), "prefix state mismatch: " + seed);
        }
    }
}
