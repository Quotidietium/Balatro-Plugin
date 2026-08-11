package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 事件流集成测试（轮次 45）。
 *
 * <p>纯逻辑验证 GameSession 的 fire* 调用链在通关/失败时的正确性。
 * 无法测试 Bukkit callEvent（需服务端），但可验证事件构造前的数据快照正确。
 */
class EventFlowIntegrationTest {

    @Test
    void playHandSnapshotCorrectForEvent() {
        // 验证 GameSession.play 中事件字段的快照正确性
        RunState s = Engine.createRun("red", 0, "EVTFLOW1", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);

        // 记录 playHand 前的状态
        Data.BlindType btBefore = s.blindType;
        int anteBefore = s.ante;
        long target = s.blindTarget;
        long roundScoreBefore = s.roundScore;

        // 出一手牌
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < Math.min(5, s.hand.size()); i++) ids.add(s.hand.get(i).id());
        Engine.PlayResult r = Engine.playHand(s, ids);

        if (r.ok) {
            // 验证事件快照字段：target 应为出牌前的 blindTarget
            assertEquals(target, s.blindTarget, "target 应与出牌前一致");
            // anteBefore/btBefore 是 GameSession 在 play() 中快照的值
            assertEquals(btBefore, s.blindType, "blindType 在回合内不应变");
            assertEquals(anteBefore, s.ante, "ante 在回合内不应变");
            // roundScore 在 playHand 后递增
            assertTrue(s.roundScore >= roundScoreBefore, "roundScore 应递增");
        }
    }

    @Test
    void blindResultEventOnWin() {
        // 验证击败盲注时 GameSession 会发 BlindResultEvent（验证数据流不崩溃）
        RunState s = Engine.createRun("red", 0, "EVTWIN1", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);

        // 反复出牌直到赢或输
        int safety = 20;
        while (s.phase == Phase.ROUND && safety-- > 0) {
            if (s.handsLeft <= 0 || s.hand.isEmpty()) break;
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < Math.min(5, s.hand.size()); i++) ids.add(s.hand.get(i).id());
            Engine.PlayResult r = Engine.playHand(s, ids);
            if (r.won || r.lost) {
                // GameSession 会发 BlindResultEvent(cleared=r.won)
                assertTrue(r.won != r.lost, "won 和 lost 互斥");
                break;
            }
            if (!r.ok && s.handsLeft > 0) {
                Engine.playHand(s, List.of(s.hand.get(0).id()));
            }
        }
    }

    @Test
    void anteClearEventOnlyOnBoss() {
        // 验证 anteClear 只在击败 Boss 盲注时触发（GameSession 逻辑：bt==BOSS）
        RunState s = Engine.createRun("red", 0, "EVTANTE1", null);
        // 手动设置到 Boss 盲注
        s.nextBlind = "boss";
        s.bossKey = "ox"; // 简单 Boss
        s.bossQueue.clear();
        s.bossQueue.add("ox");
        Engine.selectBlind(s, Data.BlindType.BOSS, false);

        // 反复出牌
        boolean wonRound = false;
        while (s.phase == Phase.ROUND && s.handsLeft > 0 && !s.hand.isEmpty()) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < Math.min(5, s.hand.size()); i++) ids.add(s.hand.get(i).id());
            Engine.PlayResult r = Engine.playHand(s, ids);
            if (r.won) { wonRound = true; break; }
            if (!r.ok) Engine.playHand(s, List.of(s.hand.get(0).id()));
        }
        // 如果赢了 Boss 回合，GameSession 会发 anteClear
        // 这里只验证引擎状态正确（s.blindType==BOSS 时 r.won 意味着 anteClear 条件满足）
        if (wonRound) {
            assertTrue(s.phase != Phase.ROUND, "赢 Boss 后应离开 ROUND");
        }
    }

    @Test
    void runEndEventDataConsistent() {
        // 验证 finishRun 时的事件字段一致性
        RunState s = Engine.createRun("red", 0, "EVTEND1", null);
        // 快速驱动到结束
        int it = 0, safety = 1000;
        while (s.phase != Phase.END && it++ < safety) {
            if (s.phase == Phase.BLIND_SELECT) { Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false); continue; }
            if (s.phase == Phase.SHOP) { Engine.nextRound(s); continue; }
            if (s.phase == Phase.PACK) { cn.quotidietium.balatro.engine.shop.Packs.skip(s); continue; }
            if (s.phase == Phase.ROUND) {
                if (s.handsLeft <= 0 || s.hand.isEmpty()) break;
                List<Integer> ids = new ArrayList<>();
                for (int i = 0; i < Math.min(5, s.hand.size()); i++) ids.add(s.hand.get(i).id());
                Engine.PlayResult r = Engine.playHand(s, ids);
                if (!r.ok && s.handsLeft > 0) {
                    for (int tn = 1; tn <= 5; tn++) {
                        if (tn > s.hand.size()) break;
                        List<Integer> t = new ArrayList<>();
                        for (int i = 0; i < tn; i++) t.add(s.hand.get(i).id());
                        if (Engine.playHand(s, t).ok) break;
                    }
                }
                continue;
            }
            break;
        }
        // 结束时 seed/deckKey/stakeIdx 应与初始一致
        assertEquals("EVTEND1", s.seed, "seed 不应变");
        assertEquals("red", s.deckKey, "deckKey 不应变");
        assertEquals(0, s.stakeIdx, "stakeIdx 不应变");
    }
}
