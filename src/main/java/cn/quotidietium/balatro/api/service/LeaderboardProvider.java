package cn.quotidietium.balatro.api.service;

import cn.quotidietium.balatro.api.PlayerStat;
import cn.quotidietium.balatro.api.RunSummary;
import java.util.List;

/**
 * 排行榜查询。默认 {@code MemoryLeaderboard} 基于 {@link StatsService} 内存排序；
 * 后续可替换为持久化/PlaceholderAPI 暴露实现。
 *
 * <p>排序口径：
 * <ul>
 *   <li>{@link #top}：单局记录，优先通关（won），其次到达底注（anteReached），最后时间。</li>
 *   <li>{@link #topAggregated}（0.3.9 新增）：按玩家聚合，最高底注降序 → 通关次数降序 → 玩家名升序。</li>
 * </ul>
 */
public interface LeaderboardProvider {

    /** 取前 n 名（单局记录）。 */
    List<RunSummary> top(int n);

    /** 玩家个人最佳（到达底注最高的记录），无则 null。 */
    RunSummary bestOf(java.util.UUID player);

    /**
     * 按玩家聚合的排行榜前 n 名（0.3.9 新增）。
     *
     * <p>每玩家一行：最高到达底注（含无尽 ante 9+）+ 累计通关次数。
     * 排序：bestAnte 降序 → winCount 降序 → playerName 升序。
     * 默认实现返回空（旧实现不破坏）；{@code MemoryLeaderboard} 覆盖。
     */
    default List<PlayerStat> topAggregated(int n) {
        return List.of();
    }
}
