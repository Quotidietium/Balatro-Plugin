package cn.quotidietium.balatro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.api.RunSummary;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * R140：崩溃撕裂文件自愈测试（FileStats 追加模式的断电窗口）。
 *
 * <p>缺陷（#70）：追加写在「内容已 flush、换行未落」处崩溃会留下**无换行尾行**——
 * 重启加载时该残行被 decode 拒绝（可接受），但下一次 record 的追加会**拼接在残行尾部**，
 * 残行与首条新记录并成一条脏行，下轮加载时两条全丢（数据操作 BUG）。
 * 修复：load 检测末字节非 '\n' 即 compact 归一化。本测试锁定三类崩溃形态。
 */
class ServiceCrashTornFileTest {

    private static final String GOOD1 = "00000000-0000-0000-0000-000000000001|true|8|GOOD1|red|0|100";
    private static final String GOOD2 = "00000000-0000-0000-0000-000000000002|false|5|GOOD2|blue|0|200";

    /** 撕裂半行（无换行）：残行丢弃 + 归一化后新追加不与残行拼接（旧行为丢首条新记录）。 */
    @Test
    void tornPartialTailIsHealedAndNextAppendStaysIntact() throws Exception {
        Path dir = TempTestDirs.newDir();
        try {
            Path file = dir.resolve("torn_partial.txt");
            // 模拟崩溃：GOOD2 的行只写了一半且无换行
            java.nio.file.Files.writeString(file,
                    GOOD1 + "\n" + GOOD2.substring(0, 20), StandardCharsets.UTF_8);
            FileStats stats = new FileStats(file, null);
            assertEquals(1, stats.all().size(), "残行应被丢弃，GOOD1 保留");

            // 修复点：归一化后新记录独立成行；旧实现会把新记录拼到残行尾部→两条全丢
            stats.record(new RunSummary(UUID.randomUUID(), true, 8, "AFTER", "red", 0, 300L));
            FileStats reloaded = new FileStats(file, null);
            assertEquals(2, reloaded.all().size(), "修复后新记录必须独立存活（R140 #70）");
            assertEquals("AFTER", reloaded.all().get(1).seed());
        } finally {
            TempTestDirs.rm(dir);
        }
    }

    /** 完整记录但缺换行（内容写完、newLine 未落）：记录应恢复且归一化，后续追加安全。 */
    @Test
    void completeRecordWithoutNewlineRecoveredAndNormalized() throws Exception {
        Path dir = TempTestDirs.newDir();
        try {
            Path file = dir.resolve("torn_complete.txt");
            java.nio.file.Files.writeString(file, GOOD1 + "\n" + GOOD2, StandardCharsets.UTF_8);
            FileStats stats = new FileStats(file, null);
            assertEquals(2, stats.all().size(), "完整无换行尾行应被恢复");
            assertEquals("GOOD2", stats.all().get(1).seed());

            stats.record(new RunSummary(UUID.randomUUID(), true, 8, "NEXT", "red", 0, 400L));
            FileStats reloaded = new FileStats(file, null);
            assertEquals(3, reloaded.all().size(), "归一化后新记录独立存活");
        } finally {
            TempTestDirs.rm(dir);
        }
    }

    /** 遗留 .tmp（compact 中途崩溃）不影响加载；下次 compact 直接覆写复用。 */
    @Test
    void leftoverTmpFileIsIgnored() throws Exception {
        Path dir = TempTestDirs.newDir();
        try {
            Path file = dir.resolve("stats.txt");
            java.nio.file.Files.writeString(file, GOOD1 + "\n", StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(file.resolveSibling("stats.txt.tmp"),
                    "GARBAGE_TORN_TMP", StandardCharsets.UTF_8);
            FileStats stats = new FileStats(file, null);
            assertEquals(1, stats.all().size(), "遗留 .tmp 不应影响加载");
            stats.record(new RunSummary(UUID.randomUUID(), false, 3, "OK", "red", 0, 500L));
            FileStats reloaded = new FileStats(file, null);
            assertEquals(2, reloaded.all().size());
            assertTrue(reloaded.all().stream().anyMatch(r -> "OK".equals(r.seed())));
        } finally {
            TempTestDirs.rm(dir);
        }
    }
}
