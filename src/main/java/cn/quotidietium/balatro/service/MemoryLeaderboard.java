package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.RunSummary;
import cn.quotidietium.balatro.api.service.LeaderboardProvider;
import cn.quotidietium.balatro.api.service.StatsService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 默认排行榜：基于 {@link StatsService} 内存排序（won 优先 → anteReached → 早完成者靠前）。 */
public final class MemoryLeaderboard implements LeaderboardProvider {

    private final StatsService stats;

    public MemoryLeaderboard(StatsService stats) {
        this.stats = stats;
    }

    private static final Comparator<RunSummary> RANK = Comparator
            .comparing((RunSummary s) -> s.won() ? 1 : 0).reversed()
            .thenComparingInt(RunSummary::anteReached).reversed()
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
}
