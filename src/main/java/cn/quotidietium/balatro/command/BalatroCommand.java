package cn.quotidietium.balatro.command;

import cn.quotidietium.balatro.BalatroPlugin;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.session.GameSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /balatro 命令（别名 blt / joker）。
 * 0.1.0 子命令：play [seed] | quit | status | playcard <1-based 索引...> | disc <索引...> | endless
 * （文字版交互，便于在全息 UI（S8/S9）落地前后端到端验证）。
 */
public final class BalatroCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "help", "play", "quit", "status", "playcard", "disc", "endless",
            "shop", "buy", "buybag", "buyvoucher", "reroll", "next", "go", "skip",
            "cons", "use", "packs", "pick", "skipack", "sellj", "sellc", "top");

    private final BalatroPlugin plugin;

    public BalatroCommand(BalatroPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令只能由玩家执行。");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help", "?" -> cmdHelp(player, args);
            case "play" -> cmdPlay(player, args);
            case "quit" -> cmdQuit(player);
            case "status", "hand" -> cmdStatus(player);
            case "playcard", "pc" -> cmdPlayCard(player, args);
            case "disc", "discard" -> cmdDiscard(player, args);
            case "endless" -> cmdEndless(player);
            case "shop" -> cmdShop(player);
            case "buy" -> cmdBuy(player, args);
            case "buybag", "pack" -> cmdBuyPack(player, args);
            case "buyvoucher", "voucher" -> cmdBuyVoucher(player);
            case "reroll" -> cmdReroll(player);
            case "next" -> cmdNext(player);
            case "go" -> cmdGo(player);
            case "skip" -> cmdSkip(player);
            case "cons", "consumables" -> cmdCons(player);
            case "use" -> cmdUse(player, args);
            case "packs" -> cmdPack(player);
            case "pick" -> cmdPick(player, args);
            case "skipack" -> cmdSkipPack(player);
            case "top" -> cmdTop(player);
            case "sellj" -> cmdSellJoker(player, args);
            case "sellc" -> cmdSellConsumable(player, args);
            default -> sendHelp(player);
        }
        return true;
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
                stake = Integer.parseInt(a);
            } else if (isDeckArg(a)) {
                deck = a;
            } else if (isChallengeArg(a)) {
                challenge = a;
            } else {
                seed = a;
            }
        }
        GameSession s = plugin.sessionManager().start(player, deck, stake, seed, challenge);
        if (s == null) {
            player.sendMessage("§c开局失败（可能 RunStart 被其他插件取消）。");
            return;
        }
        StringBuilder head = new StringBuilder("§a开始一局小丑牌！种子=").append(s.state().seed);
        head.append("  牌组=").append(deck);
        if (stake > 0) head.append("  赌注=").append(stake);
        if (challenge != null) head.append("  挑战=").append(challenge);
        player.sendMessage(head.toString());
        player.sendMessage(s.handDebug());
    }

    private static boolean isStakeArg(String a) {
        if (a.length() != 1 || !Character.isDigit(a.charAt(0))) return false;
        int n = a.charAt(0) - '0';
        return n >= 0 && n <= 7;
    }

    private static boolean isDeckArg(String a) {
        for (var d : cn.quotidietium.balatro.engine.Data.DECKS) {
            if (d.key().equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    private static boolean isChallengeArg(String a) {
        for (var c : cn.quotidietium.balatro.engine.Data.CHALLENGES) {
            if (c.key().equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    private void cmdHelp(Player player, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c页码需为数字，用法：/balatro help <页码>");
                return;
            }
        }
        BalatroHelp.sendPage(player, page);
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
        if (shop.voucher != null) {
            player.sendMessage("§d[券] §f" + shop.voucher.name + " §a$" + shop.voucher.price
                    + (shop.voucher.sold ? " §7(已售)" : ""));
        }
        player.sendMessage("§7/balatro buy <序号> | buybag <序号> | buyvoucher | reroll | next");
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
        if (s.buyPack(idx)) player.sendMessage("§a购买补充包成功（补充包选择界面 0.2.0 后续）。");
        else player.sendMessage("§c购买失败。");
    }

    private void cmdBuyVoucher(Player player) {
        GameSession s = requireShop(player);
        if (s == null) return;
        if (s.buyVoucher()) player.sendMessage("§a购买优惠券成功！");
        else player.sendMessage("§c购买失败。");
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
        promptBlindSelect(player, s);
    }

    /** 提示当前盲注选择（开始/跳过）。 */
    private void promptBlindSelect(Player player, GameSession s) {
        if (s.state().phase != cn.quotidietium.balatro.engine.Phase.BLIND_SELECT) return;
        var bt = cn.quotidietium.balatro.engine.Data.BlindType.byKey(s.state().nextBlind);
        long target = cn.quotidietium.balatro.engine.Engine.blindTarget(s.state(), bt);
        String boss = bt == cn.quotidietium.balatro.engine.Data.BlindType.BOSS
                ? "（" + cn.quotidietium.balatro.engine.Data.Boss.byKey(s.state().bossQueue.isEmpty() ? "" : s.state().bossQueue.get(0)).name + "）"
                : "";
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
        int cidx;
        try { cidx = Integer.parseInt(args[1]) - 1; } catch (NumberFormatException e) { player.sendMessage("§c无效序号"); return; }
        List<Integer> cardIds = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            int hi = Integer.parseInt(args[i]);
            if (hi < 1 || hi > s.state().hand.size()) { player.sendMessage("§c手牌序号越界：" + args[i]); return; }
            cardIds.add(s.state().hand.get(hi - 1).id());
        }
        var r = s.useConsumable(cidx, cardIds);
        if (!r.ok) player.sendMessage("§c" + r.err);
        else { player.sendMessage("§a使用成功。"); cmdCons(player); }
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
        if (s.sellJoker(idx)) player.sendMessage("§a小丑已出售！");
        else player.sendMessage("§c出售失败（永恒/无效）。");
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
        var top = plugin.services().leaderboard().top(10);
        if (top.isEmpty()) { player.sendMessage("§7暂无记录。"); return; }
        player.sendMessage("§6=== 小丑牌排行榜 ===");
        int rank = 1;
        for (var s : top) {
            player.sendMessage(String.format("§e#%d §f%s §7%s 底注%d %s",
                    rank++, s.won() ? "§a通关" : "§c失败", s.deckKey(), s.anteReached(), s.seed()));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== 小丑牌 /balatro ===");
        player.sendMessage("§e/balatro help [页码] §7- 完整玩法帮助（分页，每页≤6行）");
        player.sendMessage("§e/balatro play [牌组] [赌注] [挑战] [种子] §7- 开始一局");
        player.sendMessage("§e/balatro playcard <索引...> §7- 出牌（1 起的手牌序号，1-5 张）");
        player.sendMessage("§e/balatro disc <索引...> §7- 弃牌");
        player.sendMessage("§e/balatro status §7- 查看当前局面");
        player.sendMessage("§e/balatro endless §7- 通关后进入无尽模式");
        player.sendMessage("§e/balatro shop §7- 查看商店");
        player.sendMessage("§e/balatro buy <序号> §7- 购买商店卡牌");
        player.sendMessage("§e/balatro buybag <序号> §7- 购买补充包");
        player.sendMessage("§e/balatro buyvoucher §7- 购买优惠券");
        player.sendMessage("§e/balatro reroll §7- 重掷商店");
        player.sendMessage("§e/balatro next §7- 离开商店进入下一盲注");
        player.sendMessage("§e/balatro quit §7- 放弃本局");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
            java.util.List<String> pages = new java.util.ArrayList<>();
            for (int p = 1; p <= BalatroHelp.totalPages(); p++) pages.add(Integer.toString(p));
            return filter(args[1], pages);
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
