package cn.quotidietium.balatro.engine;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 牌型判定黄金用例：读取 {@code golden/eval.txt}（原版 evaluateHand 产出），
 * 断言 {@link HandEval} 对各牌型（含 A 低顺、broadway、同花顺、皇家、四条、
 * 万能牌同花、捷径顺子、石头牌等边界）的判定与计分牌选择一致。
 *
 * <p>支持卡牌增强后缀（r.s.enh）与每条 EVAL 后可选的 {@code # flags {...}} 行。
 */
class EvalGoldenTest {

    @Test
    void evaluateMatchesOriginal() throws IOException {
        List<String> lines = readAll("/golden/eval.txt");
        int id = 1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty() || line.startsWith("#")) continue;
            // EVAL <input> => <type> | <scoring>
            int arrow = line.indexOf(" => ");
            int pipe = line.indexOf(" | ");
            String inputPart = line.substring(5, arrow); // skip "EVAL "
            String typeKey = line.substring(arrow + 4, pipe);
            String scoringPart = line.substring(pipe + 3);

            // 紧随的 # flags 行（若有）
            Map<String, Object> flags = new HashMap<>();
            if (i + 1 < lines.size() && lines.get(i + 1).startsWith("# flags ")) {
                String fl = lines.get(i + 1);
                if (fl.contains("fourFingers")) flags.put("fourFingers", true);
                if (fl.contains("shortcut")) flags.put("shortcut", true);
                if (fl.contains("smeared")) flags.put("smeared", true);
                i++; // 消费 flags 行
            }

            List<Card> cards = new ArrayList<>();
            for (String rs : inputPart.split(",")) {
                String[] p = rs.split("\\.");
                Card c = new Card(id++, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
                if (p.length >= 3) c.setEnh(Data.Enhancement.byKey(p[2]));
                cards.add(c);
            }
            RunState dummy = new RunState("x");
            dummy.flags = flags;
            HandEval.Result res = HandEval.evaluate(dummy, cards);
            assertEquals(typeKey, res.type.key, "type for " + inputPart);

            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < res.scoring.size(); j++) {
                if (j > 0) sb.append(',');
                Card c = res.scoring.get(j);
                sb.append(c.rank()).append('.').append(c.suit());
                if (c.enh() != null) sb.append('.').append(c.enh().key);
            }
            assertEquals(scoringPart, sb.toString(), "scoring for " + inputPart);
        }
    }

    private static List<String> readAll(String resource) throws IOException {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(EvalGoldenTest.class.getResourceAsStream(resource)),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isEmpty()) out.add(line);
            }
        }
        return out;
    }
}
