package cn.quotidietium.balatro.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 牌组构成黄金用例：读取 {@code golden/deck.txt}，验证 red/checkered/abandoned/erratic
 * 等牌组的构成（点数.花色[.增强] 序列）与原版逐张一致。
 */
class DeckGoldenTest {

    @Test
    void deckCompositionMatchesOriginal() throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(DeckGoldenTest.class.getResourceAsStream("/golden/deck.txt")),
                StandardCharsets.UTF_8))) {
            String header;
            while ((header = r.readLine()) != null) {
                if (header.isEmpty()) continue;
                // DECK <key> <count>
                String[] h = header.split(" ");
                String deckKey = h[1];
                int count = Integer.parseInt(h[2]);
                String cardsLine = r.readLine();
                String[] tokens = cardsLine.split(",");

                RunState st = Engine.createRun(deckKey, 0, "DECK1");
                assertEquals(count, st.fullDeck.size(), deckKey + " size");
                assertEquals(tokens.length, st.fullDeck.size(), deckKey + " token count");
                for (int i = 0; i < tokens.length; i++) {
                    String[] p = tokens[i].split("\\.");
                    Card c = st.fullDeck.get(i);
                    assertEquals(Integer.parseInt(p[0]), c.rank(), deckKey + " card[" + i + "] rank");
                    assertEquals(Integer.parseInt(p[1]), c.suit(), deckKey + " card[" + i + "] suit");
                    if (p.length >= 3) {
                        assertEquals(p[2], c.enh() == null ? "" : c.enh().key, deckKey + " card[" + i + "] enh");
                    }
                }
            }
        }
    }
}
