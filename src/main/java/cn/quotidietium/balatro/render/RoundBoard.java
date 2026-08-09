package cn.quotidietium.balatro.render;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.session.GameSession;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

/**
 * 单个玩家回合的全息牌桌（0.1.0：TextDisplay 文字牌面）。
 *
 * <p>定位：以玩家眼部位置 + 朝向构造"前向基"，板面悬浮于玩家眼前；
 * CENTER 朝向使每个实体始终正对玩家。私有可见：仅本人可见。
 * 命中检测由 {@code BoardListener} 用 {@code World.rayTraceEntities} 完成，
 * 实体 scoreboard tag 编码动作：{@code balatro_card_<id>} / {@code balatro_act_play} / {@code balatro_act_discard}。
 */
public final class RoundBoard {

    private static final double FORWARD = 2.6;
    private static final double CARD_SPACING = 0.72;
    private static final Color BG_NORMAL = Color.fromARGB(210, 28, 28, 40);
    private static final Color BG_SELECTED = Color.fromARGB(230, 60, 165, 90);
    private static final Color BG_BUTTON = Color.fromARGB(220, 50, 70, 120);
    private static final Color BG_STATUS = Color.fromARGB(180, 0, 0, 0);
    private static final TextColor C_RED = TextColor.color(225, 70, 70);
    private static final TextColor C_DARK = TextColor.color(235, 235, 245);

    private final GameSession session;
    private final Vector origin;
    private final Vector forward;
    private final Vector right; // 玩家右手方向（板面横向）

    private final List<TextDisplay> owned = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();

    public RoundBoard(GameSession session) {
        this.session = session;
        Location eye = session.player().getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        this.origin = eye.toVector().add(dir.clone().multiply(FORWARD));
        this.forward = dir;
        Vector up = new Vector(0, 1, 0);
        this.right = dir.clone().getCrossProduct(up).normalize();
    }

    /** 初次生成。 */
    public void spawn(RunState state) {
        render(state);
    }

    /** 状态变更后刷新（重建手牌/状态/按钮文本）。 */
    public void update(RunState state) {
        render(state);
    }

    private void render(RunState state) {
        clear();
        switch (state.phase) {
            case SHOP -> renderShop(state);
            case PACK -> renderPack(state);
            default -> renderRound(state);
        }
    }

    private void renderRound(RunState state) {
        // 状态栏
        String blind = state.blindType == null ? "-" : state.blindType.key;
        String boss = state.blindType == Data.BlindType.BOSS && !state.bossQueue.isEmpty()
                ? "（" + Data.Boss.byKey(state.bossQueue.get(0)).name + "）" : "";
        Component status = Component.text()
                .append(Component.text("底注 " + state.ante + "  " + blindName(blind) + boss, NamedTextColor.GOLD)).appendNewline()
                .append(Component.text("分数 " + state.roundScore + " / " + state.blindTarget, NamedTextColor.WHITE)).appendNewline()
                .append(Component.text("出牌 " + state.handsLeft + "  弃牌 " + state.discardsLeft + "  $" + state.money
                        + "  选中 " + selected.size(), NamedTextColor.YELLOW))
                .build();
        owned.add(Holo.text(session.plugin(), session.player(), at(0, 2.3), "balatro_status", status, BG_STATUS, true));

        // 小丑行
        if (!state.jokers.isEmpty()) {
            int j = state.jokers.size();
            for (int i = 0; i < j; i++) {
                JokerInstance ji = state.jokers.get(i);
                double x = (i - (j - 1) / 2.0) * 0.8;
                Component c = Component.text("🃏" + ji.def.displayName(), TextColor.color(255, 220, 120));
                owned.add(Holo.text(session.plugin(), session.player(), at(x, 1.55), "balatro_joker_" + i, c, BG_NORMAL, true));
            }
        }

        // 手牌
        int n = state.hand.size();
        for (int i = 0; i < n; i++) {
            Card card = state.hand.get(i);
            double x = (i - (n - 1) / 2.0) * CARD_SPACING;
            boolean sel = selected.contains(card.id());
            Component face = cardFace(card, sel);
            owned.add(Holo.text(session.plugin(), session.player(), at(x, 0.5), "balatro_card_" + card.id(), face, sel ? BG_SELECTED : BG_NORMAL, true));
        }

        // 按钮
        int selN = selected.size();
        Component play = Component.text("▶ 出牌" + (selN > 0 ? "(" + selN + ")" : ""), NamedTextColor.GREEN);
        Component disc = Component.text("✗ 弃牌" + (selN > 0 ? "(" + selN + ")" : ""), NamedTextColor.RED);
        owned.add(Holo.text(session.plugin(), session.player(), at(-1.0, -0.7), "balatro_act_play", play, BG_BUTTON, true));
        owned.add(Holo.text(session.plugin(), session.player(), at(1.0, -0.7), "balatro_act_discard", disc, BG_BUTTON, true));
    }

    private Component cardFace(Card card, boolean selected) {
        if (card.isStone()) {
            return Component.text("石头", NamedTextColor.GRAY);
        }
        Data.Suit s = Data.Suit.byIndex(card.suit());
        TextColor col = selected ? NamedTextColor.WHITE : (s.isRed() ? C_RED : C_DARK);
        StringBuilder sb = new StringBuilder();
        sb.append(s.symbol).append(Data.rankName(card.rank()));
        if (card.enh() != null) sb.append("\n§e").append(shortEnh(card.enh()));
        return Component.text(sb.toString(), col);
    }

    private static String shortEnh(Data.Enhancement e) {
        return switch (e) {
            case BONUS -> "+筹";
            case MULT -> "+倍";
            case WILD -> "万";
            case GLASS -> "玻";
            case STEEL -> "钢";
            case STONE -> "石";
            case GOLD -> "金";
            case LUCKY -> "幸";
        };
    }

