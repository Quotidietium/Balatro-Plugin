package cn.quotidietium.balatro.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.api.RunSummary;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * FileStats/FileWinCounter IO 异常处理测试（轮次 41）。
 *
 * <p>验证磁盘满/权限不足/路径无效时，record/load 不崩溃、记日志、返回空/默认。
 * <p>R108：临时目录自管（替代 @TempDir，规避 Windows 清理竞态假红，见 TempTestDirs）。
 */
class FileIOFailureTest {

    @Test
    void fileStatsWithInvalidPathDoesNotCrash() {
        // 使用一个不可能的路径（Windows 保留名模拟权限问题）
        Path badPath = Path.of("Z:/nonexistent_drive_" + System.nanoTime() + "/stats.txt");
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("test");
        // 构造不崩溃——load 找不到文件返回空
        FileStats stats = assertDoesNotThrow(() -> new FileStats(badPath, logger));
        // record 尝试写入失败但记日志不崩溃
        UUID p = UUID.randomUUID();
        RunSummary rs = new RunSummary(p, true, 8, "S", "red", 0, 1);
        assertDoesNotThrow(() -> stats.record(rs), "record 应在 IO 失败时不崩溃");
        // 内存中仍有记录（record 先加内存再写文件），但重载后应为空（文件没写入）
        assertEquals(1, stats.all().size(), "内存中应有 1 条记录（IO 失败不影响内存）");
        // 重载验证文件确实没写入
        FileStats reloaded = new FileStats(badPath, logger);
        assertTrue(reloaded.all().isEmpty(), "重载后应为空（文件未写入）");
    }

    @Test
    void fileStatsNormalRoundTrip() {
        Path dir = TempTestDirs.newDir();
        try {
            Path file = dir.resolve("stats.txt");
            FileStats stats = new FileStats(file, null);
            UUID p = UUID.randomUUID();
            RunSummary rs = new RunSummary(p, true, 8, "SEED1", "red", 0, 1234567890L);
            stats.record(rs);
            // 重新加载验证持久化
            FileStats reloaded = new FileStats(file, null);
            assertEquals(1, reloaded.all().size(), "重载后应读回 1 条记录");
            assertEquals("SEED1", reloaded.all().get(0).seed());
        } finally {
            TempTestDirs.rm(dir);
        }
    }

    @Test
    void fileStatsCorruptedLineSkipped() {
        Path dir = TempTestDirs.newDir();
        try {
            Path file = dir.resolve("corrupt.txt");
            // 写入正常行 + 脏行
            try {
                java.nio.file.Files.writeString(file,
                    "00000000-0000-0000-0000-000000000001|true|8|GOOD|red|0|100\n" +
                    "CORRUPT_LINE_WITH_NO_PIPES\n" +
                    "00000000-0000-0000-0000-000000000002|false|5|BAD|blue|0|200\n",
                    java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                // 跳过——测试环境无写入权限
                return;
            }
            FileStats stats = new FileStats(file, null);
            assertEquals(2, stats.all().size(), "脏行应被跳过，保留 2 条正常记录");
            assertEquals("GOOD", stats.all().get(0).seed());
        } finally {
            TempTestDirs.rm(dir);
        }
    }

    @Test
    void fileWinCounterWithInvalidPathDoesNotCrash() {
        Path badPath = Path.of("Z:/nonexistent_drive_" + System.nanoTime() + "/wins.txt");
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("test");
        FileWinCounter fwc = assertDoesNotThrow(() -> new FileWinCounter(badPath, logger));
        UUID p = UUID.randomUUID();
        // increment 尝试写入失败但记日志不崩溃
        assertDoesNotThrow(() -> fwc.increment(p), "increment 应在 IO 失败时不崩溃");
        // count 返回内存中的值（即使文件没写入）
        assertEquals(1, fwc.count(p), "内存中应有 1 次记录");
    }

    @Test
    void fileWinCounterNormalRoundTrip() {
        Path dir = TempTestDirs.newDir();
        try {
            Path file = dir.resolve("wins.txt");
            FileWinCounter fwc = new FileWinCounter(file, null);
            UUID p = UUID.randomUUID();
            fwc.increment(p);
            fwc.increment(p);
            fwc.increment(p);
            // 重载验证持久化
            FileWinCounter reloaded = new FileWinCounter(file, null);
            assertEquals(3, reloaded.count(p), "重载后应有 3 次通关计数");
        } finally {
            TempTestDirs.rm(dir);
        }
    }

    @Test
    void fileWinCounterCorruptedLineSkipped() {
        Path dir = TempTestDirs.newDir();
        try {
            Path file = dir.resolve("wins_corrupt.txt");
            try {
                java.nio.file.Files.writeString(file,
                    "00000000-0000-0000-0000-000000000001=5\n" +
                    "CORRUPT_NO_EQUALS\n" +
                    "00000000-0000-0000-0000-000000000002=3\n",
                    java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                return;
            }
            FileWinCounter fwc = new FileWinCounter(file, null);
            assertEquals(5, fwc.count(UUID.fromString("00000000-0000-0000-0000-000000000001")));
            assertEquals(3, fwc.count(UUID.fromString("00000000-0000-0000-0000-000000000002")));
        } finally {
            TempTestDirs.rm(dir);
        }
    }
}
