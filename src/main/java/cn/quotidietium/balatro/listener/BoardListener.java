package cn.quotidietium.balatro.listener;

import cn.quotidietium.balatro.BalatroPlugin;
import cn.quotidietium.balatro.render.RoundBoard;
import cn.quotidietium.balatro.session.GameSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * 全息牌桌交互：右键时用 {@code World.rayTraceEntities} 命中最近的板面实体，
 * 按 scoreboard tag 派发：选牌 / 出牌 / 弃牌。含 150ms 节流与点击音效。
 */
public final class BoardListener implements Listener {

    private static final double RANGE = 6.0;
    private static final long THROTTLE_MS = 150;

    private final BalatroPlugin plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public BoardListener(BalatroPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        GameSession session = plugin.sessionManager().get(player);
        if (session == null || session.board() == null) return;
        if (!throttle(player)) return;

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTraceEntities(eye, dir, RANGE, this::isBoardEntity);
        if (hit == null || hit.getHitEntity() == null) return;

        event.setCancelled(true);
        Entity entity = hit.getHitEntity();
        RoundBoard board = session.board();
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith("balatro_card_")) {
                try {
                    int cardId = Integer.parseInt(tag.substring("balatro_card_".length()));
                    board.toggleSelect(cardId);
                    click(player, 1.6f);
                } catch (NumberFormatException ignored) {
                }
                return;
            }
            if (tag.equals("balatro_act_play")) {
                board.playSelected();
                click(player, 1.2f);
                return;
            }
            if (tag.equals("balatro_act_discard")) {
                board.discardSelected();
                click(player, 0.8f);
                return;
            }
        }
    }

    private boolean isBoardEntity(Entity e) {
        for (String t : e.getScoreboardTags()) {
            if (t.startsWith("balatro_card_") || t.equals("balatro_act_play") || t.equals("balatro_act_discard")) {
                return true;
            }
        }
        return false;
    }

    private void click(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.6f, pitch);
    }

    private boolean throttle(Player player) {
        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < THROTTLE_MS) return false;
        lastClick.put(player.getUniqueId(), now);
        return true;
    }
}
