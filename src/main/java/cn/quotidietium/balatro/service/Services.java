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
 *
 * <p><b>线程可见性</b>：所有服务字段为 {@code volatile}——第三方插件可在运行期替换服务
 * （主线程外的异步上下文），{@code volatile} 保证替换后其他线程立即可见，不出现脏读。
 * 替换 null 被拒绝（保留原实现并记日志），避免后续调用 NPE。
 */
public final class Services {

    private volatile EconomyService economy;
    private volatile StatsService stats;
    private volatile LeaderboardProvider leaderboard;
    private volatile RewardService reward;
    private volatile WinCounter winCounter;

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

    /**
     * 替换经济服务；null 被拒绝（保留原实现），避免后续调用 NPE。
     *
     * @param economy 新实现（不可为 null）
     */
    public void setEconomy(EconomyService economy) {
        if (economy == null) return;
        this.economy = economy;
    }

    /**
     * 替换统计服务；null 被拒绝。同步重绑 MemoryLeaderboard 的统计源，
     * 避免替换后排行榜仍读旧实现（静默不一致）。
     *
     * @param stats 新实现（不可为 null）
     */
    public void setStats(StatsService stats) {
        if (stats == null) return;
        this.stats = stats;
        if (leaderboard instanceof MemoryLeaderboard ml) ml.setStats(stats);
    }

    /**
     * 替换排行榜；null 被拒绝。替换时把当前统计源与通关计数器一并注入新 MemoryLeaderboard，
     * 保持三向（stats/winCounter/leaderboard）同步。
     *
     * @param leaderboard 新实现（不可为 null）
     */
    public void setLeaderboard(LeaderboardProvider leaderboard) {
        if (leaderboard == null) return;
        this.leaderboard = leaderboard;
        if (leaderboard instanceof MemoryLeaderboard ml) {
            ml.setStats(stats);
            if (winCounter != null) ml.setWinCounter(winCounter);
        }
    }

    /**
     * 替换奖励服务；null 被拒绝。
     *
     * @param reward 新实现（不可为 null）
     */
    public void setReward(RewardService reward) {
        if (reward == null) return;
        this.reward = reward;
    }

    /**
     * 替换通关计数器；null 被拒绝。同步注入 MemoryLeaderboard 以便聚合排行使用。
     *
     * @param winCounter 新实现（不可为 null）
     */
    public void setWinCounter(WinCounter winCounter) {
        if (winCounter == null) return;
        this.winCounter = winCounter;
        if (leaderboard instanceof MemoryLeaderboard ml) ml.setWinCounter(winCounter);
    }
}
