package cn.quotidietium.balatro.render;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.HandEval;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Consumable;
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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 单个玩家回合的全息牌桌（TextDisplay 文字牌面）。
 *
 * <p><b>稳定实体（无闪烁）</b>：状态栏/按钮/各牌区槽位均为持久 TextDisplay，{@link #update}
 * 只原地改写文本/背景/位置/标签，不再 clear+respawn——故选牌、补牌时面板不闪烁。
 * 卡牌实体打 scoreboard tag {@code balatro_card_<id>}，按钮打 {@code balatro_act_*}，
 * 命中检测由 {@code BoardListener} 用 {@code World.rayTraceEntities} 完成。
 *
 * <p><b>卡牌化外观</b>：每张牌以带背景色的色块呈现（点数 + 花色 + 增强/版本/蜡封角标），
 * 选中时改为绿色背景并向上抬起；手牌已由 {@link Engine#sortHand} 整理为点数降序，
 * 故呈现给玩家的始终是排列好的手牌。状态栏下方实时显示当前选中牌的牌型与基础筹码×倍率。
 *
 * <p>定位：以玩家眼部位置 + 朝向构造"前向基"，板面悬浮于玩家眼前；CENTER 朝向、私有可见（仅本人）。
 */
public final class RoundBoard {

    // ---- 布局常量 ----
    private static final double FORWARD = 2.6;
    private static final double CARD_SPACING = 0.78;
    private static final double HAND_Y = 0.0;
    private static final double SELECT_LIFT = 0.25;
    private static final double STATUS_Y = 2.5;
    private static final double EVAL_Y = 2.0;
    private static final double JOKER_Y = 1.4;
    private static final double JOKER_SPACING = 0.82;
    private static final double CONS_Y = 0.78;
    private static final double CONS_SPACING = 0.72;
    private static final double BUTTON_Y = -1.15;

    // ---- 命中盒（点击检测用，与视觉尺寸解耦；可独立调大以保证好点） ----
    private static final double CARD_HW = 0.36;   // 卡牌命中半宽（全宽 0.72）
    private static final double CARD_HH = 0.55;   // 卡牌命中半高（全高 1.10）
    private static final double BTN_HW = 0.55;
    private static final double BTN_HH = 0.24;
    private static final double SHOPCARD_HW = 0.6;
    private static final double SHOPCARD_HH = 0.4;
    private static final double PACK_HW = 0.8;
    private static final double PACK_HH = 0.35;

    /** 卡牌文字缩放（默认 1.0 约 0.5 格高，偏小；放大使牌面更清晰）。 */
    private static final float CARD_TEXT_SCALE = 1.5f;
    private static final float JOKER_TEXT_SCALE = 1.0f;
    private static final float FRAME_TEXT_SCALE = 1.0f;

    private static final Color TRANSPARENT = Color.fromARGB(0, 0, 0, 0);

    // ---- 配色 ----
    private static final Color BG_STATUS = Color.fromARGB(190, 0, 0, 0);
    private static final Color BG_NORMAL = Color.fromARGB(235, 30, 30, 46);
    private static final Color BG_RED = Color.fromARGB(235, 56, 28, 34);
    private static final Color BG_SELECTED = Color.fromARGB(245, 56, 165, 90);
    private static final Color BG_FACEDOWN = Color.fromARGB(235, 50, 60, 92);
    private static final Color BG_DEBUFF = Color.fromARGB(220, 60, 60, 64);
    private static final Color BG_BUTTON_PLAY = Color.fromARGB(225, 46, 120, 70);
    private static final Color BG_BUTTON_DISC = Color.fromARGB(225, 120, 50, 60);
    private static final Color BG_BUTTON = Color.fromARGB(225, 55, 70, 120);
    private static final Color BG_SOLD = Color.fromARGB(180, 40, 40, 48);

    private static final TextColor C_RED = TextColor.color(240, 90, 90);
    private static final TextColor C_DARK = TextColor.color(236, 236, 246);
    private static final TextColor C_ENH = TextColor.color(255, 220, 120);
    private static final TextColor C_SEAL = TextColor.color(120, 200, 255);
    private static final TextColor C_EDITION = TextColor.color(220, 180, 255);

    private final GameSession session;
    private final Vector origin;
    private final Vector forward;
    private final Vector right; // 玩家右手方向（板面横向）

    // ---- 持久实体 ----
    private final List<TextDisplay> all = new ArrayList<>(); // 全部实体（despawn 用）
    private TextDisplay statusBar;
    private TextDisplay evalBar;
    private TextDisplay playBtn;
    private TextDisplay discBtn;
    private TextDisplay rerollBtn;
    private TextDisplay nextBtn;
    private TextDisplay voucherEnt;
    private TextDisplay skipackBtn;
    private final List<TextDisplay> handSlots = new ArrayList<>();
    private final List<TextDisplay> jokerSlots = new ArrayList<>();
    private final List<TextDisplay> consSlots = new ArrayList<>();
    private final List<TextDisplay> shopSlots = new ArrayList<>();
    private final List<TextDisplay> packSlots = new ArrayList<>();

    private final Set<Integer> selected = new HashSet<>();
    private Phase activePhase = null;

    /** 当前阶段所有可点击元素（板面局部坐标 + 命中盒半尺寸 + 动作），由 reflow 重建。 */
    private final List<Clickable> clickables = new ArrayList<>();

    /** 一个可点击区域：板面局部 (lx,ly) 中心 + 半宽 hw + 半高 hh + 命中动作。 */
    private record Clickable(double lx, double ly, double hw, double hh, String action) {
        boolean hit(double rx, double ry) {
            return Math.abs(rx - lx) <= hw && Math.abs(ry - ly) <= hh;
        }
    }

    public RoundBoard(GameSession session) {
        this.session = session;
        Location eye = session.player().getEyeLocation();
        // 水平化朝向：牌桌正立悬浮于眼前（不随玩家俯仰倾斜），且命中平面与实体位置平面一致。
        Vector dir = eye.getDirection();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-6) dir.setX(1); // 玩家纯俯/仰视时给默认水平方向
        dir.normalize();
        this.origin = eye.toVector().add(dir.clone().multiply(FORWARD));
        this.forward = dir;
        Vector up = new Vector(0, 1, 0);
        this.right = dir.clone().getCrossProduct(up).normalize();
    }

    /** 初次生成。 */
    public void spawn(RunState state) {
        statusBar = mkFrame("balatro_status", BG_STATUS);
        evalBar = mkFrame("balatro_eval", BG_STATUS);
        playBtn = mkFrame("balatro_act_play", BG_BUTTON_PLAY);
        discBtn = mkFrame("balatro_act_discard", BG_BUTTON_DISC);
        rerollBtn = mkFrame("balatro_reroll", BG_BUTTON);
        nextBtn = mkFrame("balatro_next", BG_BUTTON_PLAY);
        voucherEnt = mkFrame("balatro_voucher", BG_NORMAL);
        skipackBtn = mkFrame("balatro_skipack", BG_BUTTON_DISC);
        update(state);
    }

    /** 状态变更后刷新（原地改写，不 clear+respawn）。 */
    public void update(RunState state) {
        clickables.clear();
        if (activePhase != state.phase) {
            hideAll();
            activePhase = state.phase;
        }
        switch (state.phase) {
            case SHOP -> reflowShop(state);
            case PACK -> reflowPack(state);
            default -> reflowRound(state);
        }
    }

    /**
     * 射线-板面平面相交命中检测（取代 {@code rayTraceEntities}——Display 实体命中盒极小不可靠）。
     *
     * <p>板面为过 {@link #origin}、法向 {@link #forward} 的平面（forward 已水平化，{@code up}=世界 Y）。
     * 求视线射线与该平面交点，转为板面局部坐标 (rx=横向, ry=垂直)，遍历 {@link #clickables}
     * 判断是否落在某命中盒内。返回动作串（如 {@code "card:12"}/{@code "play"}/{@code "shop:0"}）或 null。
     */
    public String hitTest(Location eye, Vector dir, double maxDist) {
        double denom = dir.dot(forward);
        if (denom < 0.02) return null; // 不朝向板面（或几乎平行）
        Vector e = eye.toVector();
        double t = origin.clone().subtract(e).dot(forward) / denom;
        if (t <= 0 || t > maxDist) return null;
        Vector p = e.clone().add(dir.clone().multiply(t));
        Vector rel = p.clone().subtract(origin);
        double rx = rel.dot(right);
        double ry = rel.getY(); // up = 世界 Y
        // 逆序：后注册（视觉在上层）优先，例如抬起的选中牌。
        for (int i = clickables.size() - 1; i >= 0; i--) {
            Clickable c = clickables.get(i);
            if (c.hit(rx, ry)) return c.action;
        }
        return null;
    }

    private TextDisplay mkFrame(String tag, Color bg) {
        TextDisplay d = Holo.text(session.plugin(), session.player(), at(0, 0), tag, Component.empty(), bg, true);
        all.add(d);
        return d;
    }

    /** 取第 i 个槽位（不足则创建）。 */
    private TextDisplay slot(List<TextDisplay> slots, int i, Color bg) {
        while (slots.size() <= i) {
            TextDisplay d = Holo.text(session.plugin(), session.player(), at(0, 0),
                    "balatro_slot", Component.empty(), bg, true);
            slots.add(d);
            all.add(d);
        }
        return slots.get(i);
    }

    private void hide(TextDisplay d) {
        d.text(Component.empty());
        d.setBackgroundColor(TRANSPARENT);
        // 移除所有可交互标签（card/action/shop/pick），仅保留 balatro_board/balatro_slot 标识；
        // 这样隐藏（空文本）的实体不再可被射线命中，避免跨阶段误点。
        d.getScoreboardTags().removeIf(t -> !t.equals("balatro_board") && !t.equals("balatro_slot"));
    }

    private void ensureTag(TextDisplay d, String tag) {
        if (!d.getScoreboardTags().contains(tag)) d.addScoreboardTag(tag);
    }

    private static final Quaternionf Q_IDENTITY = new Quaternionf();

    /** 设置实体整体缩放（放大文字与背景，使牌面更清晰、命中盒与之相称）。 */
    private static void setScale(TextDisplay d, float scale) {
        d.setTransformation(new Transformation(
                new Vector3f(0, 0, 0), Q_IDENTITY,
                new Vector3f(scale, scale, scale), Q_IDENTITY));
    }

    private void hideAll() {
        for (TextDisplay d : all) hide(d);
    }

    // ================= 回合视图 =================

    private void reflowRound(RunState state) {
        // 状态栏
        String blind = state.blindType == null ? "-" : state.blindType.key;
        String boss = state.blindType == Data.BlindType.BOSS && !state.bossQueue.isEmpty()
                ? "（" + Data.Boss.byKey(state.bossQueue.get(0)).name + "）" : "";
        statusBar.text(Component.text()
                .append(Component.text("底注 " + state.ante + "  " + blindName(blind) + boss, NamedTextColor.GOLD)).appendNewline()
                .append(Component.text("分数 " + state.roundScore + " / " + state.blindTarget, NamedTextColor.WHITE)).appendNewline()
                .append(Component.text("出牌 " + state.handsLeft + "  弃牌 " + state.discardsLeft + "  $" + state.money
                        + "  选中 " + selected.size(), NamedTextColor.YELLOW))
                .build());
        statusBar.teleport(at(0, STATUS_Y));

        // 实时牌型评估
        evalBar.text(evalText(state));
        evalBar.teleport(at(0, EVAL_Y));

        // 小丑行
        int jn = state.jokers.size();
        for (int i = 0; i < jn; i++) {
            JokerInstance ji = state.jokers.get(i);
            double x = (i - (jn - 1) / 2.0) * JOKER_SPACING;
            TextDisplay d = slot(jokerSlots, i, BG_NORMAL);
            TextColor jc = ji.debuff ? NamedTextColor.DARK_GRAY : TextColor.color(255, 220, 120);
            d.text(Component.text("🃏" + ji.def.displayName(), jc));
            d.setBackgroundColor(ji.debuff ? BG_DEBUFF : BG_NORMAL);
            d.teleport(at(x, JOKER_Y));
        }
        for (int i = jn; i < jokerSlots.size(); i++) hide(jokerSlots.get(i));

        // 消耗品行（仅展示）
        int cn = state.consumables.size();
        for (int i = 0; i < cn; i++) {
            Consumable c = state.consumables.get(i);
            double x = (i - (cn - 1) / 2.0) * CONS_SPACING;
            TextDisplay d = slot(consSlots, i, BG_NORMAL);
            d.text(Component.text(consLabel(c), TextColor.color(180, 220, 255)));
            d.setBackgroundColor(BG_NORMAL);
            d.teleport(at(x, CONS_Y));
        }
        for (int i = cn; i < consSlots.size(); i++) hide(consSlots.get(i));

        // 手牌（已按点数整理）
        int n = state.hand.size();
        for (int i = 0; i < n; i++) {
            Card card = state.hand.get(i);
            boolean sel = selected.contains(card.id());
            double x = (i - (n - 1) / 2.0) * CARD_SPACING;
            double y = HAND_Y + (sel ? SELECT_LIFT : 0);
            TextDisplay d = slot(handSlots, i, BG_NORMAL);
            setScale(d, CARD_TEXT_SCALE);
            d.text(cardFace(card, sel));
            d.setBackgroundColor(cardBg(card, sel));
            setCardTag(d, card.id());
            d.teleport(at(x, y));
            clickables.add(new Clickable(x, y, CARD_HW, CARD_HH, "card:" + card.id()));
        }
        for (int i = n; i < handSlots.size(); i++) hide(handSlots.get(i));

        // 按钮
        int selN = selected.size();
        playBtn.text(Component.text("▶ 出牌" + (selN > 0 ? " (" + selN + ")" : ""), NamedTextColor.GREEN));
        ensureTag(playBtn, "balatro_act_play");
        playBtn.teleport(at(-1.15, BUTTON_Y));
        clickables.add(new Clickable(-1.15, BUTTON_Y, BTN_HW, BTN_HH, "play"));
        discBtn.text(Component.text("✗ 弃牌" + (selN > 0 ? " (" + selN + ")" : ""), NamedTextColor.RED));
        ensureTag(discBtn, "balatro_act_discard");
        discBtn.teleport(at(1.15, BUTTON_Y));
        clickables.add(new Clickable(1.15, BUTTON_Y, BTN_HW, BTN_HH, "discard"));

        // 回合阶段不用的按钮隐藏
        hide(rerollBtn);
        hide(nextBtn);
        hide(voucherEnt);
        hide(skipackBtn);
        clearShopPackSlots();
    }

    /** 当前选中牌的牌型 + 基础筹码×倍率（不计小丑），无选中则给提示。 */
    private Component evalText(RunState state) {
        if (selected.isEmpty()) {
            return Component.text("右键手牌选中  ·  再右键「出牌/弃牌」", NamedTextColor.GRAY);
        }
        List<Card> cards = new ArrayList<>();
        for (Card c : state.hand) if (selected.contains(c.id())) cards.add(c);
        HandEval.Result res = Engine.evaluateHand(state, cards);
        if (res == null || res.type == null) {
            return Component.text("（无效牌组）", NamedTextColor.GRAY);
        }
        int lvl = state.handLevels.getOrDefault(res.type, 1);
        long chips = res.type.chipsAtLevel(lvl);
        long mult = res.type.multAtLevel(lvl);
        return Component.text()
                .append(Component.text(res.type.name + "  ", NamedTextColor.AQUA))
                .append(Component.text(chips + " 筹码", NamedTextColor.WHITE))
                .append(Component.text("  ×  ", NamedTextColor.GRAY))
                .append(Component.text(mult + " 倍", NamedTextColor.WHITE))
                .append(Component.text("  (Lv" + lvl + ")", NamedTextColor.DARK_GRAY))
                .build();
    }

    private Component cardFace(Card card, boolean selected) {
        if (card.facedown()) {
            return Component.text("？", NamedTextColor.WHITE);
        }
        if (card.isStone()) {
            return Component.text("石\n头", NamedTextColor.GRAY);
        }
        Data.Suit s = Data.Suit.byIndex(card.suit());
        TextColor col = selected ? NamedTextColor.WHITE : (s.isRed() ? C_RED : C_DARK);
        Component face = Component.empty()
                .append(Component.text(editionSym(card.edition()), C_EDITION))
                .append(Component.text(Data.rankName(card.rank()), col))
                .append(Component.text(sealSym(card.seal()), C_SEAL));
        face = face.appendNewline().append(Component.text(s.symbol, col));
        if (card.enh() != null) {
            face = face.appendNewline().append(Component.text(shortEnh(card.enh()), C_ENH));
        }
        if (card.debuff()) {
            face = face.appendNewline().append(Component.text("失效", NamedTextColor.DARK_GRAY));
        }
        return face;
    }

    private Color cardBg(Card card, boolean selected) {
        if (selected) return BG_SELECTED;
        if (card.facedown()) return BG_FACEDOWN;
        if (card.debuff()) return BG_DEBUFF;
        if (card.isStone()) return BG_NORMAL;
        Data.Suit s = Data.Suit.byIndex(card.suit());
        return s.isRed() ? BG_RED : BG_NORMAL;
    }

    private void setCardTag(TextDisplay d, int cardId) {
        d.getScoreboardTags().removeIf(t -> t.startsWith("balatro_card_"));
        d.addScoreboardTag("balatro_card_" + cardId);
    }

    // ================= 商店视图 =================

    private void reflowShop(RunState state) {
        var shop = state.shop;
        statusBar.text(Component.text()
                .append(Component.text("商店  $" + state.money, NamedTextColor.GOLD)).appendNewline()
                .append(Component.text("右键卡片购买  ·  重掷  ·  下一回合", NamedTextColor.GRAY))
                .build());
        statusBar.teleport(at(0, STATUS_Y));
        hide(evalBar);

        clearRoundSlots();
        if (shop == null) {
            hide(rerollBtn);
            hide(nextBtn);
            hide(voucherEnt);
            return;
        }
        int n = shop.cards.size();
        for (int i = 0; i < n; i++) {
            var c = shop.cards.get(i);
            double x = (i - (n - 1) / 2.0) * 1.15;
            TextDisplay d = slot(shopSlots, i, BG_NORMAL);
            TextColor col = c.sold ? NamedTextColor.DARK_GRAY
                    : (c.kind.equals("joker") ? TextColor.color(255, 220, 120) : NamedTextColor.WHITE);
            d.text(Component.text((c.sold ? "[售] " : "") + shopCardLabel(c) + " $" + c.price, col));
            d.setBackgroundColor(c.sold ? BG_SOLD : BG_NORMAL);
            setIndexedTag(d, "balatro_shopcard_", i);
            d.teleport(at(x, 1.3));
            if (!c.sold) clickables.add(new Clickable(x, 1.3, SHOPCARD_HW, SHOPCARD_HH, "shop:" + i));
        }
        for (int i = n; i < shopSlots.size(); i++) hide(shopSlots.get(i));

        int pn = shop.packs.size();
        for (int i = 0; i < pn; i++) {
            var p = shop.packs.get(i);
            double x = (i - (pn - 1) / 2.0) * 1.6;
            TextDisplay d = slot(packSlots, i, BG_NORMAL);
            d.text(Component.text((p.sold ? "[售] " : "") + "📦" + p.name + " $" + p.price, NamedTextColor.AQUA));
            d.setBackgroundColor(p.sold ? BG_SOLD : BG_NORMAL);
            setIndexedTag(d, "balatro_shoppack_", i);
            d.teleport(at(x, 0.2));
            if (!p.sold) clickables.add(new Clickable(x, 0.2, PACK_HW, PACK_HH, "shoppack:" + i));
        }
        for (int i = pn; i < packSlots.size(); i++) hide(packSlots.get(i));

        if (shop.voucher != null) {
            voucherEnt.text(Component.text("🎫" + shop.voucher.name + " $" + shop.voucher.price
                    + (shop.voucher.sold ? "(已售)" : ""), NamedTextColor.LIGHT_PURPLE));
            voucherEnt.setBackgroundColor(shop.voucher.sold ? BG_SOLD : BG_NORMAL);
            ensureTag(voucherEnt, "balatro_voucher");
            voucherEnt.teleport(at(0, -0.8));
            if (!shop.voucher.sold) clickables.add(new Clickable(0, -0.8, PACK_HW, PACK_HH, "voucher"));
        } else {
            hide(voucherEnt);
        }
        rerollBtn.text(Component.text("🔄 重掷", NamedTextColor.YELLOW));
        ensureTag(rerollBtn, "balatro_reroll");
        rerollBtn.teleport(at(-1.5, -1.7));
        clickables.add(new Clickable(-1.5, -1.7, BTN_HW, BTN_HH, "reroll"));
        nextBtn.text(Component.text("▶ 下一回合", NamedTextColor.GREEN));
        ensureTag(nextBtn, "balatro_next");
        nextBtn.teleport(at(1.5, -1.7));
        clickables.add(new Clickable(1.5, -1.7, BTN_HW, BTN_HH, "next"));
        hide(playBtn);
        hide(discBtn);
        hide(skipackBtn);
    }

    // ================= 补充包视图 =================

    private void reflowPack(RunState state) {
        var pack = state.pack;
        statusBar.text(Component.text()
                .append(Component.text("补充包：" + (pack == null ? "?" : pack.def.name)
                        + "（选 " + (pack == null ? 0 : pack.left) + " 张）", NamedTextColor.GOLD)).appendNewline()
                .append(Component.text("右键选择  ·  跳过", NamedTextColor.GRAY))
                .build());
        statusBar.teleport(at(0, STATUS_Y));
        hide(evalBar);
        clearRoundSlots();

        if (pack == null) {
            hide(skipackBtn);
            return;
        }
        int n = pack.cards.size();
        for (int i = 0; i < n; i++) {
            var c = pack.cards.get(i);
            double x = (i - (n - 1) / 2.0) * 1.15;
            TextDisplay d = slot(packSlots, i, BG_NORMAL);
            d.text(Component.text((c.taken ? "[选] " : "") + packCardLabel(c),
                    c.taken ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE));
            d.setBackgroundColor(c.taken ? BG_SOLD : BG_NORMAL);
            setIndexedTag(d, "balatro_pick_", i);
            d.teleport(at(x, 0.9));
            if (!c.taken) clickables.add(new Clickable(x, 0.9, SHOPCARD_HW, SHOPCARD_HH, "pick:" + i));
        }
        for (int i = n; i < packSlots.size(); i++) hide(packSlots.get(i));

        skipackBtn.text(Component.text("✗ 跳过", NamedTextColor.RED));
        ensureTag(skipackBtn, "balatro_skipack");
        skipackBtn.teleport(at(0, -0.8));
        clickables.add(new Clickable(0, -0.8, BTN_HW, BTN_HH, "skipack"));
        hide(playBtn);
        hide(discBtn);
        hide(rerollBtn);
        hide(nextBtn);
        hide(voucherEnt);
    }

    // ================= 角标 / 标签 =================

    private static String shortEnh(Data.Enhancement e) {
        return switch (e) {
            case BONUS -> "+筹";
            case MULT -> "+倍";
            case WILD -> "万能";
            case GLASS -> "玻璃";
            case STEEL -> "钢铁";
            case STONE -> "石头";
            case GOLD -> "黄金";
            case LUCKY -> "幸运";
        };
    }

    private static String editionSym(Data.Edition e) {
        if (e == null) return "";
        return switch (e) {
            case FOIL -> "✦";
            case HOLO -> "✧";
            case POLY -> "❉";
            case NEGATIVE -> "➖";
        };
    }

    private static String sealSym(Data.Seal s) {
        if (s == null) return "";
        return switch (s) {
            case GOLD -> "ᚷ";
            case RED -> "ᚱ";
            case BLUE -> "ᛒ";
            case PURPLE -> "ᛈ";
        };
    }

    private static String shopCardLabel(cn.quotidietium.balatro.engine.shop.Shop.CardItem c) {
        return switch (c.kind) {
            case "joker" -> "小丑 " + c.name;
            case "playing" -> "游戏牌 " + c.name;
            default -> c.kind + " " + c.name;
        };
    }

    private static String packCardLabel(cn.quotidietium.balatro.engine.shop.Packs.PackCard c) {
        return switch (c.kind) {
            case "joker" -> "小丑 " + c.name;
            case "playing" -> "游戏牌 " + c.name;
            default -> c.kind + " " + c.name;
        };
    }

    private static String consLabel(Consumable c) {
        return switch (c.kind) {
            case "tarot" -> "塔罗 " + Data.Tarot.byKey(c.key).name;
            case "planet" -> "星球 " + Data.Planet.byKey(c.key).name;
            case "spectral" -> "幻灵 " + Data.Spectral.byKey(c.key).name;
            default -> c.kind + " " + c.key;
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

    private void setIndexedTag(TextDisplay d, String prefix, int idx) {
        d.getScoreboardTags().removeIf(t -> t.startsWith(prefix));
        d.addScoreboardTag(prefix + idx);
    }

    private void clearRoundSlots() {
        for (TextDisplay d : handSlots) hide(d);
        for (TextDisplay d : jokerSlots) hide(d);
        for (TextDisplay d : consSlots) hide(d);
    }

    private void clearShopPackSlots() {
        for (TextDisplay d : shopSlots) hide(d);
        for (TextDisplay d : packSlots) hide(d);
    }

    /** 板面上某 (横向, 垂直) 偏移处的世界坐标。 */
    private Location at(double rightOffset, double upOffset) {
        Vector v = origin.clone()
                .add(right.clone().multiply(rightOffset))
                .add(new Vector(0, upOffset, 0));
        return new Location(session.player().getWorld(), v.getX(), v.getY(), v.getZ());
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
        for (TextDisplay d : all) {
            if (d.isValid()) d.remove();
        }
        all.clear();
        handSlots.clear();
        jokerSlots.clear();
        consSlots.clear();
        shopSlots.clear();
        packSlots.clear();
        selected.clear();
        activePhase = null;
    }
}
