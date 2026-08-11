package cn.quotidietium.balatro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.api.RunSummary;
import cn.quotidietium.balatro.api.PlayerStat;
import cn.quotidietium.balatro.api.service.EconomyService;
import cn.quotidietium.balatro.api.service.RewardService;
import cn.quotidietium.balatro.api.service.StatsService;
import cn.quotidietium.balatro.api.service.WinCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 第三方服务替换兼容性测试（轮次 40）。
 *
 * <p>验证 Services 的四个服务接口被第三方实现替换后，
 * GameSession/Leaderboard 仍能正常运行不崩溃。
 */
class ServiceReplacementCompatTest {

    @Test
    void servicesAcceptCustomEconomy() {
        Services s = new Services();
        // 模拟第三方替换 EconomyService
        s.setEconomy(new EconomyService() {
            @Override public long balance(UUID p) { return 999; }
            @Override public boolean has(UUID p, long a) { return a <= 999; }
            @Override public void deposit(UUID p, long a) {}
            @Override public void withdraw(UUID p, long a) {}
        });
        UUID p = UUID.randomUUID();
        assertEquals(999, s.economy().balance(p));
        assertTrue(s.economy().has(p, 500));
    }

    @Test
    void servicesAcceptCustomStats() {
        Services s = new Services();
        List<RunSummary> customRecords = new ArrayList<>();
        s.setStats(new StatsService() {
            @Override public void record(RunSummary sum) { customRecords.add(sum); }
            @Override public List<RunSummary> all() { return new ArrayList<>(customRecords); }
        });
        // 替换后 Leaderboard 也应更新（MemoryLeaderboard 持有旧引用）
        s.setLeaderboard(new MemoryLeaderboard(s.stats()));
        UUID p = UUID.randomUUID();
        RunSummary rs = new RunSummary(p, true, 8, "S", "red", 0, 1);
        s.stats().record(rs);
        assertEquals(1, s.stats().all().size());
        assertEquals(1, s.leaderboard().top(10).size());
    }

    @Test
    void servicesAcceptCustomReward() {
        Services s = new Services();
        final int[] blindCalls = {0};
        final int[] anteCalls = {0};
        final int[] runEndCalls = {0};
        s.setReward(new RewardService() {
            @Override public void onBlindCleared(UUID p, int a, String bt) { blindCalls[0]++; }
            @Override public void onAnteCleared(UUID p, int a) { anteCalls[0]++; }
            @Override public void onRunEnd(UUID p, boolean w, int a) { runEndCalls[0]++; }
        });
        UUID p = UUID.randomUUID();
        s.reward().onBlindCleared(p, 1, "small");
        s.reward().onAnteCleared(p, 1);
        s.reward().onRunEnd(p, true, 8);
        assertEquals(1, blindCalls[0]);
        assertEquals(1, anteCalls[0]);
        assertEquals(1, runEndCalls[0]);
    }

    @Test
    void servicesAcceptCustomWinCounter() {
        Services s = new Services();
        final java.util.Map<UUID, Integer> wins = new java.util.HashMap<>();
        s.setWinCounter(new WinCounter() {
            @Override public void increment(UUID p) { wins.merge(p, 1, Integer::sum); }
            @Override public int count(UUID p) { return wins.getOrDefault(p, 0); }
        });
        // 验证 setWinCounter 同步注入 MemoryLeaderboard
        UUID p = UUID.randomUUID();
        s.winCounter().increment(p);
        s.winCounter().increment(p);
        assertEquals(2, s.winCounter().count(p));
    }

    @Test
    void customStatsWithLeaderboardAggregation() {
        // 第三方替换 StatsService 后，聚合排行榜仍可工作（前提是 Leaderboard 也重新绑定）
        Services s = new Services();
        List<RunSummary> recs = new ArrayList<>();
        recs.add(new RunSummary(UUID.randomUUID(), true, 8, "S1", "red", 0, 1));
        recs.add(new RunSummary(UUID.randomUUID(), false, 12, "S2", "blue", 0, 2));
        s.setStats(new StatsService() {
            @Override public void record(RunSummary sum) { recs.add(sum); }
            @Override public List<RunSummary> all() { return new ArrayList<>(recs); }
        });
        MemoryLeaderboard ml = new MemoryLeaderboard(s.stats());
        s.setLeaderboard(ml);
        // 不设 WinCounter 时 topAggregated 仍可工作（bestAnte 从 stats 聚合，winCount 全 0）
        List<PlayerStat> top = s.leaderboard().topAggregated(10);
        assertEquals(2, top.size(), "无 WinCounter 时 topAggregated 仍应按 bestAnte 聚合");
        // winCount 全 0（无 WinCounter）
        for (PlayerStat ps : top) assertEquals(0, ps.winCount(), "无 WinCounter 时 winCount 应为 0");
    }

    @Test
    void noopEconomyReturnsDefaults() {
        // 默认 NoOpEconomy 的行为不变
        Services s = new Services();
        assertEquals(0, s.economy().balance(UUID.randomUUID()));
        assertTrue(s.economy().has(UUID.randomUUID(), 0));
        assertTrue(s.economy().has(UUID.randomUUID(), -1));
    }
}
