package cn.quotidietium.balatro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.api.PlayerStat;
import cn.quotidietium.balatro.api.RunSummary;
import cn.quotidietium.balatro.api.service.WinCounter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 聚合排行榜（topAggregated）回归：按玩家聚合 bestAnte + winCount，
 * 排序 bestAnte 降序 → winCount 降序（玩家名排序在显示层）。
 */
class LeaderboardAggregationTest {

    private static final class MemStats implements cn.quotidietium.balatro.api.service.StatsService {
        final java.util.List<RunSummary> records = new java.util.ArrayList<>();
        @Override public void record(RunSummary s) { records.add(s); }
        @Override public List<RunSummary> all() { return new java.util.ArrayList<>(records); }
    }

    private static final class MemWin implements WinCounter {
        final java.util.Map<UUID, Integer> counts = new java.util.HashMap<>();
        @Override public void increment(UUID p) { counts.merge(p, 1, Integer::sum); }
        @Override public int count(UUID p) { return counts.getOrDefault(p, 0); }
    }

    @Test
    void aggregatesBestAntePerPlayer() {
        MemStats stats = new MemStats();
        UUID a = UUID.randomUUID(); // 玩家 A
        stats.record(new RunSummary(a, true, 8, "S1", "red", 0, 100)); // 通关 8
        stats.record(new RunSummary(a, false, 12, "S2", "red", 0, 200)); // 无尽死 12
        MemoryLeaderboard lb = new MemoryLeaderboard(stats);
        MemWin wc = new MemWin();
        wc.counts.put(a, 1); // A 通关 1 次
        lb.setWinCounter(wc);

        var top = lb.topAggregated(10);
        assertEquals(1, top.size(), "应聚合为 1 个玩家");
        assertEquals(12, top.get(0).bestAnte(), "bestAnte 应为最高 ante（含无尽）");
        assertEquals(1, top.get(0).winCount(), "winCount 应为通关计数");
    }

    @Test
    void sortByBestAnteThenWinCount() {
        MemStats stats = new MemStats();
        UUID a = UUID.randomUUID(); // A: ante 10, win 1
        UUID b = UUID.randomUUID(); // B: ante 10, win 3
        UUID c = UUID.randomUUID(); // C: ante 12, win 0
        stats.record(new RunSummary(a, true, 10, "s", "red", 0, 1));
        stats.record(new RunSummary(b, true, 10, "s", "red", 0, 1));
        stats.record(new RunSummary(c, false, 12, "s", "red", 0, 1));
        MemoryLeaderboard lb = new MemoryLeaderboard(stats);
        MemWin wc = new MemWin();
        wc.counts.put(a, 1);
        wc.counts.put(b, 3);
        wc.counts.put(c, 0);
        lb.setWinCounter(wc);

        var top = lb.topAggregated(10);
        // 排序：C(ante12) → B(ante10,win3) → A(ante10,win1)
        assertEquals(c, top.get(0).playerId(), "第一应为 ante 最高的 C");
        assertEquals(b, top.get(1).playerId(), "第二应为 ante 同但 win 更多的 B");
        assertEquals(a, top.get(2).playerId(), "第三应为 A");
    }

    @Test
    void endlessDeathRecordedAndContributesToBestAnte() {
        // 无尽死亡的记录不被禁止——其 ante 贡献给 bestAnte（排行榜显示「无尽N」而非「失败」）
        MemStats stats = new MemStats();
        UUID p = UUID.randomUUID();
        stats.record(new RunSummary(p, true, 8, "s", "red", 0, 1));  // 通关
        stats.record(new RunSummary(p, false, 15, "s", "red", 0, 2)); // 无尽死 ante15
        MemoryLeaderboard lb = new MemoryLeaderboard(stats);
        lb.setWinCounter(new MemWin());

        var top = lb.topAggregated(10);
        assertEquals(1, top.size());
        assertEquals(15, top.get(0).bestAnte(), "无尽死亡 ante 15 应为 bestAnte");
    }

    @Test
    void emptyAndNonPositiveN() {
        MemoryLeaderboard lb = new MemoryLeaderboard(new MemStats());
        lb.setWinCounter(new MemWin());
        assertTrue(lb.topAggregated(0).isEmpty(), "n=0 应返回空");
        assertTrue(lb.topAggregated(-1).isEmpty(), "n<0 应返回空");
        assertTrue(lb.topAggregated(10).isEmpty(), "无记录应返回空");
    }
}
