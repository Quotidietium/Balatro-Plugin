package cn.quotidietium.balatro;

import cn.quotidietium.balatro.api.event.BalatroAnteClearEvent;
import cn.quotidietium.balatro.api.event.BalatroBlindResultEvent;
import cn.quotidietium.balatro.api.event.BalatroHandScoreEvent;
import cn.quotidietium.balatro.api.event.BalatroRunEndEvent;
import cn.quotidietium.balatro.api.event.BalatroRunStartEvent;
import cn.quotidietium.balatro.command.BalatroCommand;
import cn.quotidietium.balatro.service.Services;
import cn.quotidietium.balatro.session.SessionListener;
import cn.quotidietium.balatro.session.SessionManager;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 小丑牌 (Balatro) 插件主类。
 *
 * <p>分层（见 {@code note/项目计划书.md}）：
 * <ul>
 *   <li>{@code engine/} 纯逻辑（移植 balatro 网页，零 Bukkit 依赖，可单测）；</li>
 *   <li>{@code api/} 预留扩展面（自定义事件 + 服务接口）；</li>
 *   <li>{@code session/} 每玩家会话 + 事件桥；{@code render/} 全息渲染（S8）；{@code listener/} 射线交互（S9）。</li>
 * </ul>
 */
public final class BalatroPlugin extends JavaPlugin {

    private Services services;
    private SessionManager sessionManager;
    private cn.quotidietium.balatro.gui.GuiManager guiManager;

    @Override
    public void onEnable() {
        services = new Services();
        // 持久化统计（文件）+ 基于其的排行榜
        services.setStats(new cn.quotidietium.balatro.service.FileStats(
                getDataFolder().toPath().resolve("stats.txt"), getLogger()));
        services.setLeaderboard(new cn.quotidietium.balatro.service.MemoryLeaderboard(services.stats()));
        // 通关计数器（独立持久化，供聚合排行榜的 winCount）
        services.setWinCounter(new cn.quotidietium.balatro.service.FileWinCounter(
                getDataFolder().toPath().resolve("wins.txt"), getLogger()));
        if (getServer().getPluginManager().isPluginEnabled("Vault")) {
            cn.quotidietium.balatro.service.VaultEconomy ve = new cn.quotidietium.balatro.service.VaultEconomy();
            if (ve.available()) {
                services.setEconomy(ve);
                getLogger().info("已接入 Vault 经济。");
            }
        }
        sessionManager = new SessionManager(this);
        guiManager = new cn.quotidietium.balatro.gui.GuiManager(this);

        BalatroCommand command = new BalatroCommand(this);
        getServer().getPluginManager().registerEvents(new SessionListener(this), this);
        getServer().getPluginManager().registerEvents(new cn.quotidietium.balatro.listener.BoardListener(this), this);
        getServer().getPluginManager().registerEvents(new cn.quotidietium.balatro.listener.BoardMoveListener(this), this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        var cmd = getCommand("balatro");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        sweepStaleBoards();

        getLogger().info("Balatro v" + getPluginMeta().getVersion() + " enabled.");
    }

    /**
     * 启动时清扫所有世界中带 {@code balatro_board} 标签的残留实体（/reload、上次运行
     * 异常泄漏等场景）。实体本身非持久（重启即消失），此处是防御性兜底，对齐 doudizhu 范式。
     */
    private void sweepStaleBoards() {
        int removed = 0;
        try {
            for (org.bukkit.World world : getServer().getWorlds()) {
                for (org.bukkit.entity.Entity e : world.getEntities()) {
                    if (e.getScoreboardTags().contains("balatro_board")) {
                        e.remove();
                        removed++;
                    }
                }
            }
        } catch (RuntimeException ex) {
            getLogger().warning("残留牌桌实体清扫失败：" + ex);
            return;
        }
        if (removed > 0) {
            getLogger().info("已清扫 " + removed + " 个残留牌桌实体。");
        }
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            // 先关菜单界面再收会话：避免关停后玩家背包停留在插件菜单上
            guiManager.closeAll();
        }
        if (sessionManager != null) {
            sessionManager.shutdownAll();
        }
        getLogger().info("Balatro disabled.");
    }

    public Services services() {
        return services;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    /** 开局向导 GUI 管理器（/balatro gui）。 */
    public cn.quotidietium.balatro.gui.GuiManager guiManager() {
        return guiManager;
    }

    // ================= 事件桥（由 GameSession 在主线程调用） =================

    /** RunStart 事件（可取消）。 */
    public RunStartDecision fireRunStart(UUID player, String seed, String deckKey, int stakeIdx) {
        BalatroRunStartEvent ev = new BalatroRunStartEvent(player, seed, deckKey, stakeIdx);
        getServer().getPluginManager().callEvent(ev);
        return new RunStartDecision(ev.isCancelled());
    }

    public void fireHandScore(UUID player, String handType, long score, long roundScore, long target, int handsLeft) {
        getServer().getPluginManager().callEvent(
                new BalatroHandScoreEvent(player, handType, score, roundScore, target, handsLeft));
    }

    public void fireBlindResult(UUID player, int ante, String blindType, long target, long score, boolean cleared) {
        getServer().getPluginManager().callEvent(
                new BalatroBlindResultEvent(player, ante, blindType, target, score, cleared));
    }

    public void fireAnteClear(UUID player, int ante) {
        getServer().getPluginManager().callEvent(new BalatroAnteClearEvent(player, ante));
    }

    public void fireRunEnd(UUID player, boolean won, int anteReached, String seed, String deckKey, int stakeIdx) {
        getServer().getPluginManager().callEvent(
                new BalatroRunEndEvent(player, won, anteReached, seed, deckKey, stakeIdx));
    }

    /** RunStart 的取消决策。 */
    public record RunStartDecision(boolean cancelled) {
    }
}
