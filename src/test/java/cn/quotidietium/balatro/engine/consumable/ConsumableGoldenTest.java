package cn.quotidietium.balatro.engine.consumable;

import cn.quotidietium.balatro.engine.Consumable;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 消耗品黄金用例：读取 {@code golden/consumable.txt}，验证 12 张星球牌使用后对应牌型升级，
 * 且 useConsumable 框架（槽位/流/钩子）与原版一致。
 */
class ConsumableGoldenTest {

    @Test
    void planetsLevelUpMatch() throws IOException {
        RunState st = Engine.createRun("red", 0, "CONS1");
        st.phase = Phase.ROUND; // 允许在回合内使用
        int lineNo = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(ConsumableGoldenTest.class.getResourceAsStream("/golden/consumable.txt")),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                // PLANETUSE <key> <hand> <before> <after> <ok>
                String[] p = line.split(" ");
                String key = p[1];
                Data.HandType hand = Data.HandType.byKey(p[2]);
                int before = Integer.parseInt(p[3]);
                int after = Integer.parseInt(p[4]);
                boolean ok = "1".equals(p[5]);

                assertEquals(before, st.handLevel(hand), key + " before");
                st.consumables.add(new Consumable("planet", key));
                Consumables.Result res = Consumables.use(st, st.consumables.size() - 1, null);
                assertEquals(ok, res.ok, key + " ok");
                assertEquals(after, st.handLevel(hand), key + " after");
                lineNo++;
            }
        }
        assertTrue(lineNo == 12, "expected 12 planet cases, got " + lineNo);
    }
}
