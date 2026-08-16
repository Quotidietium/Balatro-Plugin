package cn.quotidietium.balatro.engine.joker;

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
 * R143：小丑 key 引用存活锁（代码级类别锁，R141 DescNameReferenceTest 的源码面版本）。
 *
 * <p>动机：R142 天文学家缺陷证明「效果消费点查错持有集合/key 拼写错」会造成**静默
 * 死特性**——`findJoker("typo")` 恒 null、`key().equals("typo")` 恒 false，编译期
 * 与运行期均无任何报错，效果直接死亡且 desc 与实现不符。本测试扫描 src/main/java
 * 全部 {@code findJoker("X")} 与 {@code key().equals("X")}（含 def.key()）字面量，
 * 断言 X 存在于 150 小丑注册表——任何未来的拼写错误/改名遗漏立即失败。
 *
 * <p>boss/voucher 等其他域的 key 比对模式不同（多为 {@code "x".equals(bk)} 反序），
 * 不在本锁范围（Boss 域已由 R139 人工全量核对）。
 */
class JokerKeyReferenceTest {

    private static final Pattern REFS = Pattern.compile(
            "(?:findJoker\\(|key\\(\\)\\.equals\\()\"([a-z0-9]+)\"\\)");

    @Test
    void allJokerKeyLiteralsExistInRegistry() throws IOException {
        Set<String> registry = new HashSet<>();
        for (var j : JokerRegistry.allJokersOrdered()) registry.add(j.key());
        Set<String> bad = new HashSet<>();
        Set<String> seen = new HashSet<>();
        Path root = Path.of("src", "main", "java");
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p, StandardCharsets.UTF_8);
                Matcher m = REFS.matcher(src);
                while (m.find()) {
                    String key = m.group(1);
                    seen.add(key);
                    if (!registry.contains(key)) bad.add(key + " @" + p.getFileName());
                }
            }
        }
        assertTrue(bad.isEmpty(), "源码引用了注册表之外的小丑 key（静默死引用）：" + bad);
        assertTrue(seen.size() >= 13,
                "应至少扫到 13 个既有引用（当前 " + seen.size() + "）——扫描器失效即失败");
    }
}
