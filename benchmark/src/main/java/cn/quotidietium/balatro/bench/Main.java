package cn.quotidietium.balatro.bench;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * 基准入口。
 *
 * <pre>
 * 测量：  ./gradlew :benchmark:run --args="--label baseline"
 * 选场景：--scenarios handEval,playHand
 * 对比：  ./gradlew :benchmark:run --args="--compare baseline current"
 * 输出：  benchmark/results/&lt;label&gt;/&lt;scenario&gt;.txt（Properties 文本，入库为证）
 * </pre>
 */
public final class Main {

    private static final Path DEFAULT_OUT = Path.of("benchmark", "results");
    private static final int WARMUP = 3;
    private static final int ITERATIONS = 9;

    public static void main(String[] args) throws Exception {
        String label = "run";
        String outDir = DEFAULT_OUT.toString();
        List<String> only = null;
        String compareA = null;
        String compareB = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--label" -> label = args[++i];
                case "--out" -> outDir = args[++i];
                case "--scenarios" -> only = Arrays.asList(args[++i].split(","));
                case "--compare" -> {
                    compareA = args[++i];
                    compareB = args[++i];
                }
                case "--list" -> {
                    for (Scenario sc : Scenarios.all()) {
                        System.out.printf("%-12s %s%n", sc.name(), sc.description());
                    }
                    return;
                }
                default -> {
                    System.err.println("未知参数: " + args[i]);
                    System.exit(2);
                }
            }
        }

        if (compareA != null) {
            printComparison(Path.of(outDir), compareA, compareB);
            return;
        }

        Path base = Path.of(outDir).resolve(label);
        for (Scenario sc : Scenarios.all()) {
            if (only != null && !only.contains(sc.name())) continue;
            System.out.printf("measuring %-12s ... ", sc.name());
            System.out.flush();
            long sinkBefore = Blackhole.SINK;
            Bench.Result r = Bench.measure(sc, WARMUP, ITERATIONS);
            Bench.save(r, base.resolve(sc.name() + ".txt"), label);
            System.out.printf(Locale.ROOT,
                    "median %,d ns/op | min %,d | p95 %,d | alloc %,d B/op | sinkΔ=%d%n",
                    r.medianNs(), r.minNs(), r.p95Ns(), r.medianAlloc(), Blackhole.SINK - sinkBefore);
        }
        System.out.println("done. results -> " + base);
    }

    // ================= 对比报告 =================

    private record Row(String scenario, double baseMedian, double baseAlloc, double curMedian, double curAlloc) {
    }

    private static void printComparison(Path out, String labelA, String labelB) throws IOException {
        Path dirA = out.resolve(labelA);
        Path dirB = out.resolve(labelB);
        if (!Files.isDirectory(dirA) || !Files.isDirectory(dirB)) {
            System.err.println("结果目录不存在: " + dirA + " / " + dirB);
            System.exit(1);
        }
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(dirA)) {
            stream.filter(f -> f.getFileName().toString().endsWith(".txt")).sorted().forEach(files::add);
        }
        List<Row> rows = new ArrayList<>();
        for (Path fa : files) {
            Path fb = dirB.resolve(fa.getFileName());
            if (!Files.exists(fb)) continue;
            Properties pa = load(fa);
            Properties pb = load(fb);
            rows.add(new Row(
                    pa.getProperty("scenario", fa.getFileName().toString()),
                    Double.parseDouble(pa.getProperty("nsPerOp.median", "0")),
                    Double.parseDouble(pa.getProperty("allocBytesPerOp.median", "0")),
                    Double.parseDouble(pb.getProperty("nsPerOp.median", "0")),
                    Double.parseDouble(pb.getProperty("allocBytesPerOp.median", "0"))));
        }
        System.out.printf("# 基准对比：%s → %s（ns/op 中位数，越小越好）%n%n", labelA, labelB);
        System.out.println("| scenario | base ns/op | cur ns/op | Δ time | speedup | alloc B/op base→cur |");
        System.out.println("|---|---:|---:|---:|---:|---:|");
        double geoBase = 0;
        double geoCur = 0;
        for (Row r : rows) {
            double speedup = r.curMedian() > 0 ? r.baseMedian() / r.curMedian() : 0;
            geoBase += Math.log(Math.max(1e-9, r.baseMedian()));
            geoCur += Math.log(Math.max(1e-9, r.curMedian()));
            System.out.printf(Locale.ROOT, "| %s | %,.0f | %,.0f | %+.1f%% | %.3fx | %,.0f → %,.0f |%n",
                    r.scenario(), r.baseMedian(), r.curMedian(),
                    (r.baseMedian() - r.curMedian()) / r.baseMedian() * 100,
                    speedup, r.baseAlloc(), r.curAlloc());
        }
        if (!rows.isEmpty()) {
            double geoSpeedup = Math.exp((geoBase - geoCur) / rows.size());
            System.out.printf(Locale.ROOT, "%n几何平均加速比：%.3fx（%d 个场景）%n", geoSpeedup, rows.size());
        }
    }

    private static Properties load(Path f) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        }
        return p;
    }
}
