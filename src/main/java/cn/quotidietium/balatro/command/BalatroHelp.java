package cn.quotidietium.balatro.command;

import java.util.ArrayList;
import java.util.List;

/**
 * /balatro help 的分页帮助内容（聊天框显示，每页 ≤ 6 行）。
 *
 * <p>每页 = 标题行 + 至多 5 行正文（合计 ≤ 6 行）。页码与总页数由 {@link #sendPage} 注入。
 * 内容覆盖：开始/牌组/赌注/挑战/回合/商店/消耗品/出售排行/全息交互/计分要素。
 */
final class BalatroHelp {

    /** 一页帮助：标题（显示在页眉）+ 正文行。 */
    private record Page(String title, String[] body) {
    }

    private static final List<Page> PAGES = new ArrayList<>();

    static {
        add("开始与目标", new String[]{
                "目标：凑扑克牌型得分达到盲注目标，击败 8 个底注(ante)即通关。",
                "计分 = 筹码 × 倍率；小丑牌与增强牌会大幅加成。",
                "开局：§e/balatro gui§f（图形界面，推荐）或 §e/balatro play [牌组] [赌注] [挑战] [种子]",
                "play 参数顺序不限：牌组名/赌注数字/挑战名自动识别，其余视作种子。",
                "翻页：§e/balatro help <页码>§f；查命令：§e/balatro help <命令名>§f（如 help play）。"
        });
        add("牌组（共 15，play 传牌组名）", new String[]{
                "§ered红§f(+弃牌) §eblue蓝§f(+出牌) §eyellow黄§f(开局+$10) §egreen绿§f(+钱/无利息)",
                "§eblack黑§f(+槽/-出牌) §emagic魔法§f(水晶球+2愚人) §enebula星云§f(望远镜/-消耗品槽)",
                "§eghost幽灵§f(幻灵+妖术) §ezodiac黄道§f(开局3张券) §eabandoned废弃§f(无人头牌)",
                "§echeckered棋盘§f(仅黑桃红桃) §epainted涂鸦§f(+手牌/-槽) §eplasma等离子§f(筹码倍率取平均)",
                "§eerratic百变§f(随机点数花色) §eanaglyph浮雕§f(击败Boss得翻倍标签)"
        });
        add("赌注（0~7，play 传数字，效果累加）", new String[]{
                "§e0 白注§f（基础）   §e1 红注§f（小盲无奖励金）",
                "§e2 绿注§f（目标分随底注加速 ×1.15） §e3 黑注§f（商店可能出现永恒小丑）",
                "§e4 蓝注§f（每回合弃牌 -1） §e5 紫注§f（目标分再加速 ×1.3）",
                "§e6 橙注§f（商店可能出现易腐小丑） §e7 金注§f（可能出现租赁小丑）",
                "赌注越高越难；从 §e0§f 开始体验，逐步挑战更高赌注。"
        });
        add("挑战模式（play 传挑战名，共 20 · 上）", new String[]{
                "§eomelette§f煎蛋卷(5蛋/无盲注奖·出牌金·利息) §ecity15§f十五分钟城市(人头牌翻倍)",
                "§erich§f富者愈富(利息翻倍) §eknife§f刀尖行走(目标分×1.5)",
                "§exray§fX光视界(1/4抽牌面朝下) §emadworld§f疯狂世界(每底注2Boss)",
                "§eluxury§f奢侈品税(每小丑目标分+10%) §enonperish§f永不过期(全永恒/6折)",
                "§emedusa§f美杜莎(人头变石头) §edouble§f孤注一掷(1出牌/目标减半)"
        });
        add("挑战模式（共 20 · 下）", new String[]{
                "§etypecast§f刻板印象(仅黑桃红桃) §einflation§f通货膨胀(每回合+价)",
                "§ebram§f布拉姆扑克(数字变人头) §efragile§f易碎品(玻璃更易碎)",
                "§emonolith§f巨石阵(全石头牌) §eblastoff§f点火升空(开局火箭/$0)",
                "§efivecard§f五张抽牌(必出5张) §egolden§f金针(1出牌/奖励×3)",
                "§ecruelty§f残酷(小大盲奖励减半) §ejokerless§f无丑之地(无法获得小丑)"
        });
        add("出牌回合命令", new String[]{
                "§e/balatro status§f   查看当前局面与手牌",
                "§e/balatro playcard <序号...>§f   出牌（1~5 张，序号从 1 起）",
                "§e/balatro disc <序号...>§f   弃牌",
                "§e/balatro endless§f   通关后进入无尽模式（ante 9+）",
                "§e/balatro quit§f   放弃本局（退出即弃，不存档）"
        });
        add("商店命令（击败盲注后进入）", new String[]{
                "§e/balatro shop§f   查看商店商品/补充包/优惠券",
                "§e/balatro buy <序号>§f 购买卡牌  §7|§e buybag <序号>§f 购买补充包",
                "§e/balatro buyvoucher§f   购买优惠券",
                "§e/balatro reroll§f   重掷商店商品",
                "§e/balatro next§f 离开商店 → §ego§f/skip  §7出售持有牌见 §e/balatro help 8"
        });
        add("商店出售持有牌", new String[]{
                "商店阶段可在全息牌桌上看到并出售持有的小丑/消耗品（对齐原版）。",
                "§e右键持有小丑§f → 确认出售（永恒不可售）；§e右键消耗品§f → 确认出售。",
                "§e/balatro sellj <序号>§f 命令出售小丑  §7|§e sellc <序号>§f 命令出售消耗品",
                "售价 = 购买价的一半（最少 $1），蛋/礼品卡可额外增加售价。",
                "回合阶段右键消耗品仍为「使用」，商店阶段右键才为「出售」。"
        });
        add("消耗品与补充包", new String[]{
                "§e/balatro cons§f   查看持有的消耗品（塔罗/星球/幻灵）",
                "§e/balatro use <序号> [手牌序号...]§f   使用消耗品（需目标时传手牌序号）",
                "§e/balatro packs§f   查看正在开启的补充包",
                "§e/balatro pick <序号>§f   从补充包选一张",
                "§e/balatro skipack§f   跳过当前补充包"
        });
        add("出售 · 排行榜 · 帮助", new String[]{
                "§e/balatro sellj <序号>§f   出售小丑（永恒不可售）",
                "§e/balatro sellc <序号>§f   出售消耗品",
                "§e/balatro top§f   查看小丑牌排行榜（按玩家聚合：最高底注 · 通关次数）",
                "§e/balatro help <页码>§f   查看指定页帮助",
                "§e/balatro§f（无参数）显示简要帮助 §7|§e version§f 版本与版权信息"
        });
        add("全息牌桌交互", new String[]{
                "§e/balatro play§f 后眼前出现牌桌（仅自己可见）。",
                "直接§e右键§f = 使用/操作（选牌·购买·选择·使用·出售·出牌/弃牌/重掷）。",
                "§eShift + 右键§f = 查看该卡简介（发到聊天框）。",
                "进入商店/补充包时会自动列出全部简介，便于判断。",
                "手牌自动按点数从大到小排列；选中牌上移并高亮。"
        });
        add("计分要素", new String[]{
                "§6增强§f(8)：奖励+筹 / 倍率+倍 / 万能 / 玻璃×2 / 钢铁(手中×1.5)",
                "       石头+50筹 / 黄金(手中+$3) / 幸运(几率+倍或+$)",
                "§d版本§f(4)：闪膜+50筹 / 镭射+10倍 / 多彩×1.5 / 负片(槽+1)",
                "§b蜡封§f(4)：金(计分+$3) / 红(重触发) / 蓝(手中→星球) / 紫(弃牌→塔罗)",
                "牌型可被§e星球牌§f升级；13 种牌型各有基础筹码 × 倍率。"
        });
    }

