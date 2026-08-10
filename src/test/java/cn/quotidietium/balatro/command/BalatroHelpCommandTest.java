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
                "play", "playcard", "disc", "status", "shop", "buy", "buybag",
                "buyvoucher", "reroll", "next", "go", "skip", "cons", "use",
                "packs", "pick", "skipack", "sellj", "sellc", "endless", "top",
                "quit", "help"}) {
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
}
