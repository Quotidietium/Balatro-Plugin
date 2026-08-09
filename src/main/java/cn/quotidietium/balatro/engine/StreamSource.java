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
    private final Map<String, Rng.Stream> streams = new HashMap<>();

    public StreamSource(String runSeed) {
        this.runSeed = runSeed;
    }

    /** 取（必要时创建并缓存）指定名称的随机流。 */
    public Rng.Stream stream(String name) {
        return streams.computeIfAbsent(name, n -> Rng.makeStream(runSeed, n));
    }
}
