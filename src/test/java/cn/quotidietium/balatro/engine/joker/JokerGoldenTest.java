package cn.quotidietium.balatro.engine.joker;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.RunState;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 小丑黄金用例：读取 {@code golden/jokers.txt}（原版在授予指定小丑后驱动 small 盲注整回合），
 * 本插件授予同一小丑后回放，逐手断言 score/type/won/lost/roundScore/handsLeft 一致。
 *
 * <p>覆盖小丑钩子：onScore(addMult/addChips/handIs/rngInt/playedCards/discardsLeft)、
 * onScoreCard(isSuit)、heldCards 等，以及它们与计分管线的集成。
 */
class JokerGoldenTest {

    @Test
    void jokerEffectsMatchOriginal() throws IOException {
        List<String> lines = readAll("/golden/jokers.txt");
        int i = 0;
        while (i < lines.size()) {
            String header = lines.get(i++);
            assertTrue(header.startsWith("JROUND "), "expected JROUND, got " + header);
            // JROUND <jokerKey> <seed> target=<n>
            String[] hp = header.split(" ");
            String jkey = hp[1];
            String seed = hp[2];
            long target = Long.parseLong(hp[3].substring("target=".length()));

            String handLine = lines.get(i++);
            assertTrue(handLine.startsWith("HAND "));
            String[] handTokens = handLine.substring(5).split(",");

            RunState st = Engine.createRun("red", 0, seed);
            // 授予小丑（须在 selectBlind/startRound 前）
            JokerInstance inst = JokerRegistry.create(jkey);
            assertNotNull(inst, "unknown joker " + jkey);
            st.jokers.add(inst);
            Engine.selectBlind(st, Data.BlindType.SMALL, false);
            assertEquals(target, st.blindTarget, jkey + " target");
            assertEquals(handTokens.length, st.hand.size(), jkey + " hand size");
            for (int h = 0; h < handTokens.length; h++) {
                String[] rs = handTokens[h].split("\\.");
                Card c = st.hand.get(h);
                assertEquals(Integer.parseInt(rs[0]), c.rank(), jkey + " hand[" + h + "] rank");
                assertEquals(Integer.parseInt(rs[1]), c.suit(), jkey + " hand[" + h + "] suit");
            }

            String line;
            while (!(line = lines.get(i++)).equals("ENDROUND")) {
                String[] p = line.substring(5).split(" "); // skip "PLAY "
                boolean expOk = "1".equals(kv(p, "ok"));
                String expType = kv(p, "type");
                long expScore = Long.parseLong(kv(p, "score"));
                boolean expWon = "1".equals(kv(p, "won"));
                boolean expLost = "1".equals(kv(p, "lost"));
                long expRs = Long.parseLong(kv(p, "rs"));
                int expHl = Integer.parseInt(kv(p, "hl"));

                List<Integer> ids = new ArrayList<>();
                int take = Math.min(5, st.hand.size());
                for (int k = 0; k < take; k++) ids.add(st.hand.get(k).id());

                Engine.PlayResult r = Engine.playHand(st, ids);
                assertEquals(expOk, r.ok, jkey + " ok");
                assertEquals(expType, r.type == null ? "-" : r.type.key, jkey + " type");
                assertEquals(expScore, r.score, jkey + " score");
                assertEquals(expWon, r.won, jkey + " won");
                assertEquals(expLost, r.lost, jkey + " lost");
                assertEquals(expRs, st.roundScore, jkey + " roundScore");
                assertEquals(expHl, st.handsLeft, jkey + " handsLeft");
            }
        }
    }

    private static String kv(String[] tokens, String key) {
        for (String t : tokens) {
            if (t.startsWith(key + "=")) return t.substring(key.length() + 1);
        }
        throw new IllegalArgumentException("missing key " + key);
    }

    private static List<String> readAll(String resource) throws IOException {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(JokerGoldenTest.class.getResourceAsStream(resource)),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isEmpty()) out.add(line);
            }
        }
        return out;
    }
}
