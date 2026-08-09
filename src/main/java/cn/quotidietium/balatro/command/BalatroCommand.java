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
            "play", "quit", "status", "playcard", "disc", "endless");

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
            case "play" -> cmdPlay(player, args);
            case "quit" -> cmdQuit(player);
            case "status", "hand" -> cmdStatus(player);
            case "playcard", "pc" -> cmdPlayCard(player, args);
            case "disc", "discard" -> cmdDiscard(player, args);
            case "endless" -> cmdEndless(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void cmdPlay(Player player, String[] args) {
        if (plugin.sessionManager().isActive(player)) {
            player.sendMessage("§c你已在一局中，先用 /balatro quit。");
            return;
        }
        String seed = args.length >= 2 ? args[1] : null;
        GameSession s = plugin.sessionManager().start(player, "red", 0, seed);
        if (s == null) {
            player.sendMessage("§c开局失败（可能 RunStart 被其他插件取消）。");
            return;
        }
        player.sendMessage("§a开始一局小丑牌！种子=" + s.state().seed + "  牌组=red  白注");
        player.sendMessage(s.handDebug());
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

    private void sendHelp(Player player) {
        player.sendMessage("§6=== 小丑牌 /balatro ===");
        player.sendMessage("§e/balatro play [种子] §7- 开始一局（红牌组/白注）");
        player.sendMessage("§e/balatro playcard <索引...> §7- 出牌（1 起的手牌序号，1-5 张）");
        player.sendMessage("§e/balatro disc <索引...> §7- 弃牌");
        player.sendMessage("§e/balatro status §7- 查看当前局面");
        player.sendMessage("§e/balatro endless §7- 通关后进入无尽模式");
        player.sendMessage("§e/balatro quit §7- 放弃本局");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(args[0], SUBS);
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
