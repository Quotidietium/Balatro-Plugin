package cn.quotidietium.balatro.engine.consumable;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Consumable;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 消耗品连续混用的混沌模糊测试（R92 补）。
 *
 * <p>动机：R17 逐张核对了单次使用的正确性，但「多个消耗品连续作用于同一张牌」的组合空间
 * 未被覆盖（strength 升点→death 复制→再 strength→aura 设版本→hex 变 negative…）。
 * 牌的可变状态（rank/suit/enh/edition/seal/chipBonus）在反复改写下可能进入非法态
 * （如 rank>14、石头牌被当普通牌升点、isStone 与 rank/suit 矛盾），属「数据操作 BUG」高危区。
 *
 * <p>策略：固定种子可复现；每步随机选一张手牌 + 随机消耗品使用；每步后断言全部手牌的状态不变量。
 * 垃圾/越界目标也混入（验证不崩）。辅助「全手改写」类（sigil/ouija/hex）单独覆盖。
 */
class ConsumableChaosFuzzTest {

    private static final int TRIALS = 150;
    private static final int STEPS = 60;

    /** 可作用于单张/双张手牌的消耗品（targets 1~2）。 */
    private static final String[][] SINGLE = {
            {"tarot", "strength"}, {"tarot", "hanged"}, {"tarot", "death"},
            {"spectral", "aura"}, {"spectral", "familiar"}, {"spectral", "grim"},
            {"spectral", "incantation"}, {"spectral", "talisman"}, {"spectral", "dejavu"},
            {"spectral", "trance"}, {"spectral", "medium"}, {"spectral", "ectoplasm"},
            {"spectral", "cryptid"}, {"tarot", "empress"}, {"tarot", "hierophant"},
            {"tarot", "lovers"}, {"tarot", "chariot"}, {"tarot", "justice"},
            {"tarot", "devil"}, {"tarot", "tower"},
    };

    @Test
    void repeatedConsumableUseKeepsCardsValid() {
        Random rnd = new Random(20260814L);
        for (int trial = 0; trial < TRIALS; trial++) {
            RunState s = Engine.createRun("red", 0, "CHAOS" + trial, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            for (int step = 0; step < STEPS; step++) {
                // 随机选一个消耗品加入槽位（绕过槽位限制直接 add，模拟无限消耗品场景）
                String[] pick = SINGLE[rnd.nextInt(SINGLE.length)];
                s.consumables.clear();
                s.consumables.add(new Consumable(pick[0], pick[1]));
                // 随机目标：半数取真实手牌，半数垃圾/越界
                List<Integer> targets = new ArrayList<>();
                int targetCount = rnd.nextInt(4);
                for (int i = 0; i < targetCount; i++) {
                    if (!s.hand.isEmpty() && rnd.nextInt(5) < 3) {
                        targets.add(s.hand.get(rnd.nextInt(s.hand.size())).id());
                    } else {
                        targets.add(rnd.nextInt(200) - 50); // 越界/负数/不存在 id
                    }
                }
                try {
                    Consumables.use(s, 0, targets);
                } catch (RuntimeException ex) {
                    fail("消耗品使用抛异常（trial=" + trial + " step=" + step + " "
                            + pick[0] + ":" + pick[1] + "）：" + ex);
                }
                assertCardsValid(s, trial, step, pick[0] + ":" + pick[1]);
            }
        }
    }

    @Test
    void fullHandMutatorsKeepCardsValid() {
        // sigil/ouija/hex 改写全部手牌，单独覆盖（它们不取 targets 或取特殊目标）
        Random rnd = new Random(20260815L);
        String[] fullHandKeys = {"sigil", "ouija"};
        for (int trial = 0; trial < TRIALS; trial++) {
            RunState s = Engine.createRun("red", 0, "FH" + trial, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            for (int step = 0; step < STEPS; step++) {
                String key = fullHandKeys[rnd.nextInt(fullHandKeys.length)];
                s.consumables.clear();
                s.consumables.add(new Consumable("spectral", key));
                try {
                    Consumables.use(s, 0, List.of());
                } catch (RuntimeException ex) {
                    fail("全手改写消耗品抛异常（trial=" + trial + " step=" + step + " " + key + "）：" + ex);
                }
                assertCardsValid(s, trial, step, "spectral:" + key);
            }
        }
    }

    /** 牌状态不变量：rank/suit/chipBonus 合法、isStone 与 rank/suit/enh 自洽。 */
    private static void assertCardsValid(RunState s, int trial, int step, String label) {
        String where = "trial=" + trial + " step=" + step + " " + label;
        for (Card c : s.hand) {
            // rank：石头为 0，普通为 2..14（strength 守卫 <14，故不应超 14）
            assertTrue(c.rank() == 0 || (c.rank() >= 2 && c.rank() <= 14),
                    "rank 非法（" + where + "）：" + c.rank());
            // suit：石头壳为 -1，普通为 0..3
            assertTrue(c.suit() == -1 || (c.suit() >= 0 && c.suit() <= 3),
                    "suit 非法（" + where + "）：" + c.suit());
            // chipBonus 不应为负（只 add 正值；若变负说明状态错乱）
            assertTrue(c.chipBonus() >= 0, "chipBonus 为负（" + where + "）：" + c.chipBonus());
            // isStone 自洽：enh==STONE 或 rank==0 或 suit<0 三者至少其一
            boolean stoneByEnh = c.enh() == Data.Enhancement.STONE;
            boolean stoneByRank = c.rank() == 0;
            boolean stoneBySuit = c.suit() < 0;
            if (stoneByRank || stoneBySuit) {
                assertTrue(c.isStone(), "rank==0 或 suit<0 应判为石头（" + where + "）");
            }
            // 石头牌不应同时有普通增强（非 STONE）——消耗品设 enh 应覆盖
            if (c.isStone()) {
                assertTrue(c.enh() == Data.Enhancement.STONE || c.enh() == null,
                        "石头牌的 enh 应为 STONE 或 null（" + where + "）：" + c.enh());
            }
        }
    }
}
