package cn.quotidietium.balatro.api.service;

import java.util.UUID;

/**
 * 通关次数计数器（0.3.9 新增）。
 *
 * <p>独立于 {@link StatsService} 的单局记录，专门统计每个玩家累计通关 ante 8 的次数。
 * 默认内存实现；{@code FileWinCounter} 提供跨重启持久化。
 *
 * <p><b>实现约束</b>：{@link #increment} 与 {@link #count} 均在 Bukkit 主线程调用，
 * 实现须保证快速返回（内存操作或缓存查询）。{@link #count} 在聚合排行榜中对每个玩家
 * 调用一次（N 玩家 = N 次调用），若需数据库 I/O 请在内部异步写入、主线程仅读内存缓存。
 */
public interface WinCounter {

    /** 玩家通关次数 +1（在通关 ante 8 时调用）。 */
    void increment(UUID player);

    /** 取玩家累计通关次数（无记录返回 0）。 */
    int count(UUID player);
}
