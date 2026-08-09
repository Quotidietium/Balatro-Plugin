package cn.quotidietium.balatro.listener;

import cn.quotidietium.balatro.BalatroPlugin;
import cn.quotidietium.balatro.render.RoundBoard;
import cn.quotidietium.balatro.session.GameSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

/**
 * 全息牌桌交互。
 *
 * <p><b>命中机制</b>：用 {@link Interaction} 实体承载点击——它是 MC 1.19.4+ 专为"可点击全息"设计的实体，
 * 有明确的 width×height 命中区，玩家右键它**确定触发** {@link PlayerInteractEntityEvent}。
 * 这不依赖 {@code TextDisplay} 那套极小且不可靠的命中盒，也不依赖手动射线计算。
 * 每个可点击元素（手牌/按钮/商品/补充包）由 {@link RoundBoard} 放一个 Interaction 命中盒，
 * 动作编进 scoreboard tag（{@code balatro_i_<action>}），本监听器解析后派发。
 *
 * <p>含每玩家 150ms 节流（防同一交互双触发）与点击音效。
 */
public final class BoardListener implements Listener {

    private static final String TAG_PREFIX = "balatro_i_";
    private static final long THROTTLE_MS = 150;

    private final BalatroPlugin plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public BoardListener(BalatroPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction)) return;

        String action = null;
        for (String t : entity.getScoreboardTags()) {
            if (t.startsWith(TAG_PREFIX)) {
                action = t.substring(TAG_PREFIX.length());
                break;
            }
        }
        if (action == null) return;

        Player player = event.getPlayer();
        GameSession session = plugin.sessionManager().get(player);
        if (session == null || session.board() == null) return;
        if (!throttle(player)) return;

        event.setCancelled(true);
        dispatch(player, session, session.board(), action);
    }

    private void dispatch(Player player, GameSession session, RoundBoard board, String act) {
        switch (act) {
            case "play" -> { board.playSelected(); click(player, 1.2f); }
            case "discard" -> { board.discardSelected(); click(player, 0.8f); }
            case "reroll" -> { session.reroll(); click(player, 1.0f); }
            case "next" -> { session.nextRound(); click(player, 1.2f); }
            case "skipack" -> { session.skipPack(); click(player, 0.8f); }
            case "voucher" -> {
                // 先取简介再购买（购买后状态变化）
                net.kyori.adventure.text.Component info = board.infoFor(session.state(), act);
                session.buyVoucher();
                click(player, 1.0f);
                if (info != null) player.sendMessage(info);
            }
            default -> {
                // 功能卡/商品：先取简介（基于当前状态），再执行操作，然后发送简介
                net.kyori.adventure.text.Component info = board.infoFor(session.state(), act);
                boolean acted = true;
                if (act.startsWith("card:")) {
                    board.toggleSelect(Integer.parseInt(act.substring("card:".length())));
                    click(player, 1.6f);
                    acted = false; // 手牌扑克牌不发简介
                } else if (act.startsWith("shop:")) {
                    session.buyCard(Integer.parseInt(act.substring("shop:".length())));
                    click(player, 1.0f);
                } else if (act.startsWith("shoppack:")) {
                    session.buyPack(Integer.parseInt(act.substring("shoppack:".length())));
                    click(player, 1.0f);
                } else if (act.startsWith("pick:")) {
                    session.pickPack(Integer.parseInt(act.substring("pick:".length())));
                    click(player, 1.2f);
                } else if (act.startsWith("joker:") || act.startsWith("cons:")) {
                    click(player, 1.6f);
                } else {
                    acted = false;
                }
                if (acted && info != null) player.sendMessage(info);
            }
        }
    }

    private void click(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 1.0f, pitch);
    }

    private boolean throttle(Player player) {
        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < THROTTLE_MS) return false;
        lastClick.put(player.getUniqueId(), now);
        return true;
    }
}
