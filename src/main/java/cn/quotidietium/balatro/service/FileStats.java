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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于行文本文件的持久化统计（无外部依赖）。
 * 每条记录一行：{@code player|won|anteReached|seed|deckKey|stakeIdx|epochMilli}。
 * 重启后仍保留，供 {@code MemoryLeaderboard} 排名。
 *
 * <p><b>长期运行边界</b>：内存与文件均以 {@link #MAX_RECORDS} 为上限——
 * 只保留最近 N 条（排行榜语义即"最近最佳"）。加载时若文件超限则一次性压缩重写；
 * 运行期内存恒定有界，保证 {@code top()} 的复制+排序成本可控（主线程安全）。
 *
 * <p>种子等字段来自客户端输入，上游（命令/会话层）已做字符集与长度校验；
 * 解码侧对分隔符错位/非法行一律丢弃，不让脏数据进入内存。
 */
public final class FileStats implements StatsService {

    /** 记录条数上限（内存 + 文件压缩后均不超过此值）。 */
    static final int MAX_RECORDS = 10_000;

    private final Path file;
    private final Logger logger;
    private final List<RunSummary> records = new ArrayList<>();
    /** 父目录仅在首次写入时确保一次，避免每次 record 都 stat 父目录。 */
    private boolean dirEnsured;
    /** 自上次压缩重写以来的追加次数；达 MAX_RECORDS 则触发一次 compact，限制长期运行下文件膨胀。 */
    private int appendsSinceCompact;

    public FileStats(Path file) {
        this(file, null);
    }

    public FileStats(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    @Override
    public synchronized void record(RunSummary s) {
        records.add(s);
        // 内存有界：超出上限丢弃最旧记录（文件端在下次启动加载时压缩）
        while (records.size() > MAX_RECORDS) {
            records.remove(0);
        }
        try {
            // 目录仅需确保一次（原先每次 record 都 stat 父目录，高频写入下是无谓开销）
            if (!dirEnsured) {
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                dirEnsured = true;
            }
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(encode(s));
                w.newLine();
            }
            // 文件端长期运行只增不减：每累计 MAX_RECORDS 次追加触发一次压缩重写，把文件
            // 收敛回当前内存（≤MAX），避免数周不重启时文件无限膨胀、下次启动读取爆内存。
            if (++appendsSinceCompact >= MAX_RECORDS) {
                appendsSinceCompact = 0;
                compact();
            }
        } catch (IOException e) {
            // 持久化失败不影响局内，但记日志便于排查磁盘/权限问题
            if (logger != null) logger.log(Level.WARNING, "统计写入失败：" + file, e);
        }
    }

    @Override
    public synchronized List<RunSummary> all() {
        return new ArrayList<>(records);
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        boolean oversized = false;
        // 流式读取，内存中只保留最近 MAX_RECORDS 条：原先 readAllLines 会把整个文件
        // （长期运行可能远超 MAX）一次性读入内存，造成启动内存尖峰与变慢。
        try (java.io.BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            java.util.ArrayDeque<RunSummary> tail = new java.util.ArrayDeque<>();
            String line;
            while ((line = reader.readLine()) != null) {
                RunSummary s = decode(line);
                if (s == null) continue;
                tail.addLast(s);
                if (tail.size() > MAX_RECORDS) {
                    tail.removeFirst();
                    oversized = true;
                }
            }
            records.addAll(tail);
        } catch (IOException e) {
            if (logger != null) logger.log(Level.WARNING, "统计读取失败（从空记录开始）：" + file, e);
            return;
        }
        // 超限则压缩重写，避免文件与下次启动耗时无限增长
        if (oversized) compact();
    }

    /** 用当前内存记录重写文件（启动时检测到超限后调用一次）。 */
    private void compact() {
        try {
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (RunSummary s : records) {
                    w.write(encode(s));
                    w.newLine();
                }
            }
            if (logger != null) logger.info("统计文件已压缩至最近 " + records.size() + " 条：" + file);
        } catch (IOException e) {
            if (logger != null) logger.log(Level.WARNING, "统计文件压缩失败（不影响运行）：" + file, e);
        }
    }

    private static String encode(RunSummary s) {
        return s.playerId() + "|" + s.won() + "|" + s.anteReached() + "|" + s.seed() + "|"
                + s.deckKey() + "|" + s.stakeIdx() + "|" + s.epochMilli();
    }

    private static RunSummary decode(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length != 7) return null; // 严格 7 段：拒绝被分隔符污染的脏行
        try {
            String seed = p[3];
            // 展示与解析防御：截断超长种子、拒绝含控制字符的脏数据（历史文件兼容）
            if (seed.length() > 64 || hasControlChars(seed)) return null;
            return new RunSummary(UUID.fromString(p[0]), Boolean.parseBoolean(p[1]), Integer.parseInt(p[2]),
                    seed, p[4], Integer.parseInt(p[5]), Long.parseLong(p[6]));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasControlChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < 0x20) return true;
        }
        return false;
    }
}
