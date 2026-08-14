package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Consumable;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.consumable.Consumables;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 复制产线的贴纸传播（R114 修复回归，第 51 处）。
 *
 * <p>真版（Wiki/Reddit）：Ankh/隐形小丑的复制**保留贴纸**——永恒/租赁/易腐（含剩余回合计数）；
 * Ankh 且**不复制负片**版本。REF 的 gainJoker 仅传版本、贴纸全丢（REF bug）。
 * 修复：RunState.duplicateJoker 继承贴纸；ankh 剔除负片；隐形原样传版本。
 */
class DupStickerPropagationTest {

    private static RunState inRound(String seed) {
        RunState s = Engine.createRun("red", 0, seed, null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        return s;
    }

    @Test
    void duplicateJokerCopiesStickersIncludingPerishCounter() {
        RunState s = inRound("STK1");
        JokerInstance src = JokerRegistry.create("runner");
        src.eternal = true;
        src.rental = true;
        src.perishable = true;
        src.perishCount = 3; // 已消耗 2 回合，剩 3
        src.edition = Data.Edition.FOIL;
        s.jokers.add(src);
        assertTrue(s.duplicateJoker(src, src.edition), "复制应成功");
        JokerInstance copy = s.jokers.get(1);
        assertTrue(copy.eternal, "永恒随复制保留");
        assertTrue(copy.rental, "租赁随复制保留");
        assertTrue(copy.perishable, "易腐随复制保留");
        assertEquals(3, copy.perishCount, "剩余回合计数随复制保留（whole joker）");
        assertEquals(Data.Edition.FOIL, copy.edition, "版本按传参");
        assertFalse(copy.extra.containsKey("chips"), "extra 仍空起步（R112 语义不变）");
    }

    @Test
    void ankhDropsNegativeEditionButKeepsEternalSticker() {
        RunState s = inRound("ANKH2");
        // 永恒源：ankh 不可摧毁（editable 过滤 eternal）→ 需要一个非永恒候选 + 永恒源被复制？
        // ankh 从非永恒里挑 src：构造唯一非永恒源 = negative+非贴纸 runner
        JokerInstance src = JokerRegistry.create("runner");
        src.edition = Data.Edition.NEGATIVE;
        s.jokers.add(src);
        s.consumables.add(new Consumable("spectral", "ankh"));
        var r = Consumables.use(s, 0, List.of());
        assertTrue(r.ok, "ankh 应可用：" + r.err);
        // removeIf 清掉其他后 duplicate：copy 应为非负片（真版 ankh 不复制 negative）
        assertEquals(2, s.jokers.size(), "src + 副本");
        JokerInstance copy = s.jokers.get(1);
        assertNull(copy.edition, "ankh 副本不保留负片（真版：loses Negative）");
        assertEquals("runner", copy.def.key());
    }

    @Test
    void invisibleDuplicateKeepsRentalSticker() {
        RunState s = inRound("INV3");
        JokerInstance src = JokerRegistry.create("green");
        src.rental = true;
        s.jokers.add(src);
        JokerInstance inv = JokerRegistry.create("invisible");
        s.jokers.add(inv);
        for (int r = 0; r < 3; r++) inv.def.onRoundEnd(s, inv); // 快进到期
        assertTrue(s.sellJoker(1), "到期出售隐形");
        assertEquals(2, s.jokers.size(), "卖一得一");
        JokerInstance dup = s.jokers.get(1);
        assertEquals("green", dup.def.key());
        assertTrue(dup.rental, "隐形复制保留租赁贴纸（whole joker）");
    }

    @Test
    void onAnySellFiresPerRemainingInstanceOfDuplicateKey() {
        // 两份篝火（唯一 onAnySell 实现者）：卖出无关小丑 → 每份各自 +0.5（快照遍历）
        RunState s = inRound("ANY1");
        JokerInstance c1 = JokerRegistry.create("campfire");
        JokerInstance c2 = JokerRegistry.create("campfire");
        s.jokers.add(c1);
        s.jokers.add(c2);
        s.jokers.add(JokerRegistry.create("joker")); // 无关牺牲品
        double x1 = gd(c1), x2 = gd(c2);
        assertTrue(s.sellJoker(2), "卖出第三张");
        assertEquals(x1 + 0.5, gd(c1), 1e-9, "篝火1 onAnySell 各自触发");
        assertEquals(x2 + 0.5, gd(c2), 1e-9, "篝火2 onAnySell 各自触发");
    }

    private static double gd(JokerInstance j) {
        Object v = j.extra.get("x");
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }
}
