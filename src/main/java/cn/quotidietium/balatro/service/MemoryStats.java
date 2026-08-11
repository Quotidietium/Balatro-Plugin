package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.RunSummary;
import cn.quotidietium.balatro.api.service.StatsService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 默认统计：内存存储（重启丢失）。后续替换为持久化实现。 */
public final class MemoryStats implements StatsService {

    /** 记录条数上限（防止第三方使用此默认实现时内存无限增长）。 */
    private static final int MAX_RECORDS = 10_000;
    private final List<RunSummary> records = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void record(RunSummary summary) {
        synchronized (records) {
            records.add(summary);
            while (records.size() > MAX_RECORDS) {
                records.remove(0);
            }
        }
    }

    @Override
    public List<RunSummary> all() {
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }
}
