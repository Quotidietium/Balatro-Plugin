package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.service.EconomyService;
import cn.quotidietium.balatro.api.service.LeaderboardProvider;
import cn.quotidietium.balatro.api.service.RewardService;
import cn.quotidietium.balatro.api.service.StatsService;

/**
 * 服务注册表：集中持有四个扩展服务的实例，默认装配内存/空操作实现，运行期可被替换。
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

    public Services() {
        StatsService memStats = new MemoryStats();
        this.stats = memStats;
        this.economy = new NoOpEconomy();
        this.leaderboard = new MemoryLeaderboard(memStats);
        this.reward = new NoOpReward();
    }

    public EconomyService economy() { return economy; }
    public StatsService stats() { return stats; }
    public LeaderboardProvider leaderboard() { return leaderboard; }
    public RewardService reward() { return reward; }

    public void setEconomy(EconomyService economy) { this.economy = economy; }
    public void setStats(StatsService stats) { this.stats = stats; }
    public void setLeaderboard(LeaderboardProvider leaderboard) { this.leaderboard = leaderboard; }
    public void setReward(RewardService reward) { this.reward = reward; }
}
