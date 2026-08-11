package cn.quotidietium.balatro.command;

import cn.quotidietium.balatro.BalatroPlugin;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.session.GameSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /balatro 命令（别名 blt / joker），仅玩家可用。
 *
 * <p>子命令（序号均从 1 起；全息右键为等价操作，命令为备用）：
 * <ul>
 *   <li>通用：{@code gui | play [牌组] [赌注] [挑战] [种子] | status | endless | top | quit}</li>
 *   <li>回合：{@code playcard <序号...> | disc <序号...>}</li>
 *   <li>盲注选择：{@code go | skip}</li>
 *   <li>商店：{@code shop | buy <序号> | buybag <序号> | buyvoucher | reroll | next}</li>
 *   <li>消耗品：{@code cons | use <序号> [手牌序号...]}</li>
 *   <li>补充包：{@code packs | pick <序号> | skipack}</li>
 *   <li>出售：{@code sellj <序号> | sellc <序号>}</li>
 * </ul>
 * {@code cancel} 仅为全息出售确认框「[取消]」按钮的回执，不列入帮助。
 * 分页详细玩法与单命令详情见 {@link BalatroHelp}。
 */
public final class BalatroCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "help", "gui", "play", "quit", "status", "playcard", "disc", "endless",
            "shop", "buy", "buybag", "buyvoucher", "reroll", "next", "go", "skip",
            "cons", "use", "packs", "pick", "skipack", "sellj", "sellc", "top", "cancel");

    private final BalatroPlugin plugin;

    /** {@code /balatro top} 的每玩家节流间隔（毫秒）：聚合排行榜每次全量遍历统计记录，防宏刷。 */
    private static final long TOP_THROTTLE_MS = 1_000L;
    private final Map<UUID, Long> lastTop = new HashMap<>();

    public BalatroCommand(BalatroPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令只能由玩家执行。");
            return true;
        }
        // 权限实施：plugin.yml 声明 balatro.play（默认 true）。默认配置行为不变；
        // 服务器经权限插件撤销后在此拦截（此前该节点仅为声明，未实际实施）。
        if (!player.hasPermission("balatro.play")) {
            player.sendMessage("§c你没有使用小丑牌命令的权限（balatro.play）。");
            return true;
        }
        // 命令层统一兜底：客户端输入一律不可信，任何子命令路径都不应向 Bukkit 命令分发器
        // 抛异常（否则触发难看的错误回显/日志刷屏）。各 cmdXxx 已对参数做防御，但第三方
        // 事件监听器（fireRunStart/fireHandScore 经 GameSession 间接调用）或意外的引擎状态
        // 仍可能抛 RuntimeException——在此最后一道兜住，记日志并向玩家给出友好提示。
        try {
            dispatch(player, args);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("命令处理异常（玩家 " + player.getName()
                    + "，参数 " + java.util.Arrays.toString(args) + "）：" + ex);
            player.sendMessage("§c处理命令时出错，请重试或联系管理员。");
        }
        return true;
    }

    private void dispatch(Player player, String[] args) {
        if (args.length == 0) {
            sendHelp(player);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "help", "?" -> cmdHelp(player, args);
            case "gui", "menu" -> cmdGui(player);
            case "play" -> cmdPlay(player, args);
            case "quit" -> cmdQuit(player);
            case "status", "hand" -> cmdStatus(player);
            case "playcard", "pc" -> cmdPlayCard(player, args);
            case "disc", "discard" -> cmdDiscard(player, args);
            case "endless" -> cmdEndless(player);
            case "shop" -> cmdShop(player);
            case "buy" -> cmdBuy(player, args);
            case "buybag", "pack" -> cmdBuyPack(player, args);
            case "buyvoucher", "voucher" -> cmdBuyVoucher(player, args);
            case "reroll" -> cmdReroll(player);
            case "next" -> cmdNext(player);
            case "go" -> cmdGo(player);
            case "skip" -> cmdSkip(player);
            case "cons", "consumables" -> cmdCons(player);
            case "use" -> cmdUse(player, args);
            case "packs" -> cmdPack(player);
            case "pick" -> cmdPick(player, args);
            case "skipack" -> cmdSkipPack(player);
            case "cancel" -> player.sendMessage("§7已取消操作。");
            case "top" -> cmdTop(player);
            case "sellj" -> cmdSellJoker(player, args);
            case "sellc" -> cmdSellConsumable(player, args);
            default -> sendHelp(player);
        }
    }

    /** 打开开局向导 GUI（图形界面选择 模式/牌组/赌注/挑战/种子）。 */
    private void cmdGui(Player player) {
        if (plugin.sessionManager().isActive(player)) {
            player.sendMessage("§c你已在一局中，先用 /balatro quit。");
            return;
        }
        plugin.guiManager().openGui(player);
    }

    private void cmdPlay(Player player, String[] args) {
        if (plugin.sessionManager().isActive(player)) {
            player.sendMessage("§c你已在一局中，先用 /balatro quit。");
            return;
        }
        // 参数顺序不限：自动识别 牌组名 / 赌注数字 / 挑战名，其余视作种子。
        String deck = "red";
        int stake = 0;
        String challenge = null;
        String seed = null;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (isStakeArg(a)) {
                stake = a.charAt(0) - '0';
            } else if (deckKeyOf(a) != null) {
                deck = deckKeyOf(a); // 规范化为表内 key（引擎大小写敏感）
            } else if (challengeKeyOf(a) != null) {
                challenge = challengeKeyOf(a);
            } else {
                seed = a;
            }
        }
        // 种子来自客户端，必须校验（长度/字符集），拒绝非法输入
        if (seed != null && !cn.quotidietium.balatro.engine.Rng.isValidSeed(seed)) {
            player.sendMessage("§c无效种子：仅允许字母/数字/下划线/连字符，长度 1~32。");
            return;
        }
        GameSession s = plugin.sessionManager().start(player, deck, stake, seed, challenge);
        if (s == null) {
            player.sendMessage("§c开局失败（可能 RunStart 被其他插件取消）。");
            return;
        }
        sendRunInfo(player, s, deck, stake, challenge);
        player.sendMessage(s.handDebug());
    }

    /**
     * 开局时在聊天框给出本局完整信息，便于新手快速了解：
     * 种子/牌组/赌注/挑战名 + 各自效果 + 开局特殊持有（券/消耗品）+ 第一个 Boss + 操作提示。
     *
     * <p>public static：GUI 开局向导（gui 包）与命令层共用同一套展示，避免两处文案漂移。
     */
    public static void sendRunInfo(Player player, GameSession s, String deck, int stake, String challenge) {
        cn.quotidietium.balatro.engine.RunState st = s.state();
        cn.quotidietium.balatro.engine.Data.Deck dk = cn.quotidietium.balatro.engine.Data.deckByKey(deck);
        cn.quotidietium.balatro.engine.Data.Stake sk = cn.quotidietium.balatro.engine.Data.STAKES.get(stake);

        player.sendMessage("§6━━ 小丑牌 · 本局信息 ━━");
        StringBuilder head = new StringBuilder("§e种子 §f").append(st.seed)
                .append("  §e牌组 §f").append(dk.name())
                .append("  §e赌注 §f").append(sk.name());
        if (challenge != null) {
            cn.quotidietium.balatro.engine.Data.Challenge ch = findChallenge(challenge);
            if (ch != null) head.append("  §e挑战 §f").append(ch.name());
        }
        player.sendMessage(head.toString());
        player.sendMessage("§6牌组效果：§f" + dk.desc());
        player.sendMessage("§6赌注效果：§f" + sk.desc());
        if (challenge != null) {
            cn.quotidietium.balatro.engine.Data.Challenge ch = findChallenge(challenge);
            if (ch != null) player.sendMessage("§6挑战效果：§f" + ch.desc());
        }
        // 开局特殊持有：本局初始拥有的优惠券 / 消耗品（牌组或挑战带来）
        java.util.List<String> startItems = new java.util.ArrayList<>();
        for (String vk : st.vouchers) {
            try {
                startItems.add(cn.quotidietium.balatro.engine.Data.voucherByKey(vk).name + "(券)");
            } catch (IllegalArgumentException ignored) {
                startItems.add(vk + "(券)");
            }
        }
        for (var c : st.consumables) startItems.add(c.name());
        if (!startItems.isEmpty()) {
            player.sendMessage("§6开局持有：§f" + String.join(" §7·§f ", startItems));
        }
        // 第一个 Boss 盲注（让玩家提前规划）
        cn.quotidietium.balatro.engine.Data.Boss boss = Engine.bossDef(st);
        player.sendMessage("§6第 1 底注 Boss：§f" + boss.name + " §7— " + boss.desc);
        player.sendMessage("§7右键手牌选中 · 出牌/弃牌；§e/balatro help§7 查看完整玩法");
    }

    private static cn.quotidietium.balatro.engine.Data.Challenge findChallenge(String key) {
        for (cn.quotidietium.balatro.engine.Data.Challenge c : cn.quotidietium.balatro.engine.Data.CHALLENGES) {
            if (c.key().equals(key)) return c;
        }
        return null;
    }

    private static boolean isStakeArg(String a) {
        if (a.length() != 1 || !Character.isDigit(a.charAt(0))) return false;
        int n = a.charAt(0) - '0';
        return n >= 0 && n <= 7;
    }

    /** 大小写不敏感匹配牌组，返回表内规范 key；不匹配返回 null。 */
    private static String deckKeyOf(String a) {
        for (var d : cn.quotidietium.balatro.engine.Data.DECKS) {
            if (d.key().equalsIgnoreCase(a)) return d.key();
        }
        return null;
    }

    /** 大小写不敏感匹配挑战，返回表内规范 key；不匹配返回 null。 */
    private static String challengeKeyOf(String a) {
        for (var c : cn.quotidietium.balatro.engine.Data.CHALLENGES) {
            if (c.key().equalsIgnoreCase(a)) return c.key();
        }
        return null;
    }

    private void cmdHelp(Player player, String[] args) {
        if (args.length < 2) {
            BalatroHelp.sendPage(player, 1);
            return;
        }
        String arg = args[1];
        // 数字 → 分页帮助
        try {
            int page = Integer.parseInt(arg);
            BalatroHelp.sendPage(player, page);
            return;
        } catch (NumberFormatException ignored) {
            // 非数字 → 视作命令名
        }
        if (!BalatroHelp.sendCommandHelp(player, arg)) {
            player.sendMessage("§c未知命令：§e" + arg + "§c。输入 §e/balatro help§c 看分页帮助，或 §e/balatro help <命令名>§c。");
        }
    }

    private void cmdQuit(Player player) {
        if (!plugin.sessionManager().isActive(player)) {
            player.sendMessage("§c当前没有进行中的局。");
            return;
        }
        plugin.sessionManager().end(player);
        player.sendMessage("§e已放弃本局。");
    }

    private void cmdStatus(Player player) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null) {
            player.sendMessage("§c当前没有进行中的局。");
            return;
        }
        player.sendMessage(s.handDebug());
    }

    private void cmdPlayCard(Player player, String[] args) {
        GameSession s = requireRound(player);
        if (s == null) return;
        List<Integer> ids = parseIndices(player, s, args, 1);
        if (ids == null) return;
        Engine.PlayResult r = s.play(ids);
        report(player, s, r);
    }

    private void cmdDiscard(Player player, String[] args) {
        GameSession s = requireRound(player);
        if (s == null) return;
        List<Integer> ids = parseIndices(player, s, args, 1);
        if (ids == null) return;
        Engine.PlayResult r = s.discard(ids);
        if (!r.ok) {
            player.sendMessage("§c" + r.err);
            return;
        }
        player.sendMessage("§7已弃牌，新手牌：");
        player.sendMessage(s.handDebug());
    }

    private void cmdEndless(Player player) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null) {
            player.sendMessage("§c当前没有进行中的局。");
            return;
        }
        if (s.continueEndless()) {
            player.sendMessage("§a进入无尽模式！");
            player.sendMessage(s.handDebug());
        } else {
            player.sendMessage("§c当前无法进入无尽模式（需先通关）。");
        }
    }

    private GameSession requireRound(Player player) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null) {
            player.sendMessage("§c当前没有进行中的局，用 /balatro play 开始。");
            return null;
        }
        if (s.state().phase != cn.quotidietium.balatro.engine.Phase.ROUND) {
            player.sendMessage("§c当前不在出牌回合。");
            return null;
        }
        return s;
    }

    private List<Integer> parseIndices(Player player, GameSession s, String[] args, int from) {
        if (args.length <= from) {
            player.sendMessage("§c用法：/balatro playcard <1-based 索引...>，例如 /balatro playcard 1 2 3");
            return null;
        }
        List<Integer> ids = new ArrayList<>();
        int handSize = s.state().hand.size();
        for (int i = from; i < args.length; i++) {
            int idx;
            try {
                idx = Integer.parseInt(args[i]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c无效的索引：" + args[i]);
                return null;
            }
            if (idx < 1 || idx > handSize) {
                player.sendMessage("§c索引越界：" + idx + "（手牌 " + handSize + " 张）");
                return null;
            }
            ids.add(s.state().hand.get(idx - 1).id());
        }
        return ids;
    }

    private void report(Player player, GameSession s, Engine.PlayResult r) {
        if (!r.ok) {
            player.sendMessage("§c" + r.err);
            return;
        }
        player.sendMessage("§f出牌【" + (r.type == null ? "-" : r.type.name) + "】 得分 §e" + r.score
                + "§f / 累计 §e" + s.state().roundScore + "§f / 目标 §e" + s.state().blindTarget);
        if (r.won) {
            if (s.state().won) {
                player.sendMessage("§6§l通关！种子 " + s.state().seed + "，可用 /balatro endless 进入无尽模式，或 /balatro quit 结束。");
            } else if (s.state().phase == cn.quotidietium.balatro.engine.Phase.ROUND) {
                player.sendMessage("§a通过盲注！进入下一回合。");
                player.sendMessage(s.handDebug());
            } else {
                player.sendMessage("§a通过盲注！");
            }
        } else if (r.lost) {
            player.sendMessage("§4§l本局失败（未达目标分）。用 /balatro play 重新开始。");
        } else {
            player.sendMessage(s.handDebug());
        }
    }

    private void cmdShop(Player player) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null || s.state().phase != cn.quotidietium.balatro.engine.Phase.SHOP) {
            player.sendMessage("§c当前不在商店。");
            return;
        }
        var shop = s.state().shop;
        player.sendMessage("§6=== 商店 === §e$" + s.state().money);
        int i = 0;
        for (var c : shop.cards) {
            String label = shopCardLabel(c);
            player.sendMessage("§e[" + (i + 1) + "] §f" + label + (c.sold ? " §7(已售)" : " §a$" + c.price));
            i++;
        }
        int j = 0;
        for (var p : shop.packs) {
            player.sendMessage("§b[包" + (j + 1) + "] §f" + p.name + " §a$" + p.price + (p.sold ? " §7(已售)" : ""));
            j++;
        }
        int vk = 0;
        for (var vch : shop.vouchers) {
            player.sendMessage("§d[券" + (vk + 1) + "] §f" + vch.name + " §a$" + vch.price
                    + (vch.sold ? " §7(已售)" : ""));
            vk++;
        }
        player.sendMessage("§7/balatro buy <序号> | buybag <序号> | buyvoucher <券序号> | reroll | next");
    }

    private String shopCardLabel(cn.quotidietium.balatro.engine.shop.Shop.CardItem c) {
        return switch (c.kind) {
            case "joker" -> "小丑 " + c.name + (c.joker.edition != null ? "(" + c.joker.edition.name + ")" : "");
            case "playing" -> "游戏牌 " + c.name;
            default -> c.kind + " " + c.name;
        };
    }

    private void cmdBuy(Player player, String[] args) {
        GameSession s = requireShop(player);
        if (s == null) return;
        int idx = parseOne(player, args);
        if (idx < 0) return;
        if (s.buyCard(idx)) player.sendMessage("§a购买成功！");
        else player.sendMessage("§c购买失败（资金不足/槽满/已售）。");
        cmdShop(player);
    }

    private void cmdBuyPack(Player player, String[] args) {
        GameSession s = requireShop(player);
        if (s == null) return;
        int idx = parseOne(player, args);
        if (idx < 0) return;
        if (s.buyPack(idx)) {
            player.sendMessage("§a购买补充包成功！");
            cmdPack(player); // 直接列出内容（与 cmdBuy 重列商店一致）；pick/skipack 提示在列表尾部
        } else {
            player.sendMessage("§c购买失败（资金不足/已售）。");
        }
    }

    private void cmdBuyVoucher(Player player, String[] args) {
        GameSession s = requireShop(player);
        if (s == null) return;
        // 多券时需指定序号；单券时允许省略（默认第 1 张），保持向后兼容
        int idx;
        if (args.length >= 2) {
            idx = parseOne(player, args);
            if (idx < 0) return;
        } else {
            if (s.state().shop.vouchers.size() == 1) idx = 0;
            else if (s.state().shop.vouchers.isEmpty()) {
                player.sendMessage("§c当前商店没有优惠券。");
                return;
            } else {
                player.sendMessage("§c当前商店有多张优惠券，请用 §e/balatro buyvoucher <券序号>§c 指定。");
                return;
            }
        }
        if (s.buyVoucher(idx)) player.sendMessage("§a购买优惠券成功！");
        else player.sendMessage("§c购买失败（资金不足/已售/越界）。");
    }

    private void cmdReroll(Player player) {
        GameSession s = requireShop(player);
        if (s == null) return;
        long cost = s.reroll();
        if (cost < 0) player.sendMessage("§c重掷失败（资金不足）。");
        else { player.sendMessage("§a重掷成功（-$" + cost + "）。"); cmdShop(player); }
    }

    private void cmdNext(Player player) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null || s.state().phase != cn.quotidietium.balatro.engine.Phase.SHOP) {
            player.sendMessage("§c当前不在商店。");
            return;
        }
        s.nextRound();
        promptBlindSelect(player, s);
    }

    private void cmdGo(Player player) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null || s.state().phase != cn.quotidietium.balatro.engine.Phase.BLIND_SELECT) {
            player.sendMessage("§c当前不在盲注选择阶段。");
            return;
        }
        if (s.chooseBlind(false)) {
            player.sendMessage("§a开始盲注！");
            player.sendMessage(s.handDebug());
        }
    }

    private void cmdSkip(Player player) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null || s.state().phase != cn.quotidietium.balatro.engine.Phase.BLIND_SELECT) {
            player.sendMessage("§c当前不在盲注选择阶段。");
            return;
        }
        if (!s.chooseBlind(true)) {
            player.sendMessage("§c无法跳过（Boss 盲注不可跳过）。");
            return;
        }
        // 跳过可能获得「立即开包」标签（standard/buffoon → 引擎进入补充包阶段）：
        // 全息路径由 board.update 自动列出简介，命令路径在此同步列出，避免玩家不知已进入开包
        if (s.state().phase == cn.quotidietium.balatro.engine.Phase.PACK && s.state().pack != null) {
            player.sendMessage("§e跳过获得标签：立即开启补充包！");
            cmdPack(player);
            return;
        }
        promptBlindSelect(player, s);
    }

    /** 提示当前盲注选择（开始/跳过）。 */
    private void promptBlindSelect(Player player, GameSession s) {
        if (s.state().phase != cn.quotidietium.balatro.engine.Phase.BLIND_SELECT) return;
        var bt = cn.quotidietium.balatro.engine.Data.BlindType.byKey(s.state().nextBlind);
        long target = cn.quotidietium.balatro.engine.Engine.blindTarget(s.state(), bt);
        String boss = "";
        if (bt == cn.quotidietium.balatro.engine.Data.BlindType.BOSS && !s.state().bossQueue.isEmpty()) {
            boss = "（" + cn.quotidietium.balatro.engine.Data.Boss.byKey(s.state().bossQueue.get(0)).name + "）";
        }
        player.sendMessage("§6下一盲注：§f底注 " + s.state().ante + " · " + blindName(bt.key) + boss
                + " · 目标 " + target + " 分");
        player.sendMessage("§e/balatro go §7开始盲注    §e/balatro skip §7跳过并获标签" + (bt == cn.quotidietium.balatro.engine.Data.BlindType.BOSS ? "（Boss 不可跳过）" : ""));
    }

    private static String blindName(String key) {
        return switch (key) {
            case "small" -> "小盲注";
            case "big" -> "大盲注";
            case "boss" -> "Boss 盲注";
            default -> key;
        };
    }

    private GameSession requireShop(Player player) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null || s.state().phase != cn.quotidietium.balatro.engine.Phase.SHOP) {
            player.sendMessage("§c当前不在商店。");
            return null;
        }
        return s;
    }

    private int parseOne(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c缺少序号参数。");
            return -1;
        }
        try {
            return Integer.parseInt(args[1]) - 1; // 1-based → 0-based
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效序号：" + args[1]);
            return -1;
        }
    }

    private void cmdCons(Player player) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null) { player.sendMessage("§c当前没有进行中的局。"); return; }
        if (s.state().consumables.isEmpty()) { player.sendMessage("§7没有消耗品。"); return; }
        int i = 0;
        for (var c : s.state().consumables) {
            player.sendMessage("§d[" + (i + 1) + "] §f" + c.kind + " " + c.name() + " §7" + c.desc());
            i++;
        }
        player.sendMessage("§7/balatro use <序号> [手牌序号...]（目标手牌从 1 起）");
    }

    private void cmdUse(Player player, String[] args) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null) { player.sendMessage("§c当前没有进行中的局。"); return; }
        if (args.length < 2) { player.sendMessage("§c用法：/balatro use <消耗品序号> [手牌序号...]"); return; }
        // 统一经 parseOne 解析消耗品序号（与 buy/pick/sell 一致）：1-based→0-based + 越界/非数字拦截。
        // 此前手写解析是唯一缺 <0 拦截的序号命令（越界仅靠引擎兜底，且文案逊于其它命令）。
        int cidx = parseOne(player, args);
        if (cidx < 0) return;
        // 全息「确认使用」按钮在末位携带期望 kind:key（含冒号，区别于数字手牌序号）：
        // 确认后到点击前消耗品列表可能已变化（使用/出售收缩列表），序号可能指向另一个
        // 消耗品——校验不一致则取消，防止错位用错。手动输入不带标识则跳过校验（向后兼容）。
        int targetEnd = args.length;
        if (args.length >= 3) {
            String last = args[args.length - 1];
            if (last.indexOf(':') >= 0) {
                if (!consKindKeyAt(s, cidx).equals(last)) {
                    player.sendMessage("§c消耗品列表已变化，使用已取消。请重新右键该消耗品确认。");
                    return;
                }
                targetEnd = args.length - 1;
            }
        }
        List<Integer> cardIds = new ArrayList<>();
        for (int i = 2; i < targetEnd; i++) {
            int hi;
            try {
                hi = Integer.parseInt(args[i]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c无效的手牌序号：" + args[i]);
                return;
            }
            if (hi < 1 || hi > s.state().hand.size()) { player.sendMessage("§c手牌序号越界：" + args[i]); return; }
            cardIds.add(s.state().hand.get(hi - 1).id());
        }
        var r = s.useConsumable(cidx, cardIds);
        if (!r.ok) player.sendMessage("§c" + r.err);
        else { player.sendMessage("§a使用成功。"); cmdCons(player); }
    }

    /** 当前消耗品 idx 处的期望标识（kind:key）；越界返回空串（必不匹配，走「列表已变化」取消）。 */
    private static String consKindKeyAt(GameSession s, int idx) {
        var cons = s.state().consumables;
        if (idx < 0 || idx >= cons.size()) return "";
        var c = cons.get(idx);
        return c.kind + ":" + c.key;
    }

    private void cmdPack(Player player) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null || s.state().phase != cn.quotidietium.balatro.engine.Phase.PACK || s.state().pack == null) {
            player.sendMessage("§c当前没有正在开启的补充包。");
            return;
        }
        var pack = s.state().pack;
        player.sendMessage("§6=== 补充包：§f" + pack.def.name + "§6（选 " + pack.left + " 张）===");
        int i = 0;
        for (var c : pack.cards) {
            String label = switch (c.kind) {
                case "joker" -> "小丑 " + c.name;
                case "playing" -> "游戏牌 " + c.name;
                default -> c.kind + " " + c.name;
            };
            player.sendMessage("§e[" + (i + 1) + "] §f" + label + (c.taken ? " §7(已选)" : ""));
            i++;
        }
        player.sendMessage("§7/balatro pick <序号> | skipack");
    }

    private void cmdPick(Player player, String[] args) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null || s.state().pack == null) { player.sendMessage("§c当前没有补充包。"); return; }
        int idx = parseOne(player, args);
        if (idx < 0) return;
        if (s.pickPack(idx)) { player.sendMessage("§a已选择。"); if (s.state().phase == cn.quotidietium.balatro.engine.Phase.PACK) cmdPack(player); }
        else player.sendMessage("§c选择失败（槽满/已选）。");
    }

    private void cmdSkipPack(Player player) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null || s.state().pack == null) { player.sendMessage("§c当前没有补充包。"); return; }
        s.skipPack();
        player.sendMessage("§e已跳过补充包。");
    }

    private void cmdSellJoker(Player player, String[] args) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null) { player.sendMessage("§c当前没有进行中的局。"); return; }
        int idx = parseOne(player, args);
        if (idx < 0) return;
        // 全息「确认出售」按钮在第 3 参数携带期望 joker key：确认后到点击前小丑列表
        // 可能已被改写（幻灵 hex/ankh、命令出售等），序号可能指向另一张小丑——
        // 校验不一致则取消，防止错位卖错。手动输入不带标识则跳过校验（向后兼容）。
        if (args.length >= 3 && !jokerKeyAt(s, idx).equals(args[2])) {
            player.sendMessage("§c小丑列表已变化，出售已取消。请重新右键该小丑确认。");
            return;
        }
        if (s.sellJoker(idx)) player.sendMessage("§a小丑已出售！");
        else player.sendMessage("§c出售失败（永恒/无效）。");
    }

    /** 当前小丑 idx 处的期望 key；越界返回空串（必不匹配，走「列表已变化」取消）。 */
    private static String jokerKeyAt(GameSession s, int idx) {
        var jokers = s.state().jokers;
        return (idx >= 0 && idx < jokers.size()) ? jokers.get(idx).def.key() : "";
    }

    private void cmdSellConsumable(Player player, String[] args) {
        GameSession s = plugin.sessionManager().get(player);
        if (s == null) { player.sendMessage("§c当前没有进行中的局。"); return; }
        int idx = parseOne(player, args);
        if (idx < 0) return;
        if (s.sellConsumable(idx)) player.sendMessage("§a消耗品已出售！");
        else player.sendMessage("§c出售失败（无效）。");
    }

    private void cmdTop(Player player) {
        // 聚合排行榜每次全量遍历统计记录（上限上万条）：篡改客户端可宏刷这条只读命令，
        // 让主线程反复做聚合+排序——每玩家 1s 节流。节流表惰性清扫（>60s 即失效），
        // 长期运行玩家流转下有界。
        long now = System.currentTimeMillis();
        Long last = lastTop.get(player.getUniqueId());
        if (last != null && now - last < TOP_THROTTLE_MS) {
            player.sendMessage("§7查询过于频繁，请稍后再试。");
            return;
        }
        lastTop.put(player.getUniqueId(), now);
        if (lastTop.size() > 128) {
            lastTop.values().removeIf(t -> now - t > 60_000L);
        }
        java.util.List<cn.quotidietium.balatro.api.PlayerStat> aggregated;
        try {
            aggregated = plugin.services().leaderboard().topAggregated(10);
        } catch (RuntimeException ex) {
            // 第三方排行榜服务异常：不向上击穿命令，降级为友好提示
            plugin.getLogger().warning("LeaderboardService.topAggregated 异常：" + ex);
            player.sendMessage("§c排行榜服务暂不可用，请稍后再试。");
            return;
        }
        if (aggregated.isEmpty()) { player.sendMessage("§7暂无记录。"); return; }
        // 补玩家名后在 Bukkit 层做完整三级排序：bestAnte 降序 → winCount 降序 → 玩家名升序
        java.util.List<String[]> rows = new java.util.ArrayList<>(); // {name, bestAnte, winCount}
        for (var ps : aggregated) {
            String name = plugin.getServer().getOfflinePlayer(ps.playerId()).getName();
            if (name == null) name = ps.playerId().toString().substring(0, 8);
            rows.add(new String[]{name, String.valueOf(ps.bestAnte()), String.valueOf(ps.winCount())});
        }
        rows.sort((a, b) -> {
            int anteA = Integer.parseInt(a[1]), anteB = Integer.parseInt(b[1]);
            if (anteA != anteB) return Integer.compare(anteB, anteA); // 降序
            int wcA = Integer.parseInt(a[2]), wcB = Integer.parseInt(b[2]);
            if (wcA != wcB) return Integer.compare(wcB, wcA); // 降序
            return a[0].compareToIgnoreCase(b[0]); // 玩家名升序
        });
        player.sendMessage("§6=== 小丑牌排行榜（最高底注 · 通关次数）===");
        int rank = 1;
        for (var row : rows) {
            int ante = Integer.parseInt(row[1]);
            int wc = Integer.parseInt(row[2]);
            String anteStr = ante > 8 ? "§d无尽" + ante : "§f底注" + ante;
            player.sendMessage(String.format("§e#%d §f%s §7%s §b通关%d次", rank++, row[0], anteStr, wc));
        }
    }

    /**
     * 直接输入 /balatro（无参数 / 未知子命令）时的简要帮助。
     * 覆盖全部面向玩家的命令，按游戏阶段分组；详细玩法见 {@code /balatro help}。
     * {@code cancel} 不列出：它是全息出售确认框「[取消]」按钮的回执，非玩法命令。
     *
     * <p>每个命令令牌均为可悬浮（显示该命令的详细说明与使用举例）+ 可点击（回填命令）的组件，
     * 由 {@link HoverText} 从帮助注册表生成——文案只维护 {@link BalatroHelp} 一份。
     */
    private void sendHelp(Player player) {
        player.sendMessage("§6=== 小丑牌 /balatro ===");
        player.sendMessage(HoverText.commandify(
                "§7新手推荐图形界面开局：§e/balatro gui§7；下列命令悬浮可看详情与举例、点击可回填。"));
        player.sendMessage(HoverText.commandify(
                "§7完整玩法（牌组/赌注/挑战/计分）：§e/balatro help [页码]§7；单命令详情：§e/balatro help <命令名>"));
        player.sendMessage("§6■ 通用");
        player.sendMessage(line(t("gui"), " 图形界面开局  ", t("play"), " 命令开局  ", t("status"), " 查看局面"));
        player.sendMessage(line(t("endless"), " 无尽模式  ", t("top"), " 排行榜  ", t("quit"), " 放弃本局"));
        player.sendMessage("§6■ 出牌回合");
        player.sendMessage(line(t("playcard"), " 出牌  ", t("disc"), " 弃牌（各 1~5 张）"));
        player.sendMessage("§6■ 盲注选择（商店 next 之后）");
        player.sendMessage(line(t("go"), " 开始盲注  ", t("skip"), " 跳过并获标签（Boss 不可跳过）"));
        player.sendMessage("§6■ 商店");
        player.sendMessage(line(t("shop"), " 查看  ", t("buy"), " 买卡  ", t("buybag"), " 买补充包"));
        player.sendMessage(line(t("buyvoucher"), " 买券  ", t("reroll"), " 重掷  ", t("next"), " 离开商店"));
        player.sendMessage("§6■ 消耗品（塔罗 / 星球 / 幻灵）");
        player.sendMessage(line(t("cons"), " 查看  ", t("use"), " 使用"));
        player.sendMessage("§6■ 补充包");
        player.sendMessage(line(t("packs"), " 查看  ", t("pick"), " 选卡  ", t("skipack"), " 跳过"));
        player.sendMessage("§6■ 出售");
        player.sendMessage(line(t("sellj"), " 卖小丑  ", t("sellc"), " 卖消耗品"));
    }

    /** 可悬浮/可点击的命令令牌（显示裸命令名，悬浮 = 详情与举例，点击 = 回填 /balatro <主键>）。 */
    private static net.kyori.adventure.text.Component t(String key) {
        return HoverText.token(key, key);
    }

    /** 拼接一行：组件原样加入，字符串按灰色说明文字加入。 */
    private static net.kyori.adventure.text.Component line(Object... parts) {
        net.kyori.adventure.text.Component out = net.kyori.adventure.text.Component.empty();
        for (Object p : parts) {
            if (p instanceof net.kyori.adventure.text.Component c) {
                out = out.append(c);
            } else {
                out = out.append(net.kyori.adventure.text.Component.text(
                        String.valueOf(p), net.kyori.adventure.text.format.NamedTextColor.GRAY));
            }
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 无 balatro.play 权限者不补全（与 onCommand 的权限实施一致，不暴露命令结构）
        if (!(sender instanceof Player) || !sender.hasPermission("balatro.play")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], SUBS);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("play")) {
            // 补全牌组名（其余参数顺序不限，牌组名是最有用的提示）
            java.util.List<String> decks = new java.util.ArrayList<>();
            for (var d : cn.quotidietium.balatro.engine.Data.DECKS) decks.add(d.key());
            decks.addAll(java.util.Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7")); // 赌注
            return filter(args[args.length - 1], decks);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            // 页码 + 命令名（含别名）
            java.util.List<String> opts = new java.util.ArrayList<>();
            for (int p = 1; p <= BalatroHelp.totalPages(); p++) opts.add(Integer.toString(p));
            opts.addAll(BalatroHelp.commandKeys());
            return filter(args[1], opts);
        }
        return List.of();
    }

    private List<String> filter(String prefix, List<String> options) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.startsWith(prefix.toLowerCase())) out.add(o);
        }
        return out;
    }
}
