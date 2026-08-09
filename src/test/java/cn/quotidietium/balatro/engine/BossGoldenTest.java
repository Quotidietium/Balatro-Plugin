package cn.quotidietium.balatro.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boss 效果黄金用例：读取 {@code golden/boss.txt}（原版强制面对指定 Boss 的回合出牌序列），
 * 本插件同样强制面对该 Boss 后回放，逐手断言一致。
 *
 * <p>覆盖 flint（减半）/tooth（扣钱）/needle（1手）/manacle（手牌-1）/water（0弃）/
 * eye（禁重复牌型）/hook（出牌后弃2）等。
 */
class BossGoldenTest {

    @Test
    void bossEffectsMatchOriginal() throws IOException {
        List<String> lines = readAll("/golden/boss.txt");
        int i = 0;
        while (i < lines.size()) {
            String header = lines.get(i++);
            assertTrue(header.startsWith("BOSS "), "expected BOSS, got " + header);
            // BOSS <bk> <seed> target=.. handSize=.. hands=.. discards=..
            String[] hp = header.split(" ");
            String bk = hp[1];
            String seed = hp[2];
            long target = Long.parseLong(kv(hp, "target"));
            int handSize = Integer.parseInt(kv(hp, "handSize"));
            int hands = Integer.parseInt(kv(hp, "hands"));
            int discards = Integer.parseInt(kv(hp, "discards"));

            String handLine = lines.get(i++);
            String[] handTokens = handLine.substring(5).split(",");

            RunState st = Engine.createRun("red", 0, seed);
            st.bossKey = bk;
            st.bossQueue.clear();
            st.bossQueue.add(bk);
            st.nextBlind = "boss";
            Engine.selectBlind(st, Data.BlindType.BOSS, false);

            assertEquals(target, st.blindTarget, bk + " target");
            assertEquals(handSize, st.handSizeRound, bk + " handSize");
            assertEquals(hands, st.handsLeft, bk + " hands");
            assertEquals(discards, st.discardsLeft, bk + " discards");
            assertEquals(handTokens.length, st.hand.size(), bk + " hand size");
            for (int h = 0; h < handTokens.length; h++) {
                String[] rs = handTokens[h].split("\\.");
                Card c = st.hand.get(h);
                assertEquals(Integer.parseInt(rs[0]), c.rank(), bk + " hand[" + h + "] rank");
                assertEquals(Integer.parseInt(rs[1]), c.suit(), bk + " hand[" + h + "] suit");
            }

            String line;
            while (!(line = lines.get(i++)).equals("ENDBOSS")) {
                String[] p = line.substring(5).split(" "); // skip "PLAY "
                boolean expOk = "1".equals(kv(p, "ok"));
                String expType = kv(p, "type");
                long expScore = Long.parseLong(kv(p, "score"));
                boolean expWon = "1".equals(kv(p, "won"));
                boolean expLost = "1".equals(kv(p, "lost"));
                long expRs = Long.parseLong(kv(p, "rs"));
                int expHl = Integer.parseInt(kv(p, "hl"));
                long expMoney = Long.parseLong(kv(p, "money"));

                List<Integer> ids = new ArrayList<>();
                int take = Math.min(5, st.hand.size());
                for (int k = 0; k < take; k++) ids.add(st.hand.get(k).id());

                Engine.PlayResult r = Engine.playHand(st, ids);
                assertEquals(expOk, r.ok, bk + " ok");
                assertEquals(expType, r.type == null ? "-" : r.type.key, bk + " type");
                assertEquals(expScore, r.score, bk + " score");
                assertEquals(expWon, r.won, bk + " won");
                assertEquals(expLost, r.lost, bk + " lost");
                assertEquals(expRs, st.roundScore, bk + " roundScore");
                assertEquals(expHl, st.handsLeft, bk + " handsLeft");
                assertEquals(expMoney, st.money, bk + " money");
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
                Objects.requireNonNull(BossGoldenTest.class.getResourceAsStream(resource)),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isEmpty()) out.add(line);
            }
        }
        return out;
    }
}
