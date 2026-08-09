package cn.quotidietium.balatro.api.service;

import cn.quotidietium.balatro.api.RunSummary;
import java.util.List;

/**
 * 局结果统计。默认 {@code MemoryStats} 仅存内存；后续可替换为持久化（SQLite/YAML）实现。
 * 排名（{@link LeaderboardProvider}）通常基于此。
 */
public interface StatsService {

    /** 记录一局结束。 */
    void record(RunSummary summary);

    /** 全部历史记录（顺序不保证，由实现决定）。 */
    List<RunSummary> all();
}