    private static void add(String title, String[] body) {
        PAGES.add(new Page(title, body));
    }

    /** 总页数。 */
    static int totalPages() {
        return PAGES.size();
    }

    /**
     * 把第 {@code page} 页（1 起）发给玩家；越界则提示范围。
     *
     * @return 实际是否发送了一页（页码合法）。
     */
    static boolean sendPage(org.bukkit.command.CommandSender sender, int page) {
        if (page < 1 || page > PAGES.size()) {
            sender.sendMessage("§7帮助共 §e" + PAGES.size() + " §7页，用法：§e/balatro help <1~" + PAGES.size() + ">");
            return false;
        }
        for (String line : linesFor(page)) {
            // commandify：行内 /balatro 命令令牌变为可悬浮（详情+举例）可点击（回填）组件
            sender.sendMessage(HoverText.commandify(line));
        }
        return true;
    }

    /**
     * 第 {@code page} 页（1 起）会被发送的全部行（页眉 + 正文）。
     * 用于测试断言「每页 ≤ 6 行」。页码越界返回空列表。
     */
    static java.util.List<String> linesFor(int page) {
        if (page < 1 || page > PAGES.size()) return java.util.List.of();
        Page p = PAGES.get(page - 1);
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("§6━━ 小丑牌 · 帮助 §e" + page + "/" + PAGES.size() + " §6· " + p.title + " ━━");
        for (String line : p.body) lines.add("§f" + line);
        return lines;
    }

