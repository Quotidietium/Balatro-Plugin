package cn.quotidietium.balatro.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * /balatro help <命令名> 的命令详情查找测试：
 * 主键、别名均可命中；未知命令返回 false。
 */
class BalatroHelpCommandTest {

    @Test
    void primaryKeyResolves() {
        for (String k : new String[]{
                "gui", "play", "playcard", "disc", "status", "shop", "buy", "buybag",
                "buyvoucher", "reroll", "next", "go", "skip", "cons", "use",
                "packs", "pick", "skipack", "sellj", "sellc", "endless", "top",
                "quit", "help", "version"}) {
            assertTrue(BalatroHelp.hasCommandHelp(k), "应有命令帮助：" + k);
        }
    }

    @Test
    void aliasResolves() {
        // 别名应解析到对应命令
        assertTrue(BalatroHelp.hasCommandHelp("pc"), "pc → playcard");
        assertTrue(BalatroHelp.hasCommandHelp("discard"), "discard → disc");
        assertTrue(BalatroHelp.hasCommandHelp("hand"), "hand → status");
        assertTrue(BalatroHelp.hasCommandHelp("pack"), "pack → buybag");
        assertTrue(BalatroHelp.hasCommandHelp("voucher"), "voucher → buyvoucher");
        assertTrue(BalatroHelp.hasCommandHelp("consumables"), "consumables → cons");
        assertTrue(BalatroHelp.hasCommandHelp("?"), "? → help");
        assertTrue(BalatroHelp.hasCommandHelp("menu"), "menu → gui");
        assertTrue(BalatroHelp.hasCommandHelp("ver"), "ver → version");
    }

    @Test
    void unknownCommandDoesNotResolve() {
        assertFalse(BalatroHelp.hasCommandHelp("fly"));
        assertFalse(BalatroHelp.hasCommandHelp(""));
        assertFalse(BalatroHelp.hasCommandHelp("notacommand"));
    }

    @Test
    void caseInsensitive() {
        assertTrue(BalatroHelp.hasCommandHelp("PLAY"));
        assertTrue(BalatroHelp.hasCommandHelp("Help"));
    }

    /**
     * R147：分页帮助的内容覆盖锁——全部 15 牌组 key 与 20 挑战 key 必须出现在分页
     * 帮助中。本轮曾发现 5 处挑战速记停留在 R123 真版对齐前的旧措辞（巨石阵「全石头
     * 牌」/点火升空「$0」/五张抽牌「必出5张」/金针「奖励×3」/残酷「减半」——与实现
     * 的 mods 全都不符）；此类漂移的结构性根源是「新增/改动内容后帮助页无覆盖检查」。
     */
    @Test
    void paginatedHelpCoversAllDeckAndChallengeKeys() {
        StringBuilder all = new StringBuilder();
        for (int p = 1; p <= BalatroHelp.totalPages(); p++) {
            for (String line : BalatroHelp.linesFor(p)) all.append(line).append('\n');
        }
        for (var d : cn.quotidietium.balatro.engine.Data.DECKS) {
            assertTrue(all.indexOf(d.key()) >= 0, "分页帮助应覆盖牌组 " + d.key());
        }
        for (var c : cn.quotidietium.balatro.engine.Data.CHALLENGES) {
            assertTrue(all.indexOf(c.key()) >= 0, "分页帮助应覆盖挑战 " + c.key());
        }
    }
}
