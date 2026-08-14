package cn.quotidietium.balatro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.api.RunSummary;
import cn.quotidietium.balatro.api.service.StatsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 持久化与服务注册表的并发压力测试（R104）。
 *
 * <p>生产中 FileStats/FileWinCounter 由主线程调用，但二者是公开 API（第三方插件可从异步
 * 线程调用）；{@link Services} 的字段全部 volatile 并承诺「运行期替换、其他线程立即可见」
 * ——这些线程安全声明此前只有设计核验（R25/R53），无行为级压测。
 *
 * <p>覆盖：
 * <ul>
 *   <li>FileStats 并发 record + 并发 all()：40 线程 4000 条（< MAX_RECORDS 无淘汰）无丢失、
 *       无异常，落盘后重载条数一致（synchronized 保证追加不交错撕裂）；</li>
 *   <li>FileWinCounter 并发 increment + 并发 count()：2000 次自增零丢失、重载一致；</li>
 *   <li>Services 并发读写 + 运行期重绑：读线程持续 stats().record/leaderboard().top 的同时
 *       主线程反复 setStats/setWinCounter——无 NPE/异常（volatile 可见性 + 重绑一致性）。</li>
 * </ul>
 *
 * <p>临时目录自管（Files.createTempDirectory + best-effort 清理），规避 JUnit @TempDir
 * 在 Windows 上的清理竞态偶发（R102 记录）。
 */
class ServiceConcurrencyStressTest {

    @Test
    void fileStatsConcurrentRecordAndReadLosesNothing() throws Exception {
        Path dir = Files.createTempDirectory("balatro-stress");
        try {
            Path file = dir.resolve("stats.txt");
            FileStats fs = new FileStats(file, null);
            int threads = 8, perThread = 500;
            ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
            runConcurrent(threads + 4, errors, t -> {
                if (t < threads) {
                    for (int i = 0; i < perThread; i++) {
                        fs.record(new RunSummary(UUID.randomUUID(), t % 2 == 0, 1 + (i % 8),
                                "S" + t + "x" + i, "red", 0, System.currentTimeMillis()));
                    }
                } else {
                    // 4 个读线程持续快照，直到写线程完成（读不阻塞写、不抛异常即可）
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                    int spun = 0;
                    while (spun < 200_000 && System.nanoTime() < deadline) {
                        List<RunSummary> snap = fs.all();
                        if (snap.size() > 10_000) errors.add(new AssertionError("快照超界 " + snap.size()));
                        spun++;
                    }
                }
            });
            assertTrue(errors.isEmpty(), "并发 record/all 异常: " + errors.poll());
            assertEquals(threads * perThread, fs.all().size(), "并发写入不得丢失（<MAX_RECORDS 无淘汰）");
            // 落盘完整性：重载条数一致（每条 record 为锁内单行追加，不应交错撕裂）
            assertEquals(threads * perThread, new FileStats(file, null).all().size(), "落盘重载条数应一致");
        } finally {
            rmBestEffort(dir);
        }
    }

    @Test
    void fileWinCounterConcurrentIncrementLosesNothing() throws Exception {
        Path dir = Files.createTempDirectory("balatro-wc");
        try {
            Path file = dir.resolve("wins.txt");
            FileWinCounter wc = new FileWinCounter(file, null);
            UUID id = UUID.randomUUID();
            int threads = 8, perThread = 250;
            ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
            runConcurrent(threads + 2, errors, t -> {
                if (t < threads) {
                    for (int i = 0; i < perThread; i++) wc.increment(id);
                } else {
                    for (int i = 0; i < 100_000; i++) wc.count(id);
                }
            });
            assertTrue(errors.isEmpty(), "并发 increment/count 异常: " + errors.poll());
            assertEquals(threads * perThread, wc.count(id), "并发自增不得丢失");
            assertEquals(threads * perThread, new FileWinCounter(file, null).count(id), "落盘重载应一致");
        } finally {
            rmBestEffort(dir);
        }
    }

    @Test
    void servicesRebindUnderConcurrentAccessNeverThrows() throws Exception {
        Services svc = new Services();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        UUID p = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        MemoryStats alt1 = new MemoryStats();
        MemoryStats alt2 = new MemoryStats();
        FileWinCounter wc = new FileWinCounter(Files.createTempDirectory("balatro-rb").resolve("w.txt"), null);
        try {
            // 4 个访问线程：持续 record + top + topAggregated（经 Services 当前实例）
            for (int t = 0; t < 4; t++) {
                pool.submit(() -> {
                    await(start);
                    // 2000×4 = 8000 条记录 + 8000 次 top/topAggregated（全量排序，负载可控）
                    for (int i = 0; i < 2_000; i++) {
                        try {
                            StatsService st = svc.stats();
                            st.record(new RunSummary(p, i % 3 == 0, 1 + (i % 8), "RB" + i, "red", 0,
                                    System.currentTimeMillis()));
                            svc.leaderboard().top(10);
                            svc.leaderboard().topAggregated(10);
                        } catch (RuntimeException ex) {
                            errors.add(ex);
                        }
                    }
                });
            }
            // 主线程：反复重绑 stats/winCounter（volatile 可见性 + MemoryLeaderboard 重绑一致性）
            start.countDown();
            for (int i = 0; i < 2_000; i++) {
                svc.setStats(i % 2 == 0 ? alt1 : alt2);
                svc.setWinCounter(wc);
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "访问线程应正常结束");
        }
        assertTrue(errors.isEmpty(), "重绑期间访问异常: " + errors.poll());
        // 终态：stats 为最后一次 setStats 的实例（alt2），重绑生效
        assertTrue(svc.stats() == alt2, "终态应为最后一次 setStats 的实例");
    }

    /** 并发执行 job；任何线程抛出的异常都被收集（不得静默丢失）。 */
    private static void runConcurrent(int threads, ConcurrentLinkedQueue<Throwable> errors,
                                      java.util.function.IntConsumer job) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                pool.submit(() -> {
                    await(start);
                    try {
                        job.accept(tid);
                    } catch (Throwable ex) {
                        errors.add(ex);
                    }
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "工作线程应正常结束");
        }
    }

    /** 静默等待栅栏（被中断则直接返回，本测试不依赖中断语义）。 */
    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** best-effort 递归删除（Windows 下文件可能被短暂锁定，失败忽略）。 */
    private static void rmBestEffort(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
        }
    }
}
