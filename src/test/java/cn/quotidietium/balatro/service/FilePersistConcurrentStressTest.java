package cn.quotidietium.balatro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.api.RunSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * FileStats/FileWinCounter 多线程高频并发写压测（R228）。
 *
 * <p>生产路径在主线程串行调用，但 record/all/increment 均声明 synchronized 作为
 * 防御纵深——本锁验证该纵深真实成立：多线程并发写后重载，文件零撕裂/零交错/零丢行
 * （重载结果 == 原实例内存态），压缩触发点（累计 MAX_RECORDS 追加）在并发下不破坏
 * 不变式。撕裂行会被 decode 静默丢弃，故「重载=内存」是数据零丢失的强断言。
 *
 * <p>R108：临时目录自管（替代 @TempDir，规避 Windows 清理竞态假红）。
 */
class FilePersistConcurrentStressTest {

    private static RunSummary sum(UUID p, int seq) {
        return new RunSummary(p, seq % 2 == 0, 1 + seq % 8, "SEED" + seq, "red", 0,
                1700000000000L + seq);
    }

    @Test
    void concurrentStatsStressZeroTornLines() throws Exception {
        Path dir = TempTestDirs.newDir();
        try {
            Path f = dir.resolve("stats-stress.txt");
            FileStats fs = new FileStats(f);
            int threads = 8, perThread = 1500; // 共 12000 > MAX_RECORDS(10000)：触发内存裁剪 + 运行期压缩
            UUID[] ids = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    final int tid = t;
                    futures.add(pool.submit(() -> {
                        try {
                            start.await();
                            for (int i = 0; i < perThread; i++) {
                                fs.record(sum(ids[(tid + i) % ids.length], tid * perThread + i));
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }));
                }
                start.countDown();
                for (Future<?> fu : futures) fu.get(60, TimeUnit.SECONDS);
            } finally {
                pool.shutdown();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }

            List<RunSummary> mem = fs.all();
            assertTrue(mem.size() <= FileStats.MAX_RECORDS, "内存有界：actual=" + mem.size());
            // 重载零丢行：文件重载结果与原实例内存态完全一致（任何撕裂/交错行都会被
            // decode 丢弃导致两边不等）
            FileStats reloaded = new FileStats(f);
            assertEquals(mem, reloaded.all(), "并发写后重载应与内存态一致（零撕裂/零交错/零丢行）");
        } finally {
            TempTestDirs.rm(dir);
        }
    }

    @Test
    void concurrentRecordWithConcurrentReaders() throws Exception {
        Path dir = TempTestDirs.newDir();
        try {
            Path f = dir.resolve("stats-rw.txt");
            FileStats fs = new FileStats(f);
            int writers = 4, readers = 2, per = 800;
            ExecutorService pool = Executors.newFixedThreadPool(writers + readers);
            try {
                CountDownLatch start = new CountDownLatch(1);
                CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();
                List<Future<?>> futures = new ArrayList<>();
                for (int t = 0; t < writers; t++) {
                    futures.add(pool.submit(() -> {
                        try {
                            start.await();
                            UUID p = UUID.randomUUID();
                            for (int i = 0; i < per; i++) fs.record(sum(p, i));
                        } catch (Throwable e) { errors.add(e); }
                    }));
                }
                for (int t = 0; t < readers; t++) {
                    futures.add(pool.submit(() -> {
                        try {
                            start.await();
                            for (int i = 0; i < 200; i++) {
                                List<RunSummary> snap = fs.all();
                                if (snap.size() > FileStats.MAX_RECORDS) {
                                    errors.add(new IllegalStateException("读快照超界: " + snap.size()));
                                }
                                Thread.sleep(2);
                            }
                        } catch (Throwable e) { errors.add(e); }
                    }));
                }
                start.countDown();
                for (Future<?> fu : futures) fu.get(60, TimeUnit.SECONDS);
                assertEquals(List.of(), errors, "并发读写不得抛异常");
            } finally {
                pool.shutdown();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }
            assertEquals(writers * per, fs.all().size(), "未达上限前零丢失");
        } finally {
            TempTestDirs.rm(dir);
        }
    }

    @Test
    void concurrentWinCounterCountsExact() throws Exception {
        Path dir = TempTestDirs.newDir();
        try {
            Path f = dir.resolve("wins-stress.txt");
            FileWinCounter wc = new FileWinCounter(f, null);
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            int threads = 8, per = 400;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    futures.add(pool.submit(() -> {
                        try {
                            start.await();
                            for (int i = 0; i < per; i++) wc.increment(i % 2 == 0 ? a : b);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }));
                }
                start.countDown();
                for (Future<?> fu : futures) fu.get(60, TimeUnit.SECONDS);
            } finally {
                pool.shutdown();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }

            assertEquals(threads * per / 2, wc.count(a), "并发 increment 计数精确（a）");
            assertEquals(threads * per / 2, wc.count(b), "并发 increment 计数精确（b）");
            // 重载一致（原子重写不丢）
            FileWinCounter re = new FileWinCounter(f, null);
            assertEquals(wc.count(a), re.count(a));
            assertEquals(wc.count(b), re.count(b));
            // 文件每行都是 uuid=count 形态（无撕裂）
            List<String> lines = Files.readAllLines(f);
            assertEquals(2, lines.size(), "恰好两行（每 uuid 一行）");
            for (String ln : lines) assertTrue(ln.matches("[0-9a-f-]{36}=\\d+"), "行形态: " + ln);
        } finally {
            TempTestDirs.rm(dir);
        }
    }
}