    private static String blindName(String key) {
        return switch (key) {
            case "small" -> "小盲注";
            case "big" -> "大盲注";
            case "boss" -> "Boss 盲注";
            default -> key;
        };
    }

    /** 板面上某 (横向, 垂直) 偏移处的世界坐标。 */
    private Location at(double rightOffset, double upOffset) {
        Vector v = origin.clone()
                .add(right.clone().multiply(rightOffset))
                .add(new Vector(0, upOffset, 0));
        return new Location(session.player().getWorld(), v.getX(), v.getY(), v.getZ());
    }

    private void renderShop(RunState state) {
        var shop = state.shop;
        Component status = Component.text()
                .append(Component.text("商店  $" + state.money, NamedTextColor.GOLD)).appendNewline()
                .append(Component.text("右键卡片购买 | 重掷 | 下一回合", NamedTextColor.GRAY))
                .build();
        owned.add(Holo.text(session.plugin(), session.player(), at(0, 2.3), "balatro_status", status, BG_STATUS, true));
        if (shop == null) return;
        int n = shop.cards.size();
        for (int i = 0; i < n; i++) {
            var c = shop.cards.get(i);
            double x = (i - (n - 1) / 2.0) * 1.1;
            TextColor col = c.sold ? NamedTextColor.GRAY : (c.kind.equals("joker") ? TextColor.color(255, 220, 120) : NamedTextColor.WHITE);
            Component face = Component.text((c.sold ? "[售] " : "") + shopCardLabel(c) + " $" + c.price, col);
            owned.add(Holo.text(session.plugin(), session.player(), at(x, 1.2), "balatro_shopcard_" + i, face, c.sold ? BG_STATUS : BG_NORMAL, true));
        }
        int pn = shop.packs.size();
        for (int i = 0; i < pn; i++) {
            var p = shop.packs.get(i);
            double x = (i - (pn - 1) / 2.0) * 1.6;
            owned.add(Holo.text(session.plugin(), session.player(), at(x, 0.1), "balatro_shoppack_" + i,
                    Component.text((p.sold ? "[售] " : "") + "📦" + p.name + " $" + p.price, NamedTextColor.AQUA), BG_NORMAL, true));
        }
        if (shop.voucher != null) {
            owned.add(Holo.text(session.plugin(), session.player(), at(0, -0.9), "balatro_voucher",
                    Component.text("🎫" + shop.voucher.name + " $" + shop.voucher.price + (shop.voucher.sold ? "(已售)" : ""), NamedTextColor.LIGHT_PURPLE), BG_NORMAL, true));
        }
        owned.add(Holo.text(session.plugin(), session.player(), at(-1.4, -1.8), "balatro_reroll",
                Component.text("🔄 重掷", NamedTextColor.YELLOW), BG_BUTTON, true));
        owned.add(Holo.text(session.plugin(), session.player(), at(1.4, -1.8), "balatro_next",
                Component.text("▶ 下一回合", NamedTextColor.GREEN), BG_BUTTON, true));
    }

    private void renderPack(RunState state) {
        var pack = state.pack;
        Component status = Component.text()
                .append(Component.text("补充包：" + (pack == null ? "?" : pack.def.name) + "（选 " + (pack == null ? 0 : pack.left) + " 张）", NamedTextColor.GOLD)).appendNewline()
                .append(Component.text("右键选择 | 跳过", NamedTextColor.GRAY))
                .build();
        owned.add(Holo.text(session.plugin(), session.player(), at(0, 2.3), "balatro_status", status, BG_STATUS, true));
        if (pack == null) return;
        int n = pack.cards.size();
        for (int i = 0; i < n; i++) {
            var c = pack.cards.get(i);
            double x = (i - (n - 1) / 2.0) * 1.1;
            String label = switch (c.kind) {
                case "joker" -> "小丑 " + c.name;
                case "playing" -> "游戏牌 " + c.name;
                default -> c.kind + " " + c.name;
            };
            TextColor col = c.taken ? NamedTextColor.GRAY : NamedTextColor.WHITE;
            owned.add(Holo.text(session.plugin(), session.player(), at(x, 0.8), "balatro_pick_" + i,
                    Component.text((c.taken ? "[选] " : "") + label, col), c.taken ? BG_STATUS : BG_NORMAL, true));
        }
        owned.add(Holo.text(session.plugin(), session.player(), at(0, -0.9), "balatro_skipack",
                Component.text("✗ 跳过", NamedTextColor.RED), BG_BUTTON, true));
    }

    private static String shopCardLabel(cn.quotidietium.balatro.engine.shop.Shop.CardItem c) {
        return switch (c.kind) {
            case "joker" -> "小丑 " + c.name;
            case "playing" -> "游戏牌 " + c.name;
            default -> c.kind + " " + c.name;
        };
    }

    // ---- 交互（由 BoardListener 经 tag 派发） ----

    public void toggleSelect(int cardId) {
        if (!selected.remove(cardId)) selected.add(cardId);
        update(session.state());
    }

    public void playSelected() {
        if (selected.isEmpty()) return;
        session.play(new ArrayList<>(selected));
        selected.clear();
    }

    public void discardSelected() {
        if (selected.isEmpty()) return;
        session.discard(new ArrayList<>(selected));
        selected.clear();
    }

    public void clearSelection() {
        selected.clear();
    }

    /** 销毁全部实体。 */
    public void despawn() {
        for (TextDisplay d : owned) {
            if (d.isValid()) d.remove();
        }
        owned.clear();
        selected.clear();
    }

    private void clear() {
        for (TextDisplay d : owned) {
            if (d.isValid()) d.remove();
        }
        owned.clear();
    }
}
