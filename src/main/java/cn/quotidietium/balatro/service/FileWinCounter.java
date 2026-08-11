package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.service.WinCounter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于行文本文件的通关计数器（每行 {@code uuid=count}）。
 *
 * <p>与 {@link FileStats} 同目录、同样的长期运行策略：内存有界、IO 失败记日志不崩溃。
 * 文件在每次 increment 后追加；计数远小于统计记录数（仅通关局），不需要压缩。
 */
public final class FileWinCounter implements WinCounter {

    private final Path file;
    private final Logger logger;
    private final Map<UUID, Integer> counts = new HashMap<>();
    private boolean dirEnsured;

    public FileWinCounter(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    @Override
    public synchronized void increment(UUID player) {
        int c = counts.getOrDefault(player, 0) + 1;
        counts.put(player, c);
        try {
            if (!dirEnsured) {
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                dirEnsured = true;
            }
            // 整文件重写（计数器条目数 = 玩家数，远小于统计记录数，重写开销可忽略）
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (var e : counts.entrySet()) {
                    w.write(e.getKey().toString() + "=" + e.getValue());
                    w.newLine();
                }
            }
        } catch (IOException e) {
            if (logger != null) logger.log(Level.WARNING, "通关计数写入失败：" + file, e);
        }
    }

    @Override
    public synchronized int count(UUID player) {
        return counts.getOrDefault(player, 0);
    }

    /** 全部玩家计数（供聚合排行榜使用）。 */
    public synchronized Map<UUID, Integer> allCounts() {
        return new HashMap<>(counts);
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0 || eq >= line.length() - 1) continue;
                try {
                    UUID id = UUID.fromString(line.substring(0, eq));
                    int c = Integer.parseInt(line.substring(eq + 1));
                    if (c > 0) counts.put(id, c);
                } catch (RuntimeException ignored) {
                    // 脏行跳过
                }
            }
        } catch (IOException | RuntimeException e) {
            if (logger != null) logger.log(Level.WARNING, "通关计数读取失败（从空开始）：" + file, e);
        }
    }
}
