package cn.quotidietium.balatro.engine.joker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 校验 {@link JokerRegistry} 加载的小丑元数据（稀有度/售价/名称）与原版 jokers.js 一致。
 */
class JokerMetaTest {

    @Test
    void metaMatchesOriginal() throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(JokerMetaTest.class.getResourceAsStream("/golden/jokermeta.txt")),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] p = line.split("\\|", -1);
                // JOKER|key|rarity|cost|name
                String key = p[1];
                int rarity = Integer.parseInt(p[2]);
                int cost = Integer.parseInt(p[3]);
                String name = p[4];
                assertEquals(rarity, JokerRegistry.rarityOf(key), "rarity " + key);
                assertEquals(cost, JokerRegistry.costOf(key), "cost " + key);
                assertEquals(name, JokerRegistry.nameOf(key), "name " + key);
            }
        }
    }
}
