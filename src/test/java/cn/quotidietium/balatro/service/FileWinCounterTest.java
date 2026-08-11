package cn.quotidietium.balatro.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileWinCounter 持久化与原子写测试（轮次 57）。
 *
 * <p>覆盖：递增/查询往返、跨实例重载（模拟重启）、原子写不留 .tmp 残留、
 * 脏行容错、IO 失败不崩溃。
 */
class FileWinCounterTest {

    @Test
    void incrementAndCountRoundTrip(@TempDir Path dir) {
        Path f = dir.resolve("wins.txt");
        FileWinCounter wc = new FileWinCounter(f, null);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(0, wc.count(a));
        wc.increment(a);
        wc.increment(a);
        wc.increment(b);
        assertEquals(2, wc.count(a));
        assertEquals(1, wc.count(b));
        assertEquals(2, wc.allCounts().size());
    }

    @Test
    void persistsAcrossReload(@TempDir Path dir) {
        Path f = dir.resolve("wins.txt");
        UUID a = UUID.randomUUID();
        FileWinCounter wc1 = new FileWinCounter(f, null);
        wc1.increment(a);
        wc1.increment(a);
        wc1.increment(a);
        // 模拟重启：新实例从文件恢复
        FileWinCounter wc2 = new FileWinCounter(f, null);
        assertEquals(3, wc2.count(a));
    }

    @Test
    void atomicWriteLeavesNoTmpFile(@TempDir Path dir) {
        Path f = dir.resolve("wins.txt");
        FileWinCounter wc = new FileWinCounter(f, null);
        wc.increment(UUID.randomUUID());
        assertTrue(Files.isRegularFile(f), "主文件应存在");
        assertFalse(Files.exists(dir.resolve("wins.txt.tmp")), "原子写后不得残留 .tmp 文件");
    }

    @Test
    void dirtyLinesAreSkipped(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("wins.txt");
        UUID a = UUID.randomUUID();
        Files.write(f, List.of(
                a + "=5",
                "not-a-uuid=3",
                a + "=x",
                "noequals",
                "=",
                UUID.randomUUID() + "=-2", // 非正计数丢弃
                ""), java.nio.charset.StandardCharsets.UTF_8);
        FileWinCounter wc = new FileWinCounter(f, null);
        assertEquals(5, wc.count(a), "仅合法行生效");
        assertEquals(1, wc.allCounts().size());
    }

    @Test
    void ioFailureDoesNotCrash(@TempDir Path dir) {
        // 目标路径的父目录是一个已存在的文件 → createDirectories 失败 → 记日志不抛
        Path blocker = dir.resolve("blocker");
        try {
            Files.writeString(blocker, "x");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        FileWinCounter wc = new FileWinCounter(blocker.resolve("wins.txt"), null);
        wc.increment(UUID.randomUUID()); // 不抛异常
        assertEquals(1, wc.allCounts().size(), "内存计数仍正确（IO 失败只影响持久化）");
    }
}
