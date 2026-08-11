package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.PlayerStat;
import cn.quotidietium.balatro.api.RunSummary;
import cn.quotidietium.balatro.api.service.LeaderboardProvider;
import cn.quotidietium.balatro.api.service.StatsService;
import cn.quotidietium.balatro.api.service.WinCounter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 默认排行榜：基于 {@link StatsService} 内存排序。
 *
 * <p>排序口径：
 * <ul>
 *   <li>{@link #top}：单局记录，won 优先 → anteReached → 早完成者靠前。</li>
 *   <li>{@link #topAggregated}：按玩家聚合，bestAnte 降序 → winCount 降序。
 *       玩家名排序由显示层（Bukkit 层查名后）补充——服务层无 Bukkit 依赖。</li>
 * </ul>
 */
public final class MemoryLeaderboard implements LeaderboardProvider {

    private final StatsService stats;
    private WinCounter winCounter;

    public MemoryLeaderboard(StatsService stats) {
        this.stats = stats;
    }

    /** 注入通关计数器（用于聚合排行榜的 winCount 字段）。 */
    public void setWinCounter(WinCounter winCounter) {
        this.winCounter = winCounter;
    }

    // 单局排行排序：won 降序（通关优先）→ anteReached 降序 → epochMilli 升序（早完成优先）。
    // 注意：不能链式 .reversed().thenComparingInt().reversed()——第二个 reversed 会反转整个
    // comparator（含前段），导致 won 变成升序（失败在前）。用 thenComparing(子comparator) 确保各段独立。
    private static final Comparator<RunSummary> RANK = Comparator
            .comparing((RunSummary s) -> s.won() ? 1 : 0).reversed()
            .thenComparing(Comparator.comparingInt(RunSummary::anteReached).reversed())
            .thenComparingLong(RunSummary::epochMilli);

    @Override
    public List<RunSummary> top(int n) {
        if (n <= 0) return new ArrayList<>();
        List<RunSummary> all = new ArrayList<>(stats.all());
        all.sort(RANK);
        if (all.size() <= n) return all;
        return new ArrayList<>(all.subList(0, n));
    }

    @Override
    public RunSummary bestOf(UUID player) {
        RunSummary best = null;
        for (RunSummary s : stats.all()) {
            if (!s.playerId().equals(player)) continue;
            if (best == null || RANK.compare(s, best) < 0) best = s;
        }
        return best;
    }

    @Override
    public List<PlayerStat> topAggregated(int n) {
        if (n <= 0) return new ArrayList<>();
        // 聚合：每玩家的最高 anteReached
        Map<UUID, Integer> bestAnte = new HashMap<>();
        for (RunSummary s : stats.all()) {
            int cur = bestAnte.getOrDefault(s.playerId(), 0);
            if (s.anteReached() > cur) bestAnte.put(s.playerId(), s.anteReached());
        }
        Map<UUID, Integer> wins = winCounter instanceof FileWinCounter fwc
                ? fwc.allCounts() : new HashMap<>();
        List<PlayerStat> list = new ArrayList<>();
        for (var e : bestAnte.entrySet()) {
            UUID id = e.getKey();
            int wc = winCounter != null ? winCounter.count(id) : wins.getOrDefault(id, 0);
            list.add(new PlayerStat(id, null, e.getValue(), wc));
        }
        // 服务层排序：bestAnte 降序 → winCount 降序（玩家名排序留给显示层）。
        // 注意：不能链式 .reversed().thenComparingInt().reversed()——第二个 reversed 会反转整个
        // comparator（含前段）。用 thenComparing(子comparator) 确保各段独立降序。
        list.sort(Comparator
                .comparingInt(PlayerStat::bestAnte).reversed()
                .thenComparing(Comparator.comparingInt(PlayerStat::winCount).reversed()));
        if (list.size() <= n) return list;
        return new ArrayList<>(list.subList(0, n));
    }
}
