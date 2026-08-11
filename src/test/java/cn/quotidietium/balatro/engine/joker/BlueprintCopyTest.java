package cn.quotidietium.balatro.engine.joker;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 蓝图（Blueprint）/ 头脑风暴（Brainstorm）复制方向回归（轮次 R10）。
 *
 * <p>锁定 {@code Engine.resolveCopy} 的复制语义（深审确认 REF 对齐，此处显式锁定防回归）：
 * <ul>
 *   <li>蓝图复制<strong>右侧</strong>邻居的 onScore 效果（无右邻则不复制）；</li>
 *   <li>头脑风暴复制<strong>最左侧</strong>（index 0）小丑的 onScore 效果（自身在最左则不复制）；</li>
 *   <li>复制类不复制复制类（相邻互不复制）。</li>
 * </ul>
 *
 * <p>锚点小丑 JOLLY（对子 +8 倍率，onScore）。打一对 A（黑桃 A + 红桃 A）：
 * 每张 A = {@code rankChips(14)=11} 筹码，对子 Lv1 = 10 筹码 × 2 倍。
 * 故筹码 = 10 + 11 + 11 = 32，倍率 = 2（基础）。JOLLY 触发 +8 倍。
 */
class BlueprintCopyTest {

    /**
     * 准备一局小盲注（无 Boss 效果干扰），手牌替换为黑桃 A + 红桃 A，
     * 加入指定小丑（按参数顺序决定左右/index）后打出该对子。
     */
    private static RunState playPairOfAces(String seed, String... jokerKeys) {
        RunState s = Engine.createRun("red", 0, seed);
        Engine.selectBlind(s, Data.BlindType.SMALL, false); // → ROUND，无 Boss
        s.hand.clear();
        cn.quotidietium.balatro.engine.Card c0 = s.makeCard(14, 0); // 黑桃 A
        cn.quotidietium.balatro.engine.Card c1 = s.makeCard(14, 1); // 红桃 A
        s.hand.add(c0);
        s.hand.add(c1);
        for (String k : jokerKeys) {
            var j = JokerRegistry.create(k);
            if (j != null) s.jokers.add(j);
        }
        Engine.playHand(s, List.of(c0.id(), c1.id()));
        return s;
    }

    @Test
    void jollyAloneBaseline() {
        // 仅 JOLLY：倍率 2 + 8 = 10 ⇒ 32 × 10 = 320
        RunState s = playPairOfAces("BP_BASE", "jolly");
        assertEquals(320, s.roundScore, "JOLLY 单丑基线：32 筹码 × 10 倍 = 320");
    }

    @Test
    void blueprintCopiesRightNeighbor() {
        // [BLUEPRINT, JOLLY]：蓝图在左(index0)，复制右邻 JOLLY → JOLLY 自身 +8 + 蓝图复制 +8
        // 倍率 2 + 8 + 8 = 18 ⇒ 32 × 18 = 576
        RunState s = playPairOfAces("BP_RIGHT", "blueprint", "jolly");
        assertEquals(576, s.roundScore, "[蓝图, JOLLY]：蓝图复制右邻 → 32 × 18 = 576");
    }

    @Test
    void blueprintNoRightNeighborDoesNotCopy() {
        // [JOLLY, BLUEPRINT]：蓝图在最右(index1)，无右邻 → 不复制；仅 JOLLY 自身 +8
        // 倍率 2 + 8 = 10 ⇒ 320
        RunState s = playPairOfAces("BP_NORIGHT", "jolly", "blueprint");
        assertEquals(320, s.roundScore, "[JOLLY, 蓝图]：蓝图无右邻不复制 → 32 × 10 = 320");
    }

    @Test
    void brainstormCopiesLeftmost() {
        // [JOLLY, BRAINSTORM]：头脑风暴(index1)复制最左(index0)=JOLLY → JOLLY +8 + 复制 +8
        // 倍率 18 ⇒ 576
        RunState s = playPairOfAces("BP_LEFT", "jolly", "brainstorm");
        assertEquals(576, s.roundScore, "[JOLLY, 头脑风暴]：复制最左 JOLLY → 32 × 18 = 576");
    }

    @Test
    void brainstormIsLeftmostDoesNotCopy() {
        // [BRAINSTORM, JOLLY]：头脑风暴在最左(index0)，复制最左=自身 → 不复制；仅 JOLLY(index1) +8
        // 倍率 2 + 8 = 10 ⇒ 320
        RunState s = playPairOfAces("BP_ISFIRST", "brainstorm", "jolly");
        assertEquals(320, s.roundScore, "[头脑风暴, JOLLY]：头脑风暴在最左不复制 → 32 × 10 = 320");
    }

    @Test
    void copiersDoNotCopyCopiers() {
        // [BLUEPRINT, BRAINSTORM]：蓝图复制右邻(头脑风暴=复制类→null)；头脑风暴复制最左(蓝图=复制类→null)
        // 二者均不复制，无 onScore 加成 → 倍率 2 ⇒ 32 × 2 = 64
        RunState s = playPairOfAces("BP_NOCOPY", "blueprint", "brainstorm");
        assertEquals(64, s.roundScore, "[蓝图, 头脑风暴]：互不复制 → 32 × 2 = 64");
    }
}
