package cn.quotidietium.balatro.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * 命名随机流的惰性缓存，对应 balatro {@code state.stream(name)}：
 * {@code makeStreamFactory(state)} 返回的函数会按流名惰性创建并复用同一条流，
 * 保证同一名流的状态推进连续、可复现。
 *
 * <p><b>移植红线</b>：引擎各处获取随机必须经由 {@link #stream(String)}，
 * 且对每个流名的调用顺序必须与原版逐字一致，否则种子不复现。
 */
public final class StreamSource {
    private final String runSeed;
    /** P11 性能：runSeed+"::" 的 FNV-1a 前缀态（每局一次），未命中建流零字符串拼接。 */
    private final int seedPrefix;
    /** P11 性能：预置容量——每局流名持续增长（shuffle/shopgen 带回合后缀，~2/回合）。 */
    private final Map<String, Rng.Stream> streams = new HashMap<>(32);

    public StreamSource(String runSeed) {
        this.runSeed = runSeed;
        this.seedPrefix = Rng.prefixHash(runSeed);
    }

    /** 取（必要时创建并缓存）指定名称的随机流。
     *
     * <p>P2 性能：原实现 computeIfAbsent(name, lambda) 每次调用都新分配一个捕获
     * {@code runSeed} 的 lambda 实例（实测 16 B/op）。改为 get/缺省创建/put——
     * 命中路径零分配。RunState 为单玩家单线程访问，无需并发原语，语义不变。
     * <p>P11 性能：未命中路径改用前缀哈希增量折叠（与 Rng.makeStream 逐位等价），
     * 免去 runSeed+"::"+name 的字符串拼接分配。 */
    public Rng.Stream stream(String name) {
        Rng.Stream st = streams.get(name);
        if (st == null) {
            st = Rng.streamFrom(seedPrefix, name);
            streams.put(name, st);
        }
        return st;
    }

    /**
     * P14 性能：一次性流（名字内嵌递增序号，永不复现）——分段折叠直接建流，
     * **零字符串物化、零缓存插入**（原实现每次拼接 String + HashMap 插入，且长局下
     * 缓存 Map 无界增长）。与 {@code stream("use:"+key+":"+roundCount+":"+seq)}
     * 逐位等价（FNV-1a 分段折叠，等价性由守门测试锁定）；跳过缓存对可观察行为
     * 无影响：同名二次取流在本命名方案下不可能（seq 严格递增），缓存副本永不再读。
     */
    public Rng.Stream streamUse(String key, int roundCount, int seq) {
        return Rng.streamUse(seedPrefix, key, roundCount, seq);
    }

    /** 同 {@link #streamUse}（"pack"+roundCount+":"+key+":"+seq 命名方案）。 */
    public Rng.Stream streamPack(int roundCount, String key, int seq) {
        return Rng.streamPack(seedPrefix, roundCount, key, seq);
    }

    /** 兼容保留：种子串（供调试/展示）。 */
    @Override
    public String toString() {
        return runSeed;
    }
}
