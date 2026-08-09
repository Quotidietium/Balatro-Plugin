package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.RunSummary;
import cn.quotidietium.balatro.api.service.StatsService;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 基于行文本文件的持久化统计（无外部依赖）。
 * 每条记录一行：{@code player|won|anteReached|seed|deckKey|stakeIdx|epochMilli}。
 * 重启后仍保留，供 {@code MemoryLeaderboard} 排名。
 */
public final class FileStats implements StatsService {

    private final Path file;
    private final List<RunSummary> records = new ArrayList<>();

    public FileStats(Path file) {
        this.file = file;
        load();
    }

    @Override
    public synchronized void record(RunSummary s) {
        records.add(s);
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(encode(s));
                w.newLine();
            }
        } catch (IOException e) {
            // 持久化失败不影响局内
        }
    }

    @Override
    public synchronized List<RunSummary> all() {
        return new ArrayList<>(records);
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                RunSummary s = decode(line);
                if (s != null) records.add(s);
            }
        } catch (IOException e) {
            // 读取失败则从空开始
        }
    }

    private static String encode(RunSummary s) {
        return s.playerId() + "|" + s.won() + "|" + s.anteReached() + "|" + s.seed() + "|"
                + s.deckKey() + "|" + s.stakeIdx() + "|" + s.epochMilli();
    }

    private static RunSummary decode(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 7) return null;
        try {
            return new RunSummary(UUID.fromString(p[0]), Boolean.parseBoolean(p[1]), Integer.parseInt(p[2]),
                    p[3], p[4], Integer.parseInt(p[5]), Long.parseLong(p[6]));
        } catch (Exception e) {
            return null;
        }
    }
}
