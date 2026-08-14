package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import org.junit.jupiter.api.Test;

/**
 * riffraff（杂牌军）双小丑产出的满槽半发语义（R120）。
 *
 * <p>onBlindStart 连续两次 gainRandomJoker(0)：剩 1 空槽 → 第一发成功、第二发被
 * jokerSpace 守卫拒绝（**先于取流返回**，R6 核验满槽不耗流）——半发不崩溃、槽位精确到顶。
 * 满槽开局 → 两发全拒。产物经 gainRandomJoker 自动受 R108 禁入清单过滤。
 */
class RiffraffHalfGrantTest {

    private static void fill(RunState s, int n) {
        for (int i = 0; i < n; i++) s.jokers.add(JokerRegistry.create("joker"));
    }

    @Test
    void oneEmptySlotGrantsExactlyOne() {
        RunState s = Engine.createRun("red", 0, "RIFF1", null);
        s.jokers.add(JokerRegistry.create("riffraff"));
        fill(s, 3); // 4/5，剩 1 空槽
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        assertEquals(5, s.jokers.size(), "半发：第一发成功到顶，第二发被拒（精确 5/5）");
    }

    @Test
    void fullSlotsGrantNothing() {
        RunState s = Engine.createRun("red", 0, "RIFF2", null);
        s.jokers.add(JokerRegistry.create("riffraff"));
        fill(s, 4); // 5/5 满
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        assertEquals(5, s.jokers.size(), "满槽两发全拒");
    }

    @Test
    void twoFreeSlotsGrantTwo() {
        RunState s = Engine.createRun("red", 0, "RIFF3", null);
        s.jokers.add(JokerRegistry.create("riffraff"));
        fill(s, 2); // 3/5，剩 2
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        assertEquals(5, s.jokers.size(), "两空槽双发到顶");
    }
}