    // ================= 单命令详情（/balatro help <命令名>） =================

    /** 一条命令的帮助：主键、别名、标题、正文行。包内可见：{@link HoverText} 复用为悬浮详情。 */
    record CmdHelp(String key, String[] aliases, String title, String[] body) {
    }

    private static final List<CmdHelp> COMMANDS = new ArrayList<>();

    static {
        cmd("help", new String[]{"?"}, "帮助", new String[]{
                "§e/balatro help [页码 | 命令名]",
                "· 不带参数 / 数字 → 分页帮助（每页 ≤ 6 行）。",
                "· 命令名（如 §eplay§f）→ 该命令的详细说明。",
                "例：§e/balatro help§f · §e/balatro help 2§f · §e/balatro help play"
        });
        cmd("gui", new String[]{"menu"}, "图形界面开局向导", new String[]{
                "§e/balatro gui",
                "打开图形界面，点击物品依次选择：模式(标准/挑战) → 牌组(15) → 赌注(0~7) → (挑战) → 确认。",
                "种子默认随机；确认页左键种子图标可在聊天框输入指定种子（60 秒内），右键恢复随机。",
                "例：§e/balatro gui§f（等价别名 §e/balatro menu§f）"
        });
        cmd("play", new String[]{}, "开始一局", new String[]{
                "§e/balatro play [牌组] [赌注] [挑战] [种子]",
                "参数顺序不限，自动识别：牌组名(15)/赌注数字(0~7)/挑战名(20)，其余视作种子。",
                "§7单数字 0~7 识别为赌注；种子含数字时需混入字母（如 seed3）或用 GUI 输入。",
                "例：§e/balatro play blue 1§f · §e/balatro play red 0 omelette§f · §e/balatro play myseed",
                "留空 = 随机种子 · 红牌组 · 白注。牌组/赌注/挑战详见 §e/balatro help 2~5"
        });
        cmd("playcard", new String[]{"pc"}, "出牌", new String[]{
                "§e/balatro playcard <手牌序号...>",
                "选 1~5 张手牌出牌计分（序号从 1 起，见 §e/balatro status§f）。",
                "例：§e/balatro playcard 1 2 3",
                "也可全息：右键手牌选中 → 右键「▶ 出牌」。"
        });
        cmd("disc", new String[]{"discard"}, "弃牌", new String[]{
                "§e/balatro disc <手牌序号...>",
                "弃 1~5 张手牌并补满（序号从 1 起）。",
                "紫色蜡封的牌被弃时会获得一张塔罗牌。",
                "全息：右键选中 → 右键「✗ 弃牌」。"
        });
        cmd("status", new String[]{"hand"}, "查看当前局面", new String[]{
                "§e/balatro status",
                "显示底注 / 盲注 / 分数 / 出牌·弃牌次数 / 金钱 / 手牌。"
        });
        cmd("shop", new String[]{}, "查看商店", new String[]{
                "§e/balatro shop",
                "列出商品(小丑/塔罗/星球/幻灵/游戏牌) · 补充包 · 优惠券 及价格。",
                "仅在击败盲注后的商店阶段可用。"
        });
        cmd("buy", new String[]{}, "购买商店卡牌", new String[]{
                "§e/balatro buy <序号>",
                "购买商店第 N 张卡牌（序号见 §e/balatro shop§f，从 1 起）。",
                "全息：右键商品卡。"
        });
        cmd("buybag", new String[]{"pack"}, "购买补充包", new String[]{
                "§e/balatro buybag <序号>",
                "购买商店第 N 个补充包，购买后进入补充包选择。"
        });
        cmd("buyvoucher", new String[]{"voucher"}, "购买优惠券", new String[]{
                "§e/balatro buyvoucher",
                "购买商店当前陈列的优惠券（若有）。"
        });
        cmd("reroll", new String[]{}, "重掷商店", new String[]{
                "§e/balatro reroll",
                "花费重掷商店商品（费用逐次 +1，优惠券「重掷红利」等可减免）。"
        });
        cmd("next", new String[]{}, "离开商店", new String[]{
                "§e/balatro next",
                "离开商店，进入下一盲注的【选择阶段】（再用 §ego§f/skip 决定）。"
        });
        cmd("go", new String[]{}, "开始当前盲注", new String[]{
                "§e/balatro go",
                "在盲注选择阶段开始当前盲注，进入出牌回合。",
                "全息：右键「▶ 开始盲注」。"
        });
        cmd("skip", new String[]{}, "跳过当前盲注", new String[]{
                "§e/balatro skip",
                "跳过当前盲注并获 1 个随机标签（Boss 盲注不可跳过）。",
                "全息：右键「✗ 跳过(标签)」。"
        });
        cmd("cons", new String[]{"consumables"}, "查看消耗品", new String[]{
                "§e/balatro cons",
                "列出持有的消耗品（塔罗/星球/幻灵）及说明与序号。"
        });
        cmd("use", new String[]{}, "使用消耗品", new String[]{
                "§e/balatro use <消耗品序号> [手牌序号...]",
                "需指定目标的消耗品（如改写手牌的塔罗）要传手牌序号（从 1 起）。",
                "例：§e/balatro use 1 2 3§f（用第 1 个消耗品作用于第 2、3 张手牌）。",
                "全息右键为「无目标使用」，需目标时请用命令。"
        });
        cmd("packs", new String[]{}, "查看补充包", new String[]{
                "§e/balatro packs",
                "列出正在开启的补充包内容（仅补充包阶段）。"
        });
        cmd("pick", new String[]{}, "从补充包选卡", new String[]{
                "§e/balatro pick <序号>",
                "从当前补充包选第 N 张（可选张数视包而定）。"
        });
        cmd("skipack", new String[]{}, "跳过补充包", new String[]{
                "§e/balatro skipack",
                "跳过当前补充包的剩余选择。"
        });
        cmd("sellj", new String[]{}, "出售小丑", new String[]{
                "§e/balatro sellj <序号>",
                "出售第 N 张小丑（永恒小丑不可出售）。",
                "全息：回合或商店阶段右键小丑牌 → 确认出售。"
        });
        cmd("sellc", new String[]{}, "出售消耗品", new String[]{
                "§e/balatro sellc <序号>",
                "出售第 N 个消耗品。",
                "全息：商店阶段右键消耗品 → 确认出售。"
        });
        cmd("endless", new String[]{}, "进入无尽模式", new String[]{
                "§e/balatro endless",
                "通关（底注 8）后进入无尽模式：底注 9+，目标分指数增长。"
        });
        cmd("top", new String[]{}, "查看排行榜", new String[]{
                "§e/balatro top",
                "查看小丑牌排行榜（按玩家聚合：最高到达底注降序 · 累计通关次数降序）。"
        });
        cmd("version", new String[]{"ver"}, "版本与版权信息", new String[]{
                "§e/balatro version",
                "显示当前版本号、插件作者/协作者、开源协议（Apache-2.0）与项目开源地址。"
        });
        cmd("quit", new String[]{}, "放弃本局", new String[]{
                "§e/balatro quit",
                "放弃当前局（退出即弃，不存档）。下线也会自动放弃。"
        });
    }

