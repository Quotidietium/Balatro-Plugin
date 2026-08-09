package cn.quotidietium.balatro.session;

import cn.quotidietium.balatro.BalatroPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** 玩家离线时结束其会话（退出即弃，不存档）。 */
public final class SessionListener implements Listener {

    private final BalatroPlugin plugin;

    public SessionListener(BalatroPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.sessionManager().isActive(event.getPlayer())) {
            plugin.sessionManager().end(event.getPlayer());
        }
    }
}
