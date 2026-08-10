package cn.quotidietium.balatro.listener;

import cn.quotidietium.balatro.BalatroPlugin;
import cn.quotidietium.balatro.render.RoundBoard;
import cn.quotidietium.balatro.session.GameSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
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
 * 全息牌桌交互（统一规则）。
 *
 * <p>命中机制：用 {@link Interaction} 实体承载点击——它有 width×height 原生命中盒，
 * 玩家右键它确定触发 {@link PlayerInteractEntityEvent}。动作编进 scoreboard tag（{@code balatro_i_<action>}）。
 *
 * <p><b>统一交互规则</b>：
 * <ul>
 *   <li><b>Shift + 右键</b> = 查看该卡简介（发到聊天框）；按钮等无简介的则执行操作。</li>
 *   <li><b>直接右键</b> = 使用/操作（选中手牌 / 购买商品 / 选择补充包卡 / 使用消耗品 / 出售小丑 / 出牌弃牌…）。</li>
 * </ul>
 * 含每玩家 150ms 节流与点击音效。
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
        dispatch(player, session, session.board(), action, player.isSneaking());
    }

    private void dispatch(Player player, GameSession session, RoundBoard board, String act, boolean sneak) {
        // Shift + 右键：查看简介（若有），不执行操作
        if (sneak) {
            Component info = board.infoFor(session.state(), act);
            if (info != null) {
                player.sendMessage(info);
                click(player, 1.6f);
                return;
            }
            // 按钮等无简介：继续执行操作
        }

        // 直接右键（或无简介的 Shift 右键）：执行使用/操作
        switch (act) {
            case "play" -> { board.playSelected(); click(player, 1.2f); }
            case "discard" -> { board.discardSelected(); click(player, 0.8f); }
            case "reroll" -> { session.reroll(); click(player, 1.0f); }
            case "next" -> { session.nextRound(); click(player, 1.2f); }
            case "go" -> { session.chooseBlind(false); click(player, 1.2f); }
            case "skip" -> { session.chooseBlind(true); click(player, 0.8f); }
            case "skipack" -> { session.skipPack(); click(player, 0.8f); }
            case "voucher" -> { session.buyVoucher(); click(player, 1.0f); }
            default -> {
                if (act.startsWith("card:")) {
                    boolean changed = board.toggleSelect(Integer.parseInt(act.substring("card:".length())));
                    if (changed) click(player, 1.6f); // 超限拒绝时不播音效（不作出反应）
                } else if (act.startsWith("shop:")) {
                    session.buyCard(Integer.parseInt(act.substring("shop:".length())));
                    click(player, 1.0f);
                } else if (act.startsWith("shoppack:")) {
                    session.buyPack(Integer.parseInt(act.substring("shoppack:".length())));
                    click(player, 1.0f);
                } else if (act.startsWith("pick:")) {
                    session.pickPack(Integer.parseInt(act.substring("pick:".length())));
                    click(player, 1.2f);
                } else if (act.startsWith("joker:")) {
                    session.sellJoker(Integer.parseInt(act.substring("joker:".length())));
                    click(player, 0.8f);
                } else if (act.startsWith("cons:")) {
                    session.useConsumable(Integer.parseInt(act.substring("cons:".length())), null);
                    click(player, 1.0f);
                }
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