    private static void cmd(String key, String[] aliases, String title, String[] body) {
        COMMANDS.add(new CmdHelp(key, aliases, title, body));
    }

    /** 按主键或别名查找（大小写不敏感）；未找到返回 null。包内可见：{@link HoverText} 查询悬浮详情。 */
    static CmdHelp findCommand(String name) {
        if (name == null) return null;
        String n = name.toLowerCase();
        for (CmdHelp c : COMMANDS) {
            if (c.key.equalsIgnoreCase(n)) return c;
            for (String a : c.aliases) if (a.equalsIgnoreCase(n)) return c;
        }
        return null;
    }

    /**
     * 发送指定命令的详情；未找到返回 false。
     */
    static boolean sendCommandHelp(org.bukkit.command.CommandSender sender, String name) {
        CmdHelp c = findCommand(name);
        if (c == null) return false;
        sender.sendMessage(HoverText.commandify("§6■ §e" + c.key + "§6 — " + c.title));
        for (String line : c.body) sender.sendMessage(HoverText.commandify("§f" + line));
        return true;
    }

    /** 全部命令主键 + 别名（供 Tab 补全）。 */
    static List<String> commandKeys() {
        List<String> out = new ArrayList<>();
        for (CmdHelp c : COMMANDS) {
            out.add(c.key);
            for (String a : c.aliases) out.add(a);
        }
        return out;
    }

    /** 是否存在该命令（主键或别名）的帮助；大小写不敏感。供测试与外部查询。 */
    static boolean hasCommandHelp(String name) {
        return findCommand(name) != null;
    }

    private BalatroHelp() {
    }
}
