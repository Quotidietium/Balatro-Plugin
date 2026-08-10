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
                "开局：§e/balatro play [牌组] [赌注] [挑战] [种子]",
                "参数顺序不限：牌组名/赌注数字/挑战名会自动识别，其余视作种子。",
                "翻页：§e/balatro help <页码>§f（每页不超过 6 行）。"
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
                "§eomelette§f煎蛋卷(5蛋/无收入) §ecity15§f十五分钟城市(满员/免费重掷)",
                "§erich§f富者愈富(利息翻倍) §eknife§f刀尖行走(目标分×1.5)",
                "§exray§fX光视界(牌朝上/-手牌) §emadworld§f疯狂世界(每底注2Boss)",
                "§eluxury§f奢侈品税(每小丑+10%分) §enonperish§f永不过期(全永恒/6折)",
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
                "§e/balatro next§f   离开商店，进入下一盲注"
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
                "§e/balatro top§f   查看小丑牌排行榜（通关 > 底注 > 时间）",
                "§e/balatro help <页码>§f   查看指定页帮助",
                "§e/balatro§f（无参数）   显示简要帮助"
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
        Page p = PAGES.get(page - 1);
        sender.sendMessage("§6━━ 小丑牌 · 帮助 §e" + page + "/" + PAGES.size() + " §6· " + p.title + " ━━");
        for (String line : p.body) {
            sender.sendMessage("§f" + line);
        }
        return true;
    }

    private BalatroHelp() {
    }
}
