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
 * Boss 效果 × 消耗品的交叉组合边界 fuzz（R98）。
 *
 * <p>动机：R92-R93 发现卡牌状态变更（增强转换）的 bug，R19/R15 审过 Boss debuff 清除。
 * 但「Boss debuff/facedown 状态下的牌被消耗品作用」的交叉组合未被覆盖——这是数据操作高危区：
 * leaf 全 debuff + strength/death、house 面朝下 + 消耗品、bell 强制 + hanged 销毁、
 * pillar 打过失效 + aura 设版本、club 花色失效 + star 改花色 等。
 *
 * <p>策略：强制特定 Boss 进入回合（牌已带 Boss 效果），随机用消耗品作用于这些牌，
 * 断言不抛异常 + 牌状态合法（rank/suit/chipBonus 不变负、isStone 自洽）+ 出牌计分不崩溃。
 */
class BossConsumableCrossFuzzTest {

    private static final int TRIALS = 80; // 每 Boss 的试验数

    /** 与 Boss 效果可能产生有趣交叉的消耗品。 */
    private static final String[][] CONSUMABLES = {
            {"tarot", "strength"}, {"tarot", "death"}, {"tarot", "hanged"},
            {"tarot", "empress"}, {"tarot", "hierophant"}, {"tarot", "lovers"},
            {"tarot", "chariot"}, {"tarot", "justice"}, {"tarot", "devil"}, {"tarot", "tower"},
            {"tarot", "star"}, {"tarot", "moon"}, {"tarot", "sun"}, {"tarot", "world"},
            {"spectral", "aura"}, {"spectral", "talisman"}, {"spectral", "cryptid"},
            {"spectral", "sigil"}, {"spectral", "ouija"},
    };

    /** 有显著 debuff/facedown/限制效果的 Boss（与消耗品交叉最有价值）。 */
    private static final String[] BOSSES = {
            "leaf", "house", "club", "goad", "head", "window", "bell", "pillar",
            "plant", "mark", "wheel", "water", "manacle", "psychic", "fish", "hook",
    };

    @Test
    void consumablesOnBossEffectedCardsNeverCorrupt() {
        Random rnd = new Random(20260818L);
        for (String bossKey : BOSSES) {
            for (int trial = 0; trial < TRIALS; trial++) {
                RunState s = Engine.createRun("red", 0, "BCROSS" + bossKey + trial, null);
                // 强制特定 Boss 并进入其回合（startRound 会把 Boss 效果施加到抽到的牌）
                s.bossQueue.clear();
                s.bossQueue.add(bossKey);
                Engine.selectBlind(s, Data.BlindType.BOSS, false);
                // 现在 phase=ROUND，hand 里的牌可能已 debuff/facedown（视 Boss）
                // bell 需要 bellCardId 初始化（startRound 已设）
                // 随机用 1~3 个消耗品作用于手牌
                for (int step = 0; step < 3; step++) {
                    String[] cons = CONSUMABLES[rnd.nextInt(CONSUMABLES.length)];
                    s.consumables.clear();
                    s.consumables.add(new Consumable(cons[0], cons[1]));
                    // 随机目标：取真实手牌 id（含 debuff/facedown 的牌）
                    List<Integer> targets = new ArrayList<>();
                    int tc = rnd.nextInt(4);
                    for (int i = 0; i < tc; i++) {
                        if (!s.hand.isEmpty() && rnd.nextInt(4) > 0) {
                            targets.add(s.hand.get(rnd.nextInt(s.hand.size())).id());
                        } else {
                            targets.add(rnd.nextInt(100) - 20);
                        }
                    }
                    try {
                        Consumables.use(s, 0, targets);
                    } catch (RuntimeException ex) {
                        fail("Boss(" + bossKey + ") 下消耗品 " + cons[0] + ":" + cons[1]
                                + " 抛异常（trial=" + trial + " step=" + step + "）：" + ex);
                    }
                    assertHandValid(s, bossKey, trial, step, cons[0] + ":" + cons[1]);
                }
                // 最后尝试出牌计分（验证不崩溃；Boss 限制如 psychic 必 5 张可能导致拒绝，可接受）
                if (!s.hand.isEmpty()) {
                    List<Integer> playIds = new ArrayList<>();
                    // psychic 必须正好 5 张
                    int pn = "psychic".equals(bossKey) ? Math.min(5, s.hand.size()) : 1 + rnd.nextInt(Math.min(5, s.hand.size()));
                    for (int i = 0; i < pn && i < s.hand.size(); i++) {
                        playIds.add(s.hand.get(i).id());
                    }
                    try {
                        Engine.playHand(s, playIds);
                    } catch (RuntimeException ex) {
                        fail("Boss(" + bossKey + ") 下出牌计分抛异常（trial=" + trial + "）：" + ex);
                    }
                }
            }
        }
    }

    private static void assertHandValid(RunState s, String bossKey, int trial, int step, String label) {
        String where = "boss=" + bossKey + " trial=" + trial + " step=" + step + " " + label;
        for (Card c : s.hand) {
            assertTrue(c.rank() == 0 || (c.rank() >= 2 && c.rank() <= 14),
                    "rank 非法（" + where + "）：" + c.rank());
            assertTrue(c.suit() == -1 || (c.suit() >= 0 && c.suit() <= 3),
                    "suit 非法（" + where + "）：" + c.suit());
            assertTrue(c.chipBonus() >= 0, "chipBonus 为负（" + where + "）：" + c.chipBonus());
            // isStone 自洽
            if (c.rank() == 0 || c.suit() < 0) {
                assertTrue(c.isStone(), "rank==0/suit<0 应判石头（" + where + "）");
            }
            if (c.isStone()) {
                assertTrue(c.enh() == Data.Enhancement.STONE || c.enh() == null,
                        "石头牌 enh 矛盾（" + where + "）：" + c.enh());
            }
        }
        assertTrue(s.hand.size() <= 40, "手牌泄漏（" + where + "）：" + s.hand.size());
    }
}
