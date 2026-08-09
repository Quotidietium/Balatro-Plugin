package cn.quotidietium.balatro.engine;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rng 黄金用例：读取由 {@code tools/gen-golden.mjs}（运行原版 rng.js）产出的
 * {@code golden/rng.txt}，逐值断言本插件 Java 移植与原版 mulberry32/FNV-1a 完全一致。
 *
 * <p>这是种子可复现的基石——若本测试不过，后续引擎移植的种子复现无从谈起。
 */
class RngGoldenTest {

    @Test
    void nextSequencesMatchOriginal() throws IOException {
        for (Section sec : parse("/golden/rng.txt")) {
            if (!sec.tag.equals("NEXT")) continue;
            String[] sk = sec.key.split("\\|", 2);
            Rng.Stream s = Rng.makeStream(sk[0], sk[1]);
            for (String ev : sec.values) {
                assertEquals(Double.parseDouble(ev), s.next(), 0.0,
                        "NEXT " + sec.key + " diverged");
            }
        }
    }

    @Test
    void rangeMatchesOriginal() throws IOException {
        for (Section sec : parse("/golden/rng.txt")) {
            if (!sec.tag.equals("RANGE")) continue;
            Rng.Stream s = Rng.makeStream(sec.key, "rng");
            for (String ev : sec.values) {
                assertEquals(Integer.parseInt(ev), s.range(2, 14),
                        "RANGE " + sec.key + " diverged");
            }
        }
    }

    @Test
    void pickMatchesOriginal() throws IOException {
        for (Section sec : parse("/golden/rng.txt")) {
            if (!sec.tag.equals("PICK")) continue;
            Rng.Stream s = Rng.makeStream(sec.key, "pick");
            List<Integer> base = new ArrayList<>();
            for (int i = 0; i < 52; i++) base.add(i);
            for (String ev : sec.values) {
                assertEquals(Integer.parseInt(ev), s.pick(base),
                        "PICK " + sec.key + " diverged");
            }
        }
    }

    @Test
    void shuffleMatchesOriginal() throws IOException {
        for (Section sec : parse("/golden/rng.txt")) {
            if (!sec.tag.equals("SHUF")) continue;
            Rng.Stream s = Rng.makeStream(sec.key, "shuf");
            List<Integer> arr = new ArrayList<>();
            for (int i = 0; i < 52; i++) arr.add(i);
            s.shuffle(arr);
            int[] expected = sec.values.stream().mapToInt(Integer::parseInt).toArray();
            int[] actual = arr.stream().mapToInt(Integer::intValue).toArray();
            assertArrayEquals(expected, actual, "SHUF " + sec.key + " diverged");
        }
    }

    @Test
    void chanceMatchesOriginal() throws IOException {
        for (Section sec : parse("/golden/rng.txt")) {
            if (!sec.tag.equals("CHANCE")) continue;
            Rng.Stream s = Rng.makeStream(sec.key, "chance");
            for (String ev : sec.values) {
                assertEquals(ev.equals("1"), s.chance(0.25),
                        "CHANCE " + sec.key + " diverged");
            }
        }
    }

    @Test
    void weightedMatchesOriginal() throws IOException {
        // 权重 [1,2,3,0]；断言被选项的权重与原版一致
        record W(int w) {}
        for (Section sec : parse("/golden/rng.txt")) {
            if (!sec.tag.equals("WEIGHTED")) continue;
            Rng.Stream s = Rng.makeStream(sec.key, "weighted");
            List<W> items = new ArrayList<>();
            items.add(new W(1)); items.add(new W(2)); items.add(new W(3)); items.add(new W(0));
            for (String ev : sec.values) {
                W got = s.weighted(items, W::w);
                assertEquals(Integer.parseInt(ev), got.w,
                        "WEIGHTED " + sec.key + " diverged");
            }
        }
    }

    @Test
    void seedDeterministic() {
        // 同种子同流多次创建：序列必须完全一致
        Rng.Stream a = Rng.makeStream("DETERMINISTIC", "s");
        Rng.Stream b = Rng.makeStream("DETERMINISTIC", "s");
        for (int i = 0; i < 100; i++) {
            assertEquals(a.next(), b.next(), 0.0);
        }
    }

    @Test
    void nextInRange() {
        Rng.Stream s = Rng.makeStream("RANGECHK", "x");
        for (int i = 0; i < 1000; i++) {
            double v = s.next();
            assertTrue(v >= 0.0 && v < 1.0, "next() out of [0,1): " + v);
        }
    }

    // ---- 黄金文件解析 ----

    private static List<Section> parse(String resource) throws IOException {
        List<Section> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(RngGoldenTest.class.getResourceAsStream(resource),
                        "missing golden resource " + resource),
                StandardCharsets.UTF_8))) {
            String line;
            Section cur = null;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.equals("END")) {
                    if (cur != null) out.add(cur);
                    cur = null;
                    continue;
                }
                if (cur == null) {
                    int sp = line.indexOf(' ');
                    String tag = sp < 0 ? line : line.substring(0, sp);
                    String key = sp < 0 ? "" : line.substring(sp + 1);
                    cur = new Section(tag, key);
                } else {
                    cur.values.add(line);
                }
            }
        }
        return out;
    }

    private static final class Section {
        final String tag;
        final String key;
        final List<String> values = new ArrayList<>();

        Section(String tag, String key) {
            this.tag = tag;
            this.key = key;
        }
    }
}
