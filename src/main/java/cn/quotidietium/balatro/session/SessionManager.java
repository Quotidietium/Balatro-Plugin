package cn.quotidietium.balatro.session;

import cn.quotidietium.balatro.BalatroPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * 会话管理器：每玩家至多一局（{@code Map<UUID, GameSession>}）。
 * 退出即弃：结束会话即销毁 RunState（不存档），后续由渲染层负责回收实体。
 */
public final class SessionManager {

    private final BalatroPlugin plugin;
    private final Map<UUID, GameSession> sessions = new HashMap<>();

    public SessionManager(BalatroPlugin plugin) {
        this.plugin = plugin;
    }

    /** 开始一局；若已有进行中的局则返回 null（由调用方提示）。 */
    public GameSession start(Player player, String deckKey, int stakeIdx, String seed) {
        return start(player, deckKey, stakeIdx, seed, null);
    }

    /** 开始一局（带挑战模式）；若已有进行中的局则返回 null。 */
    public GameSession start(Player player, String deckKey, int stakeIdx, String seed, String challenge) {
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) return null;
        // 兜底校验（命令层已先行校验并提示）：种子来自客户端，不接受超长/非法字符集
        if (seed != null && !cn.quotidietium.balatro.engine.Rng.isValidSeed(seed)) {
            plugin.getLogger().warning("拒绝非法种子输入（玩家 " + player.getName() + "）");
            return null;
        }
        GameSession session = new GameSession(plugin, player, deckKey, stakeIdx, seed, challenge);
        if (!session.start()) {
            // RunStart 被取消
            return null;
        }
        sessions.put(id, session);
        return session;
    }

    public GameSession get(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** 结束会话（退出即弃）。 */
    public void end(Player player) {
        GameSession s = sessions.remove(player.getUniqueId());
        if (s != null) {
            s.despawnBoard();
        }
    }

    /** 关闭全部（onDisable / reload）：逐一销毁牌桌实体，避免世界内残留全息。 */
    public void shutdownAll() {
        for (GameSession s : sessions.values()) {
            try {
                s.despawnBoard();
            } catch (RuntimeException ignored) {
                // 关停阶段实体可能已随世界卸载失效，忽略个别清理失败
            }
        }
        sessions.clear();
    }
}
