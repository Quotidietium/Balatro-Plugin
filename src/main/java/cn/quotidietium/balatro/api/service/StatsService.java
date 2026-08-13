package cn.quotidietium.balatro.api.service;

import cn.quotidietium.balatro.api.RunSummary;
import java.util.List;

/**
 * 局结果统计。默认 {@code MemoryStats} 仅存内存；后续可替换为持久化（SQLite/YAML）实现。
 * 排名（{@link LeaderboardProvider}）通常基于此。
 *
 * <p><b>实现约束</b>：{@link #record} 与 {@link #all} 均在 Bukkit 主线程调用，
 * 实现须保证快速返回（内存操作或缓存的快照）。若需数据库 I/O，请在内部异步写入、
 * 主线程仅读写内存缓存——否则会阻塞主线程导致 TPS 下降。
 */
public interface StatsService {

    /** 记录一局结束。 */
    void record(RunSummary summary);

    /** 全部历史记录（顺序不保证，由实现决定）。 */
    List<RunSummary> all();
}
