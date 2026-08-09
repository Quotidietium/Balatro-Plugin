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
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) return null;
        GameSession session = new GameSession(plugin, player, deckKey, stakeIdx, seed);
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

    /** 关闭全部（onDisable）。 */
    public void shutdownAll() {
        sessions.clear();
    }
}
