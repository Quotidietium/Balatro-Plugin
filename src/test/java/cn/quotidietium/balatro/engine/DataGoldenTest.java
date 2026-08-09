package cn.quotidietium.balatro.engine;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Data 黄金用例：读取 {@code golden/data.txt}（由原版 data.js 产出），
 * 断言花色/点数/牌型/盲注目标分等静态数据与原版逐项一致。
 */
class DataGoldenTest {

    @Test
    void rankNameMatches() throws IOException {
        for (String[] p : lines("RANKNAME")) {
            int r = Integer.parseInt(p[1]);
            assertEquals(p[2], Data.rankName(r), "rankName " + r);
        }
    }

    @Test
    void rankChipsMatches() throws IOException {
        for (String[] p : lines("RANKCHIPS")) {
            int r = Integer.parseInt(p[1]);
            assertEquals(Integer.parseInt(p[2]), Data.rankChips(r), "rankChips " + r);
        }
    }

    @Test
    void blindBaseMatches() throws IOException {
        for (String[] p : lines("BLINDBASE")) {
            int ante = Integer.parseInt(p[1]);
            assertEquals(Long.parseLong(p[2]), Data.blindBase(ante), "blindBase " + ante);
        }
    }

    @Test
    void handsMatch() throws IOException {
        for (String[] p : lines("HAND")) {
            // HAND <key> <name> <chips> <mult> <lchips> <lmult> <order>
            Data.HandType h = Data.HandType.byKey(p[1]);
            assertEquals(p[2], h.name, "name " + p[1]);
            assertEquals(Integer.parseInt(p[3]), h.chips, "chips " + p[1]);
            assertEquals(Integer.parseInt(p[4]), h.mult, "mult " + p[1]);
            assertEquals(Integer.parseInt(p[5]), h.lchips, "lchips " + p[1]);
            assertEquals(Integer.parseInt(p[6]), h.lmult, "lmult " + p[1]);
            assertEquals(Integer.parseInt(p[7]), h.order, "order " + p[1]);
        }
    }

    @Test
    void blindsMatch() throws IOException {
        for (String[] p : lines("BLIND")) {
            Data.BlindType b = Data.BlindType.byKey(p[1]);
            assertEquals(Double.parseDouble(p[2]), b.mult, 0.0, "mult " + p[1]);
            assertEquals(Integer.parseInt(p[3]), b.reward, "reward " + p[1]);
        }
    }

    @Test
    void suitsMatch() throws IOException {
        for (String[] p : lines("SUIT")) {
            Data.Suit s = Data.Suit.byKey(p[1]);
            assertEquals(p[2], s.name, "name " + p[1]);
            assertEquals(p[3], s.symbol, "symbol " + p[1]);
            assertEquals(p[4], s.color, "color " + p[1]);
        }
    }

    // ---- 读取 data.txt，按首 token 过滤；跳过段头(HANDS/SUITS)与 END ----
    private static List<String[]> lines(String tag) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(DataGoldenTest.class.getResourceAsStream("/golden/data.txt"),
                        "missing golden/data.txt"),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.equals("END") || line.equals("HANDS") || line.equals("SUITS")) continue;
                String[] p = line.split(" ");
                if (p[0].equals(tag)) out.add(p);
            }
        }
        return out;
    }
}
