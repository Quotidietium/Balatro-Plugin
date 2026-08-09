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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * 全息牌桌交互。
 *
 * <p><b>命中检测</b>：不用 {@code World.rayTraceEntities}（Display/TextDisplay 实体命中盒极小、几乎无法命中），
 * 改由 {@link RoundBoard#hitTest} 做「视线射线 × 板面平面」相交，再按命中盒（与视觉尺寸解耦、可独立调大）判断落点。
 * 这与 doudizhu 的 {@code raycastHand/raycastButton}（手动射线-平面相交）思路一致，点击可靠。
 *
 * <p>右键主手 → 命中 → 按 action 串派发（选牌/出牌/弃牌/购买/选择/重掷/下一回合/跳过）。含 150ms 节流与点击音效。
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
        RoundBoard board = session.board();
        String act = board.hitTest(eye, dir, RANGE);
        if (act == null) return;

        event.setCancelled(true);
        dispatch(player, session, board, act);
    }

    private void dispatch(Player player, GameSession session, RoundBoard board, String act) {
        switch (act) {
            case "play" -> { board.playSelected(); click(player, 1.2f); }
            case "discard" -> { board.discardSelected(); click(player, 0.8f); }
            case "reroll" -> { session.reroll(); click(player, 1.0f); }
            case "next" -> { session.nextRound(); click(player, 1.2f); }
            case "voucher" -> { session.buyVoucher(); click(player, 1.0f); }
            case "skipack" -> { session.skipPack(); click(player, 0.8f); }
            default -> {
                if (act.startsWith("card:")) {
                    board.toggleSelect(Integer.parseInt(act.substring("card:".length())));
                    click(player, 1.6f);
                } else if (act.startsWith("shop:")) {
                    session.buyCard(Integer.parseInt(act.substring("shop:".length())));
                    click(player, 1.0f);
                } else if (act.startsWith("shoppack:")) {
                    session.buyPack(Integer.parseInt(act.substring("shoppack:".length())));
                    click(player, 1.0f);
                } else if (act.startsWith("pick:")) {
                    session.pickPack(Integer.parseInt(act.substring("pick:".length())));
                    click(player, 1.2f);
                }
            }
        }
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
