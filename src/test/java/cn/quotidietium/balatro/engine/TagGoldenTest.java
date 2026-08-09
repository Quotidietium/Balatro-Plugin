package cn.quotidietium.balatro.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 标签黄金用例：读取 {@code golden/tag.txt}，对 24 个标签逐一施加并断言
 * money/小丑数/nextShop 修饰/牌型等级变化与原版一致。
 */
class TagGoldenTest {

    @Test
    void tagEffectsMatchOriginal() throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(TagGoldenTest.class.getResourceAsStream("/golden/tag.txt")),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                // TAG <key> money=<m> jokers=<n> nextshop=<k=v,...> levels=<hand:lvl,...>
                String[] parts = line.split(" ");
                String key = parts[1];
                long money = Long.parseLong(parts[2].substring("money=".length()));
                int jokers = Integer.parseInt(parts[3].substring("jokers=".length()));
                String nextShop = parts[4].substring("nextshop=".length());
                String levels = parts[5].substring("levels=".length());

                RunState st = Engine.createRun("red", 0, "TAG1");
                st.phase = Phase.ROUND;
                Engine.gainTag(st, key);

                assertEquals(money, st.money, key + " money");
                assertEquals(jokers, st.jokers.size(), key + " jokers");
                assertEquals(nextShop, nextShopStr(st), key + " nextshop");
                assertEquals(levels, levelsStr(st), key + " levels");
            }
        }
    }

    private static String nextShopStr(RunState s) {
        List<String> entries = new ArrayList<>();
        List<String> keys = new ArrayList<>(s.nextShop.keySet());
        keys.sort(Comparator.naturalOrder());
        for (String k : keys) entries.add(k + "=" + s.nextShop.get(k));
        return String.join(",", entries);
    }

    private static String levelsStr(RunState s) {
        List<String> entries = new ArrayList<>();
        List<Data.HandType> keys = new ArrayList<>(s.handLevels.keySet());
        keys.sort(Comparator.comparingInt(h -> h.order));
        for (Data.HandType h : keys) {
            int lvl = s.handLevels.get(h);
            if (lvl != 1) entries.add(h.key + ":" + lvl);
        }
        return String.join(",", entries);
    }
}
