package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.RunSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileStats 持久化与长期运行边界测试。
 *
 * <p>覆盖：往返一致性、超大文件流式有界加载 + 压缩、脏行丢弃、父目录自动创建。
 * <p>R108：临时目录自管（替代 @TempDir，规避 Windows 清理竞态假红，见 TempTestDirs）。
 */
class FileStatsTest {

    /** 构造一条可区分的记录（k 决定 seed/ante/stake，UUID 随机）。 */
    private static RunSummary run(int k) {
        return new RunSummary(UUID.randomUUID(), k % 2 == 0, k, "seed" + k, "red", k % 8,
                1_700_000_000_000L + k);
    }

    /** 与 FileStats.encode 一致的行格式（encode 为私有，测试侧复刻）。 */
    private static String line(RunSummary s) {
        return s.playerId() + "|" + s.won() + "|" + s.anteReached() + "|" + s.seed() + "|"
                + s.deckKey() + "|" + s.stakeIdx() + "|" + s.epochMilli();
    }

    @Test
    void roundTrip() {
        Path dir = TempTestDirs.newDir();
        try {
            Path f = dir.resolve("stats.txt");
            FileStats a = new FileStats(f);
            RunSummary r1 = run(1), r2 = run(2), r3 = run(3);
            a.record(r1);
            a.record(r2);
            a.record(r3);

            List<RunSummary> all = new FileStats(f).all(); // 重新从文件加载
            assertEquals(3, all.size(), "应往返出 3 条");
            assertEquals(r1, all.get(0), "顺序应保持：最旧在前");
            assertEquals(r2, all.get(1));
            assertEquals(r3, all.get(2));
        } finally {
            TempTestDirs.rm(dir);
        }
    }

    @Test
    void loadBoundsOversizedFileAndCompacts() throws Exception {
        Path dir = TempTestDirs.newDir();
        try {
            Path f = dir.resolve("stats.txt");
            int extra = 10;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < FileStats.MAX_RECORDS + extra; i++) {
                sb.append(line(run(i))).append('\n');
            }
            Files.writeString(f, sb.toString());

            FileStats fs = new FileStats(f);
            List<RunSummary> all = fs.all();
            assertEquals(FileStats.MAX_RECORDS, all.size(), "应只保留最近 MAX_RECORDS 条");

            // 文件应被压缩重写为 ≤MAX 行（流式加载不应把整个超大文件读入内存）
            long lineCount = Files.lines(f).count();
            assertTrue(lineCount <= FileStats.MAX_RECORDS, "文件应被压缩，实际行数 " + lineCount);
        } finally {
            TempTestDirs.rm(dir);
        }
    }

    @Test
    void malformedLinesRejected() throws Exception {
        Path dir = TempTestDirs.newDir();
        try {
            Path f = dir.resolve("stats.txt");
            String valid = line(run(1));
            // 混入：垃圾整行、段数不对、空 UUID
            Files.writeString(f, "garbage line\n" + valid + "\n" + "a|b|c\n" + "|false|3|s|red|0|1\n");

            List<RunSummary> all = new FileStats(f).all();
            assertEquals(1, all.size(), "只有一条合法行应被加载，脏行一律丢弃");
            assertEquals("seed1", all.get(0).seed());
        } finally {
            TempTestDirs.rm(dir);
        }
    }

    @Test
    void recordCreatesParentDir() {
        Path dir = TempTestDirs.newDir();
        try {
            Path f = dir.resolve("nested/deep/stats.txt");
            FileStats fs = new FileStats(f);
            fs.record(run(1));
            assertTrue(Files.isRegularFile(f), "应自动创建父目录并写入文件");
            assertEquals(1, new FileStats(f).all().size());
        } finally {
            TempTestDirs.rm(dir);
        }
    }
}
