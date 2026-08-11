package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.service.EconomyService;
import cn.quotidietium.balatro.api.service.LeaderboardProvider;
import cn.quotidietium.balatro.api.service.RewardService;
import cn.quotidietium.balatro.api.service.StatsService;
import cn.quotidietium.balatro.api.service.WinCounter;

/**
 * 服务注册表：集中持有扩展服务的实例，默认装配内存/空操作实现，运行期可被替换。
 *
 * <p>替换方式（其他插件或配置）：
 * <pre>{@code
 *   balatroPlugin.services().setEconomy(new VaultEconomy());
 *   balatroPlugin.services().setStats(new SqliteStats());
 * }</pre>
 */
public final class Services {

    private EconomyService economy;
    private StatsService stats;
    private LeaderboardProvider leaderboard;
    private RewardService reward;
    private WinCounter winCounter;

    public Services() {
        StatsService memStats = new MemoryStats();
        this.stats = memStats;
        this.economy = new NoOpEconomy();
        MemoryLeaderboard ml = new MemoryLeaderboard(memStats);
        this.leaderboard = ml;
        this.reward = new NoOpReward();
    }

    public EconomyService economy() { return economy; }
    public StatsService stats() { return stats; }
    public LeaderboardProvider leaderboard() { return leaderboard; }
    public RewardService reward() { return reward; }
    public WinCounter winCounter() { return winCounter; }

    public void setEconomy(EconomyService economy) { this.economy = economy; }
    public void setStats(StatsService stats) {
        this.stats = stats;
        // 若排行榜是 MemoryLeaderboard，同步重绑统计源——否则排行榜仍读旧实现，
        // 替换后新记录不再反映到排名（静默不一致）。与 setWinCounter 同一约定。
        if (leaderboard instanceof MemoryLeaderboard ml) ml.setStats(stats);
    }
    public void setLeaderboard(LeaderboardProvider leaderboard) {
        this.leaderboard = leaderboard;
        // 与 setStats/setWinCounter 同一约定：替换排行榜时把当前统计源与通关计数器
        // 一并注入——否则新 MemoryLeaderboard 仍持构造时的旧 stats、winCount 恒为 0
        // （topAggregated 静默不一致）。三向（stats/winCounter/leaderboard）任一替换都保持同步。
        if (leaderboard instanceof MemoryLeaderboard ml) {
            ml.setStats(stats);
            if (winCounter != null) ml.setWinCounter(winCounter);
        }
    }
    public void setReward(RewardService reward) { this.reward = reward; }
    public void setWinCounter(WinCounter winCounter) {
        this.winCounter = winCounter;
        // 若排行榜是 MemoryLeaderboard，同步注入以便聚合排行使用
        if (leaderboard instanceof MemoryLeaderboard ml) ml.setWinCounter(winCounter);
    }
}
