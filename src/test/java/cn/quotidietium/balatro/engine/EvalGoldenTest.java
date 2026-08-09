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
 * 牌型判定黄金用例：读取 {@code golden/eval.txt}（原版 evaluateHand 产出），
 * 断言 {@link HandEval} 对各牌型（含 A 低顺、broadway、同花顺、皇家、四条等）的判定与计分牌选择一致。
 */
class EvalGoldenTest {

    @Test
    void evaluateMatchesOriginal() throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(EvalGoldenTest.class.getResourceAsStream("/golden/eval.txt")),
                StandardCharsets.UTF_8))) {
            String line;
            int id = 1;
            while ((line = r.readLine()) != null) {
                // EVAL <input> => <type> | <scoring>
                int arrow = line.indexOf(" => ");
                int pipe = line.indexOf(" | ");
                String inputPart = line.substring(5, arrow); // skip "EVAL "
                String typeKey = line.substring(arrow + 4, pipe);
                String scoringPart = line.substring(pipe + 3);

                List<Card> cards = new ArrayList<>();
                for (String rs : inputPart.split(",")) {
                    String[] p = rs.split("\\.");
                    cards.add(new Card(id++, Integer.parseInt(p[0]), Integer.parseInt(p[1])));
                }
                RunState dummy = new RunState("x"); // flags 默认空
                HandEval.Result res = HandEval.evaluate(dummy, cards);
                assertEquals(typeKey, res.type.key, "type for " + inputPart);

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < res.scoring.size(); i++) {
                    if (i > 0) sb.append(',');
                    Card c = res.scoring.get(i);
                    sb.append(c.rank()).append('.').append(c.suit());
                }
                assertEquals(scoringPart, sb.toString(), "scoring for " + inputPart);
            }
        }
    }
}
