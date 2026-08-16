package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Rng 命名流的全表清点锁定（R106）。
 *
 * <p>种子复现红线 = 「stream(name) 的调用集合与消耗顺序须与 REF 一致」。此前该约束靠
 * 逐点人工对照（R6/R15/R17/R22/R105 等），无**全量集合锁定**——未来任何人新增/改名/删除
 * 一个流调用都可能无声破坏红线。本测试扫描 src/main/java 全部 {@code stream("...")} 字面量，
 * 断言集合恰为白名单：
 *
 * <p>白名单 = REF 原版全部流名（2026-08-14 经 grep REF/balatro/js 逐一对账：
 * Java 7 处 "consumable" 对应 REF 3 处按 kind 拆分的展开，每路径恰好一次 next()，无额外消耗；
 * 其余 35 名计数一致）∪ 唯一有意分歧 {@code xray}（R102 真版对齐，仅 xray 挑战消耗，
 * 见 note/release/0.4.23.md）。
 *
 * <p>失败即表示出现了未对账的新流调用——须对照 REF 审核（或走有意分歧流程：更新本白名单
 * + 审计记录留痕），不得随手改白名单了事。
 */
class RngStreamInventoryTest {

    /** 经 R106 全量对账的期望流名集合（字面量形式，含动态前缀的常量部分如 "use:"）。 */
    private static final Set<String> EXPECTED = Set.of(
            "acorn", "ancient", "bell", "boss", "cavendish", "castle", "certificate",
            "consumable", "deckbuild", "destroyhand", "glass", "grossmichel",
            "hallucination", "heart", "hook", "idol", "illusion", "invisible",
            "lucky", "madness", "mailin", "perkeo", "prob", "rpc", "randomjoker",
            "shuffle", "skiptag", "space", "tag", "todo",
            "shopcards", "shopgen", "shopjoker",
            "use:", "pack",
            "wheel",
            // —— 唯一有意分歧（R102 真版对齐，仅 xray 挑战）——
            "xray");

    private static final Pattern STREAM_CALL = Pattern.compile("stream\\(\"([^\"]*)\"");

    // P14/P15：一次性流改经分段折叠入口（零字符串物化）——名字仍为 "use:"+... / "pack"+...
    // / "shuffle"+roundCount 等。等价性由 RngSegmentedStreamTest + 黄金测试锁定；此处把
    // 入口调用同样纳入清点，保证新增分段入口调用点依旧过白名单审计。
    private static final Pattern STREAM_USE_CALL = Pattern.compile("\\.streamUse\\(");
    private static final Pattern STREAM_PACK_CALL = Pattern.compile("\\.streamPack\\(");
    private static final Pattern STREAM_ROUND_CALL = Pattern.compile("\\.streamRound\\(\"([^\"]*)\"");

    @Test
    void streamNameInventoryMatchesVettedWhitelist() throws IOException {
        Path root = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(root),
                "源码目录应存在（测试须从项目根运行，Gradle 默认工作目录即项目根）");

        Set<String> found = new HashSet<>();
        Set<String> files = new HashSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p, StandardCharsets.UTF_8);
                Matcher m = STREAM_CALL.matcher(src);
                while (m.find()) {
                    found.add(m.group(1));
                    files.add(p.toString());
                }
                if (STREAM_USE_CALL.matcher(src).find()) found.add("use:");
                if (STREAM_PACK_CALL.matcher(src).find()) found.add("pack");
                Matcher mr = STREAM_ROUND_CALL.matcher(src);
                while (mr.find()) found.add(mr.group(1));
            }
        }
        assertTrue(!found.isEmpty(), "应能提取到流名（正则失效防护）");

        Set<String> unexpected = new HashSet<>(found);
        unexpected.removeAll(EXPECTED);
        Set<String> missing = new HashSet<>(EXPECTED);
        missing.removeAll(found);

        assertEquals(Set.of(), unexpected,
                "出现未对账的新流调用（须对照 REF 审核或走有意分歧流程并更新白名单+审计记录）: " + unexpected);
        assertEquals(Set.of(), missing,
                "白名单中的流名未在源码找到（被删除/改名？须同步更新白名单与审计记录）: " + missing);
    }
}
