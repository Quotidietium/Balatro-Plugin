package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 多副本同 key 小丑的 extra 独立性（R111 修复回归）。
 *
 * <p>REF-inherited 缺陷：10 个累积器的 onPlayHand 经 info.findJoker(key) 只取**第一份**——
 * 多副本（ankh/隐形小丑可造）时副本1 被每次出牌重复衰减 N 次、副本2+ 永不变化，与描述
 * （如冰淇淋「每次出牌 -5 筹码」）矛盾；真版每副本独立计数。修复：onPlayHand 增加
 * self 参数（onDiscard 早已如此——GREEN 自身的不对称即漂移证据），累积器经 self 各自计数。
 */
class MultiCopyAccumulatorTest {

    private static RunState inRound(String seed) {
        RunState s = Engine.createRun("red", 0, seed, null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        return s;
    }

    private static void playFirstTwo(RunState s) {
        // 不预置达标分：让本手不赢盲，避免 endRound 奖励金污染金钱断言
        Engine.playHand(s, List.of(s.hand.get(0).id(), s.hand.get(1).id()));
    }

    private static JokerInstance add(RunState s, String key) {
        JokerInstance j = JokerRegistry.create(key);
        s.jokers.add(j);
        return j;
    }

    @Test
    void twoIceCreamsEachMeltFivePerHand() {
        RunState s = inRound("MCICE");
        JokerInstance a = add(s, "icecream"), b = add(s, "icecream");
        playFirstTwo(s);
        // 修复前：a=-10(两次首实例), b=0；修复后：各 -5
        assertEquals(95, ((Number) a.extra.get("chips")).intValue(), "副本A应 -5");
        assertEquals(95, ((Number) b.extra.get("chips")).intValue(), "副本B应 -5（不再永不融化）");
        // 单副本回归：再打一手，a 再 -5
        s.phase = Phase.ROUND; // 预置分可能已赢盲进商店，强制回合同继续观察
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertEquals(90, ((Number) a.extra.get("chips")).intValue(), "副本A再 -5");
        assertEquals(90, ((Number) b.extra.get("chips")).intValue(), "副本B同样再 -5（独立计数）");
    }

    @Test
    void twoGreensAccumulateIndependentlyOnPlayAndDiscard() {
        RunState s = inRound("MCGRN");
        JokerInstance a = add(s, "green"), b = add(s, "green");
        // 出牌：两份各 +1（修复前仅首份 +2）
        playFirstTwo(s);
        assertEquals(1, ((Number) a.extra.get("mult")).intValue(), "A +1");
        assertEquals(1, ((Number) b.extra.get("mult")).intValue(), "B +1");
        // 弃牌：onDiscard(self) 本就正确——两份各 -1 至 0
        s.phase = Phase.ROUND;
        Engine.discard(s, List.of(s.hand.get(0).id()));
        assertEquals(0, ((Number) a.extra.get("mult")).intValue(), "A -1");
        assertEquals(0, ((Number) b.extra.get("mult")).intValue(), "B -1");
    }

    @Test
    void twoSeltzersEachCountDown() {
        RunState s = inRound("MCSEL");
        JokerInstance a = add(s, "seltzer"), b = add(s, "seltzer");
        playFirstTwo(s);
        assertEquals(9, ((Number) a.extra.get("uses")).intValue(), "A uses 10→9");
        assertEquals(9, ((Number) b.extra.get("uses")).intValue(), "B uses 10→9（不再永不耗尽）");
    }

    @Test
    void twoRunnersEachGainOnStraight() {
        RunState s = inRound("MCRUN");
        JokerInstance a = add(s, "runner"), b = add(s, "runner");
        // 构造顺子：直接改 5 张手牌点数为 5..9 同花色
        for (int i = 0; i < 5 && i < s.hand.size(); i++) {
            s.hand.get(i).setRank(5 + i);
            s.hand.get(i).setSuit(i % 2); // 花色交替：确保是顺子而非同花顺（runner 仅认 straight）
        }
        Engine.playHand(s, List.of(s.hand.get(0).id(), s.hand.get(1).id(),
                s.hand.get(2).id(), s.hand.get(3).id(), s.hand.get(4).id()));
        assertEquals(15, ((Number) a.extra.get("chips")).intValue(), "A +15");
        assertEquals(15, ((Number) b.extra.get("chips")).intValue(), "B +15（修复前 B=0）");
    }

    @Test
    void twoTodosEachPayTheirOwnTarget() {
        RunState s = inRound("MCTODO");
        JokerInstance a = add(s, "todo"), b = add(s, "todo");
        a.extra.put("hand", Data.HandType.PAIR);
        b.extra.put("hand", Data.HandType.HIGH); // 两份目标不同
        // 构造明确的对子（前两张同点）——不依赖随机手牌的牌型
        s.hand.get(1).setRank(s.hand.get(0).rank());
        playFirstTwo(s); // PAIR
        // A 目标 PAIR 命中 +$4；B 目标 HIGH 未命中 +$0（修复前只查 A 的目标）
        assertEquals(8, s.money, "开局 $4 + A 的 $4（B 目标未命中）");
    }

    @Test
    void singleCopyBehaviorUnchanged() {
        RunState s = inRound("MCSINGLE");
        JokerInstance a = add(s, "icecream");
        add(s, "green");
        add(s, "seltzer");
        playFirstTwo(s);
        assertEquals(95, ((Number) a.extra.get("chips")).intValue(), "单副本 -5 不变");
        assertEquals(1, ((Number) s.jokers.get(1).extra.get("mult")).intValue(), "green 单副本 +1");
        assertEquals(9, ((Number) s.jokers.get(2).extra.get("uses")).intValue(), "seltzer 单副本 10→9");
    }
}
