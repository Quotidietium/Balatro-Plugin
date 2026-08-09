package cn.quotidietium.balatro.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 挑战效果黄金用例：读取 {@code golden/challenge.txt}，对 20 个挑战逐一 createRun
 * 并断言 money/小丑/牌组数/石头牌数与原版一致。
 */
class ChallengeGoldenTest {

    @Test
    void challengeEffectsMatchOriginal() throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(ChallengeGoldenTest.class.getResourceAsStream("/golden/challenge.txt")),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                // CHALFX <key> money=<m> jokers=<keys> deck=<n> stone=<n>
                String[] parts = line.split(" ");
                String key = parts[1];
                long money = Long.parseLong(parts[2].substring("money=".length()));
                String jokers = parts[3].substring("jokers=".length());
                int deck = Integer.parseInt(parts[4].substring("deck=".length()));
                int stone = Integer.parseInt(parts[5].substring("stone=".length()));

                RunState st = Engine.createRun("red", 0, "CHAL1", key);
                assertEquals(money, st.money, key + " money");

                StringBuilder jb = new StringBuilder();
                for (int i = 0; i < st.jokers.size(); i++) {
                    if (i > 0) jb.append(',');
                    jb.append(st.jokers.get(i).def.key());
                }
                assertEquals(jokers, jb.toString(), key + " jokers");
                assertEquals(deck, st.fullDeck.size(), key + " deck size");

                int stN = 0;
                for (Card c : st.fullDeck) if (c.enh() == Data.Enhancement.STONE) stN++;
                assertEquals(stone, stN, key + " stone count");
            }
        }
    }
}
