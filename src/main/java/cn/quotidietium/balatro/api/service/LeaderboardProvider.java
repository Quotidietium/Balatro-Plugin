package cn.quotidietium.balatro.api.service;

import cn.quotidietium.balatro.api.RunSummary;
import java.util.List;

/**
 * 排行榜查询。默认 {@code MemoryLeaderboard} 基于 {@link StatsService} 内存排序；
 * 后续可替换为持久化/PlaceholderAPI 暴露实现。
 *
 * <p>排序口径：优先通关（won），其次到达底注（anteReached），最后时间。
 */
public interface LeaderboardProvider {

    /** 取前 n 名。 */
    List<RunSummary> top(int n);

    /** 玩家个人最佳（到达底注最高的记录），无则 null。 */
    RunSummary bestOf(java.util.UUID player);
}
