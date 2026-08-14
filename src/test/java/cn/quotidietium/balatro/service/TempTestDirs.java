package cn.quotidietium.balatro.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * 测试用自管临时目录（R108）。
 *
 * <p>替代 JUnit {@code @TempDir}：其在 Windows 上偶发清理竞态（DirectoryNotEmptyException，
 * 杀毒/索引器短暂占用目录——本会话两次触发假红），best-effort 清理失败仅吞掉，
 * 不再让测试套件因基础设施抖动误报失败。
 */
final class TempTestDirs {

    /** 创建一个全新临时目录。 */
    static Path newDir() {
        try {
            return Files.createTempDirectory("balatro-test");
        } catch (IOException e) {
            throw new IllegalStateException("无法创建临时目录", e);
        }
    }

    /** best-effort 递归删除（失败忽略——OS 临时区最终会清理）。 */
    static void rm(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private TempTestDirs() {
    }
}
