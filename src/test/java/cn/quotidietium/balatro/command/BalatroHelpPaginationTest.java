package cn.quotidietium.balatro.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 帮助分页约束测试：每页 ≤ 6 行（目标硬性要求）。
 */
class BalatroHelpPaginationTest {

    @Test
    void everyPageAtMostSixLines() {
        int total = BalatroHelp.totalPages();
        assertTrue(total > 0, "应至少有一页帮助");
        for (int p = 1; p <= total; p++) {
            int lines = BalatroHelp.linesFor(p).size();
            assertTrue(lines <= 6, "第 " + p + " 页有 " + lines + " 行，超过 6 行上限");
        }
    }
}
