package cn.quotidietium.balatro.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * R148：benchmark 场景表文档一致性锁——Scenarios.java 中每个 {@code name()} 场景
 * 必须出现在 benchmark/README.md 的场景表里。
 *
 * <p>缺陷（#75 的一部分）：P14 新增 useConsumable/packOpen 两场景时代码合入了，
 * README 场景表漏更（8 行表停留在一期）——「新增场景忘更文档」与 R147 帮助页漏更
 * 同构。本测试源码扫描两侧，新增场景未同步文档即失败。
 */
class BenchScenarioDocConsistencyTest {

    private static final Pattern SCENARIO_NAME = Pattern.compile("name\\(\\) \\{ return \"([a-zA-Z]+)\"; \\}");

    @Test
    void benchmarkReadmeCoversAllScenarios() throws IOException {
        Path scenarios = Path.of("benchmark", "src", "main", "java", "cn", "quotidietium", "balatro",
                "bench", "Scenarios.java");
        assertTrue(Files.isRegularFile(scenarios), "Scenarios.java 应存在（须从项目根运行）");
        Path readme = Path.of("benchmark", "README.md");
        assertTrue(Files.isRegularFile(readme));

        List<String> names = new ArrayList<>();
        Matcher m = SCENARIO_NAME.matcher(Files.readString(scenarios, StandardCharsets.UTF_8));
        while (m.find()) names.add(m.group(1));
        assertTrue(names.size() >= 10, "应至少扫到 10 个场景（当前 " + names.size() + "）——扫描器失效防护");

        String doc = Files.readString(readme, StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (String n : names) {
            if (!doc.contains("`" + n + "`")) missing.add(n);
        }
        assertTrue(missing.isEmpty(), "benchmark/README.md 场景表缺少场景（新增场景须同步文档）: " + missing);
    }
}
