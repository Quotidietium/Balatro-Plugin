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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引擎回合黄金用例：读取 {@code golden/engine.txt}（原版在固定种子下 small 盲注整回合的出牌序列），
 * 用本插件 Engine 完全相同的操作序列回放，逐手断言 type/score/won/lost/roundScore/handsLeft 一致。
 *
 * <p>覆盖：种子化洗牌→抽牌（手牌顺序）、drawUpTo 补牌、牌型判定、chips×mult 计分、胜负判定。
 * 全程在"非 Boss 回合"内，避开 0.1.0 未实现的 Boss 干扰效果与商店差异。
 */
class EngineGoldenTest {

    @Test
    void replaySmallBlindRoundsMatchOriginal() throws IOException {
        List<String> lines = readAll("/golden/engine.txt");
        int i = 0;
        while (i < lines.size()) {
            String round = lines.get(i++);
            assertTrue(round.startsWith("ROUND "), "expected ROUND, got " + round);
            // ROUND <seed> target=<n> handSize=<n> hands=<n> discards=<n>
            String[] parts = round.split(" ");
            String seed = parts[1];
            long target = Long.parseLong(kv(parts, "target"));
            int handSize = Integer.parseInt(kv(parts, "handSize"));
            int hands = Integer.parseInt(kv(parts, "hands"));
            int discards = Integer.parseInt(kv(parts, "discards"));

            String handLine = lines.get(i++);
            assertTrue(handLine.startsWith("HAND "));
            String[] handTokens = handLine.substring(5).split(",");

            RunState st = Engine.createRun("red", 0, seed);
            Engine.selectBlind(st, Data.BlindType.SMALL, false);
            assertEquals(target, st.blindTarget, seed + " target");
            assertEquals(handSize, st.handSizeRound, seed + " handSize");
            assertEquals(hands, st.handsLeft, seed + " hands");
            assertEquals(discards, st.discardsLeft, seed + " discards");
            // 初始手牌顺序一致（验证洗牌+抽牌）
            assertEquals(handTokens.length, st.hand.size(), seed + " hand size");
            for (int h = 0; h < handTokens.length; h++) {
                String[] rs = handTokens[h].split("\\.");
                Card c = st.hand.get(h);
                assertEquals(Integer.parseInt(rs[0]), c.rank(), seed + " hand[" + h + "] rank");
                assertEquals(Integer.parseInt(rs[1]), c.suit(), seed + " hand[" + h + "] suit");
            }

            // 回放出牌序列：每手打前 min(5, hand) 张
            String line;
            while (!(line = lines.get(i++)).equals("ENDROUND")) {
                // PLAY ok=<0/1> type=<key> score=<n> won=<0/1> lost=<0/1> rs=<n> hl=<n>
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
                assertEquals(expOk, r.ok, seed + " ok");
                assertEquals(expType, r.type == null ? "-" : r.type.key, seed + " type");
                assertEquals(expScore, r.score, seed + " score");
                assertEquals(expWon, r.won, seed + " won");
                assertEquals(expLost, r.lost, seed + " lost");
                assertEquals(expRs, st.roundScore, seed + " roundScore");
                assertEquals(expHl, st.handsLeft, seed + " handsLeft");
                // 不提前 break：won/lost 后黄金下一行即 ENDROUND，自然退出；任何分歧由逐手断言捕获
            }
        }
    }

    /** 从 "key=value" token 数组里取某个 key 的值。 */
    private static String kv(String[] tokens, String key) {
        for (String t : tokens) {
            if (t.startsWith(key + "=")) return t.substring(key.length() + 1);
        }
        throw new IllegalArgumentException("missing key " + key);
    }

    private static List<String> readAll(String resource) throws IOException {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(EngineGoldenTest.class.getResourceAsStream(resource)),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isEmpty()) out.add(line);
            }
        }
        return out;
    }
}
