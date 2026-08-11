package cn.quotidietium.balatro.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 菜单布局（{@link GuiLayout}）的纯逻辑测试：
 * 内容槽位连续且不触碰边框/按钮区；序号↔槽位映射互逆；非法尺寸抛异常。
 */
class GuiLayoutTest {

    @Test
    void contentSlots54AreRows1To4() {
        List<Integer> slots = GuiLayout.contentSlots(54);
        assertEquals(36, slots.size());
        assertEquals(9, slots.get(0), "首个内容槽应为第 1 行第 1 列");
        assertEquals(44, slots.get(35), "末个内容槽应为第 4 行末列");
        for (int s : slots) {
            assertTrue(s >= 9 && s <= 44, "内容槽不应触碰边框/按钮区：" + s);
        }
    }

    @Test
    void contentSlots27AreRow1() {
        List<Integer> slots = GuiLayout.contentSlots(27);
        assertEquals(9, slots.size());
        for (int i = 0; i < 9; i++) {
            assertEquals(9 + i, slots.get(i));
        }
    }

    @Test
    void allContentFitsWithoutPagination() {
        // 设计前提：15 牌组 / 20 挑战单页放得下（54 格 36 内容槽）；8 赌注放得下（27 格 9 内容槽）
        assertTrue(Data.DECKS.size() <= GuiLayout.contentSlots(54).size(), "牌组应单页放得下");
        assertTrue(Data.CHALLENGES.size() <= GuiLayout.contentSlots(54).size(), "挑战应单页放得下");
        assertTrue(Data.STAKES.size() <= GuiLayout.contentSlots(27).size(), "赌注应单页放得下");
    }

    @Test
    void slotForIndexBounds() {
        assertEquals(9, GuiLayout.slotForIndex(54, 0));
        assertEquals(-1, GuiLayout.slotForIndex(54, -1));
        assertEquals(-1, GuiLayout.slotForIndex(54, 36));
        assertEquals(-1, GuiLayout.slotForIndex(27, 9));
    }

    @Test
    void indexOfSlotRoundTrip() {
        for (int size : new int[]{27, 54}) {
            List<Integer> slots = GuiLayout.contentSlots(size);
            for (int i = 0; i < slots.size(); i++) {
                assertEquals(i, GuiLayout.indexOfSlot(size, slots.get(i)),
                        "序号↔槽位应互逆（size=" + size + ", index=" + i + "）");
            }
            // 非内容槽
            assertEquals(-1, GuiLayout.indexOfSlot(size, 0));
            assertEquals(-1, GuiLayout.indexOfSlot(size, GuiLayout.backSlot(size)));
            assertEquals(-1, GuiLayout.indexOfSlot(size, GuiLayout.nextSlot(size)));
            assertEquals(-1, GuiLayout.indexOfSlot(size, size + 100));
        }
    }

    @Test
    void backAndNextSlots() {
        assertEquals(45, GuiLayout.backSlot(54));
        assertEquals(53, GuiLayout.nextSlot(54));
        assertEquals(18, GuiLayout.backSlot(27));
        assertEquals(26, GuiLayout.nextSlot(27));
    }

    @Test
    void unsupportedSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> GuiLayout.contentSlots(9));
        assertThrows(IllegalArgumentException.class, () -> GuiLayout.contentSlots(36));
    }
}
