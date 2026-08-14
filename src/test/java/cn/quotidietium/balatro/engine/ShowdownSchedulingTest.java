package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Showdown Boss 调度与倍率（R126 修复回归，第 56 处修复族）。
 *
 * <p>真版（Boss_Blinds/The Needle/Violet Vessel Wiki）：
 * ①5 终结者（琥珀橡子/翠绿之叶/绯红之心/青蓝之铃/紫罗兰之瓶）**仅在底注 8/16…出现**，
 *   底注 1~7 只从 23 个常规 Boss 抽取；
 * ②缝衣针 = 1× 基础分；紫罗兰之瓶 = 6× 基础分；③终结者奖励 $8。
 * REF 28 混抽/瓶 3×/针缺倍数修正为 REF bug。
 */
class ShowdownSchedulingTest {

    @Test
    void regularAntesNeverQueueFinishers() {
        for (int i = 0; i < 300; i++) {
            RunState s = Engine.createRun("red", 0, "SHOW" + i, null);
            assertTrue(!Data.FINISHERS.contains(s.bossQueue.get(0)),
                    "底注 1 不得出现终结者（seed=" + i + "：" + s.bossQueue.get(0) + "）");
        }
    }

    @Test
    void all23RegularBossesReachableAtAnte1() {
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 600; i++) {
            seen.add(Engine.createRun("red", 0, "REG" + i, null).bossQueue.get(0));
        }
        int regular = 0;
        for (var b : Data.Boss.values()) {
            if (!Data.FINISHERS.contains(b.key)) {
                regular++;
                assertTrue(seen.contains(b.key), "常规 Boss 应可达：" + b.key);
            }
        }
        assertEquals(23, regular, "常规 Boss 应为 23 个");
    }

    @Test
    void finishersAppearOnlyAtShowdownAntes() {
        // 驱动到 ante 8：startAnte 在击败 boss 后调用，此处经 nextRound 推进
        for (int i = 0; i < 60; i++) {
            RunState s = Engine.createRun("red", 0, "FIN" + i, null);
            // 快进：每底注赢 boss → 商店 → next
            for (int ante = 1; ante < 8 && s.phase != Phase.END; ante++) {
                Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false); // small
                s.roundScore = s.blindTarget;
                Engine.playHand(s, List.of(s.hand.get(0).id()));
                if (s.phase != Phase.SHOP) break;
                Engine.nextRound(s); // → big
                if (s.phase != Phase.BLIND_SELECT) break;
                Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                s.roundScore = s.blindTarget;
                Engine.playHand(s, List.of(s.hand.get(0).id()));
                if (s.phase != Phase.SHOP) break;
                Engine.nextRound(s); // → boss select
                if (s.phase != Phase.BLIND_SELECT) break;
                assertTrue(!Data.FINISHERS.contains(s.bossQueue.get(0)),
                        "底注 " + ante + " 的 Boss 不得为终结者");
                Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                s.roundScore = s.blindTarget;
                Engine.playHand(s, List.of(s.hand.get(0).id()));
                if (s.phase != Phase.SHOP) break;
                Engine.nextRound(s); // 击败 boss → startAnte(下一底注)
            }
            if (s.ante == 8) {
                assertTrue(Data.FINISHERS.contains(s.bossQueue.get(0)),
                        "底注 8 必为终结者（实际 " + s.bossQueue.get(0) + "）");
            }
        }
    }

    @Test
    void needleIsOneXAndVesselSixX() {
        RunState s = Engine.createRun("red", 0, "MULT1", null);
        s.bossQueue.clear();
        s.bossQueue.add("needle");
        s.blindType = Data.BlindType.BOSS;
        assertEquals(300, Engine.blindTarget(s, Data.BlindType.BOSS), "缝衣针 1× 基础分（ante1=300）");
        s.bossQueue.clear();
        s.bossQueue.add("vessel");
        assertEquals(1800, Engine.blindTarget(s, Data.BlindType.BOSS), "紫罗兰之瓶 6×（300×6）");
        s.bossQueue.clear();
        s.bossQueue.add("wall");
        assertEquals(1200, Engine.blindTarget(s, Data.BlindType.BOSS), "高墙 4×（对照不变）");
    }

    @Test
    void finisherBossPaysEight() {
        RunState s = Engine.createRun("red", 0, "PAY8", null);
        s.bossQueue.clear();
        s.bossQueue.add("heart"); // 终结者（无卡牌 debuff，可正常达标）
        s.nextBlind = "boss"; // selectBlind 需类型匹配 nextBlind
        Engine.selectBlind(s, Data.BlindType.BOSS, false);
        s.handsLeft = 1; // 最后一手：胜后归零，隔离剩余出牌金（0 手直接走失败路径）
        long before = s.money;
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertEquals(before + 8, s.money, "终结者奖励 $8（对照普通 Boss $5）");
    }
}
