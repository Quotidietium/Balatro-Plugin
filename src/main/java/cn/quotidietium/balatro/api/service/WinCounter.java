package cn.quotidietium.balatro.api.service;

import java.util.UUID;

/**
 * 通关次数计数器（0.3.9 新增）。
 *
 * <p>独立于 {@link StatsService} 的单局记录，专门统计每个玩家累计通关 ante 8 的次数。
 * 默认内存实现；{@code FileWinCounter} 提供跨重启持久化。
 */
public interface WinCounter {

    /** 玩家通关次数 +1（在通关 ante 8 时调用）。 */
    void increment(UUID player);

    /** 取玩家累计通关次数（无记录返回 0）。 */
    int count(UUID player);
}
