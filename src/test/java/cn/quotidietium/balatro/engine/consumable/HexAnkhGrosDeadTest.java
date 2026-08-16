package cn.quotidietium.balatro.engine.consumable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Consumable;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R137 真版对齐：hex/ankh 经 removeIf 直删小丑（未走 destroyJoker），销毁格罗米歇尔
 * 时须置 {@code grosDead}（真版：任意途径销毁格罗米歇尔都解锁卡文迪什的生成，见
 * Gros Michel/Cavendish Wiki）。REF 同 bug（splice 直删不置位）。
 *
 * <p>keep/src 的选取经一次性流随机——用多种子探测，确保「被销毁」与「被保留」
 * 两种结果都被覆盖并各自断言 grosDead 的正确性。
 */
class HexAnkhGrosDeadTest {

    /** hex：保留者非格罗米歇尔（即其被销毁）时 grosDead 必须置位；保留者是其本人则不置。 */
    @Test
    void hexSetsGrosDeadWhenGrosMichelDestroyed() {
        boolean seenDestroyed = false;
        boolean seenKept = false;
        for (int seed = 0; seed < 80 && !(seenDestroyed && seenKept); seed++) {
            RunState s = Engine.createRun("red", 0, "HEXGROS" + seed, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            assertTrue(s.gainJoker("grossmichel", null));
            assertTrue(s.gainJoker("cavendish", null));
            s.consumables.add(new Consumable("spectral", "hex"));
            assertTrue(Consumables.use(s, 0, List.of()).ok, "hex 应成功（seed=" + seed + "）");
            assertEquals(1, s.jokers.size(), "hex 后仅保留 1 张");
            boolean keptIsGros = s.jokers.get(0).def.key().equals("grossmichel");
            if (keptIsGros) {
                seenKept = true;
                assertFalse(s.grosDead, "格罗米歇尔被保留时不应置 grosDead");
            } else {
                seenDestroyed = true;
                assertTrue(s.grosDead, "R137 修复点：格罗米歇尔被 hex 销毁必须置 grosDead（seed=" + seed + "）");
            }
        }
        assertTrue(seenDestroyed, "80 个种子内应出现「格罗米歇尔被销毁」的抽取结果");
        assertTrue(seenKept, "80 个种子内应出现「格罗米歇尔被保留」的抽取结果");
    }

    /** ankh：复制源非格罗米歇尔（即其被销毁）时同样必须置位。 */
    @Test
    void ankhSetsGrosDeadWhenGrosMichelDestroyed() {
        boolean seenDestroyed = false;
        boolean seenKept = false;
        for (int seed = 0; seed < 80 && !(seenDestroyed && seenKept); seed++) {
            RunState s = Engine.createRun("red", 0, "ANKGROS" + seed, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            assertTrue(s.gainJoker("grossmichel", null));
            assertTrue(s.gainJoker("cavendish", null));
            s.consumables.add(new Consumable("spectral", "ankh"));
            assertTrue(Consumables.use(s, 0, List.of()).ok, "ankh 应成功（seed=" + seed + "）");
            boolean srcIsGros = s.jokers.get(0).def.key().equals("grossmichel");
            // ankh 后 = [复制源, 复制]（其余销毁；源保留且追加同 key 复制，共 2 张）
            assertEquals(2, s.jokers.size(), "ankh 后应为复制源+复制共 2 张");
            assertEquals(s.jokers.get(0).def.key(), s.jokers.get(1).def.key(), "复制须与源同 key");
            if (srcIsGros) {
                seenKept = true;
                assertFalse(s.grosDead, "格罗米歇尔为复制源（存活）时不应置 grosDead");
            } else {
                seenDestroyed = true;
                assertTrue(s.grosDead, "R137 修复点：格罗米歇尔被 ankh 销毁必须置 grosDead（seed=" + seed + "）");
            }
        }
        assertTrue(seenDestroyed, "80 个种子内应出现「格罗米歇尔被销毁」的抽取结果");
        assertTrue(seenKept, "80 个种子内应出现「格罗米歇尔为复制源」的抽取结果");
    }
}
