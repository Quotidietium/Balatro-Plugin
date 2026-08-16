package cn.quotidietium.balatro.bench;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Properties;

/**
 * 单场景测量器：预热若干批后测量若干批，逐批记录 ns/op 与 分配字节/op，
 * 汇总为 中位数/最小值/均值/p95，写入 Properties 结果文件。
 *
 * <p>方法学说明（对比报告同样引用）：
 * <ul>
 *   <li>每批工作量固定且由固定种子驱动 ⇒ 优化（行为不变，458 测试锁定）前后
 *       执行的指令序列语义等价，ns/op 直接可比；</li>
 *   <li>取多批中位数抗环境噪声（后台进程/CPU 频率漂移），最小值作参考下界；</li>
 *   <li>分配字节经 {@code com.sun.management.ThreadMXBean#getThreadAllocatedBytes}
 *       （TLAB 估算，含误差），用于观测分配削减趋势而非精确值；</li>
 *   <li>Blackhole.SINK 汇总全部计算结果，末尾打印防死码消除。</li>
 * </ul>
 */
public final class Bench {

    static final com.sun.management.ThreadMXBean TMX =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static final class Result {
        public final String scenario;
        public final long[] nsPerOp;      // 每批的 ns/op
        public final long[] allocPerOp;   // 每批的 分配字节/op
        public final long opsPerBatch;
        public final int warmup;
        public final int iterations;

        Result(String scenario, long[] nsPerOp, long[] allocPerOp, long opsPerBatch,
               int warmup, int iterations) {
            this.scenario = scenario;
            this.nsPerOp = nsPerOp;
            this.allocPerOp = allocPerOp;
            this.opsPerBatch = opsPerBatch;
            this.warmup = warmup;
            this.iterations = iterations;
        }

        public long medianNs() {
            return median(nsPerOp);
        }

        public long minNs() {
            long m = Long.MAX_VALUE;
            for (long v : nsPerOp) m = Math.min(m, v);
            return m;
        }

        public long meanNs() {
            long sum = 0;
            for (long v : nsPerOp) sum += v;
            return sum / nsPerOp.length;
        }

        public long p95Ns() {
            long[] c = nsPerOp.clone();
            Arrays.sort(c);
            int idx = (int) Math.min(c.length - 1, Math.ceil(c.length * 0.95) - 1);
            return c[Math.max(0, idx)];
        }

        public long medianAlloc() {
            return median(allocPerOp);
        }

        private static long median(long[] a) {
            long[] c = a.clone();
            Arrays.sort(c);
            return c[c.length / 2];
        }
    }

    public static Result measure(Scenario sc, int warmup, int iterations) {
        for (int i = 0; i < warmup; i++) {
            sc.runBatch();
        }
        long[] ns = new long[iterations];
        long[] alloc = new long[iterations];
        long ops = 1;
        long tid = Thread.currentThread().threadId();
        for (int i = 0; i < iterations; i++) {
            long a0 = TMX.getThreadAllocatedBytes(tid);
            long t0 = System.nanoTime();
            ops = sc.runBatch();
            long dt = System.nanoTime() - t0;
            long a1 = TMX.getThreadAllocatedBytes(tid);
            ns[i] = dt / ops;
            alloc[i] = (a1 - a0) / ops;
        }
        return new Result(sc.name(), ns, alloc, ops, warmup, iterations);
    }

    /** 保存为 Properties 文本（ASCII，可直接 diff/解析）。 */
    public static void save(Result r, Path file, String label) throws IOException {
        Properties p = new Properties();
        p.setProperty("scenario", r.scenario);
        p.setProperty("label", label);
        p.setProperty("nsPerOp.median", Long.toString(r.medianNs()));
        p.setProperty("nsPerOp.min", Long.toString(r.minNs()));
        p.setProperty("nsPerOp.mean", Long.toString(r.meanNs()));
        p.setProperty("nsPerOp.p95", Long.toString(r.p95Ns()));
        p.setProperty("allocBytesPerOp.median", Long.toString(r.medianAlloc()));
        p.setProperty("opsPerBatch", Long.toString(r.opsPerBatch));
        p.setProperty("warmup", Integer.toString(r.warmup));
        p.setProperty("iterations", Integer.toString(r.iterations));
        p.setProperty("blackholeSink", Long.toString(Blackhole.SINK));
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "Balatro benchmark result — " + LocalDateTime.now());
        }
    }
}
