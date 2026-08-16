package cn.quotidietium.balatro.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P14 守门：一次性流的分段折叠与「拼接字符串建流」逐位等价。
 *
 * <p>消耗品/补充包的流名内嵌递增序号（useSeq/packSeq），永不复现——改为分段折叠
 * 直接建流（零字符串物化、零缓存插入）。FNV-1a 左到右可分段：折叠
 * {@code A+B} ≡ 从 hash(A) 态继续折叠 B；int 段折叠其十进制字符
 * （负数含 '-'，MIN_VALUE 用 long 幅度）。本测试对 语义矩阵逐值断言两种建流方式的
 * next() 序列逐位一致——种子复现红线守门。
 */
class RngSegmentedStreamTest {

    private static void assertStreamsEqual(Rng.Stream a, Rng.Stream b, int n, String ctx) {
        for (int i = 0; i < n; i++) {
            double va = a.next();
            double vb = b.next();
            assertEquals(Double.doubleToRawLongBits(va), Double.doubleToRawLongBits(vb),
                    ctx + " 第 " + i + " 个值");
        }
    }

    @Test
    void streamUseSegmentedEquivalence() {
        String[] keys = {"magician", "strength", "mercury", "aura", "cryptid", "soul", "a"};
        int[] rounds = {0, 1, 2, 9, 10, 123, 99999};
        int[] seqs = {1, 2, 3, 17, 100, 65535, 1234567};
        for (String seed : new String[]{"SEGA", "SEGB", "12345", "zz-seed_X"}) {
            int prefix = Rng.prefixHash(seed);
            for (String key : keys) {
                for (int rc : rounds) {
                    for (int seq : seqs) {
                        String name = "use:" + key + ":" + rc + ":" + seq;
                        assertStreamsEqual(
                                Rng.streamUse(prefix, key, rc, seq),
                                Rng.makeStream(seed, name),
                                16, seed + "/" + name);
                    }
                }
            }
        }
    }

    @Test
    void streamPackSegmentedEquivalence() {
        String[] keys = {"arcana1", "arcana2", "arcana3", "celestial1", "standard3", "buffoon2", "spectral2"};
        int[] rounds = {0, 1, 7, 42, 1000};
        int[] seqs = {1, 2, 5, 99, 300000};
        for (String seed : new String[]{"SEGA", "PKB", "q-9"}) {
            int prefix = Rng.prefixHash(seed);
            for (String key : keys) {
                for (int rc : rounds) {
                    for (int seq : seqs) {
                        String name = "pack" + rc + ":" + key + ":" + seq;
                        assertStreamsEqual(
                                Rng.streamPack(prefix, rc, key, seq),
                                Rng.makeStream(seed, name),
                                16, seed + "/" + name);
                    }
                }
            }
        }
    }

    /** foldInt 对全值域抽样的十进制折叠与 Integer.toString 折叠逐位一致（含负数与边界）。 */
    @Test
    void foldIntMatchesStringFolding() {
        int[] vals = {0, 1, 9, 10, 99, 100, 127, 128, 255, 1000, 65535, 65536,
                Integer.MAX_VALUE, -1, -9, -10, -128, -65535, Integer.MIN_VALUE, Integer.MIN_VALUE + 1};
        for (int v : vals) {
            int hStr = 0x811C9DC5;
            String s = Integer.toString(v);
            for (int i = 0; i < s.length(); i++) {
                hStr ^= s.charAt(i);
                hStr *= 0x01000193;
            }
            int hSeg = Rng.foldInt(0x811C9DC5, v);
            assertEquals(hStr, hSeg, "v=" + v);
        }
    }

    /** P15：每回合一次性流（prefix+roundCount，如 shuffle/shopgen）与拼接路径逐位一致。 */
    @Test
    void streamRoundSegmentedEquivalence() {
        String[] prefixes = {"shuffle", "shopgen"};
        int[] rounds = {0, 1, 2, 9, 10, 123, 99999};
        for (String seed : new String[]{"SEGA", "RND", "42"}) {
            int prefix = Rng.prefixHash(seed);
            for (String rp : prefixes) {
                for (int rc : rounds) {
                    assertStreamsEqual(
                            Rng.streamRound(prefix, rp, rc),
                            Rng.makeStream(seed, rp + rc),
                            16, seed + "/" + rp + rc);
                }
            }
        }
    }

    /** RunState.streamUse/streamPack 与旧拼接路径（含序号递增副作用）逐位一致。 */
    @Test
    void runStateOneShotStreamsMatchLegacyConcat() {
        for (String seed : new String[]{"RUNA", "RUNB"}) {
            RunState s = Engine.createRun("red", 0, seed);
            RunState legacy = Engine.createRun("red", 0, seed); // 孪生：手动重放旧拼接语义
            String[] keys = {"magician", "strength", "mercury"};
            for (int call = 0; call < 12; call++) {
                String key = keys[call % 3];
                s.roundCount = call % 4; // 固定回合变化
                legacy.roundCount = call % 4;
                Rng.Stream viaNew = s.streamUse(key); // 内部 ++useSeq（旧语义同名副作用）
                String name = "use:" + key + ":" + legacy.roundCount + ":" + (legacy.useSeq = legacy.useSeq + 1);
                assertStreamsEqual(viaNew, legacy.stream(name), 8, seed + "/" + name);
                assertEquals(legacy.useSeq, s.useSeq, "序号递增副作用一致");
            }
        }
    }
}
