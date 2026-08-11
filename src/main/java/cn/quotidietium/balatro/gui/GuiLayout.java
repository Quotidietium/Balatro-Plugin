package cn.quotidietium.balatro.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单布局（纯逻辑，零 Bukkit 依赖，可单测）。
 * 内容槽位/按钮槽位的计算集中在这一处，渲染与点击分派共用同一份映射，
 * 从根本上避免「点到的槽位」与「画出来的槽位」不一致。
 *
 * <p>约定：27 格菜单内容区 = 第 1 行（槽位 9~17）；54 格菜单内容区 = 第 1~4 行（槽位 9~44）。
 * 顶行与底行为边框/按钮区：返回键在左下角（{@code size-9}），前进键在右下角（{@code size-1}）。
 */
public final class GuiLayout {

    public static final int SIZE_MAIN = 27;
    public static final int SIZE_LIST = 54;

    /** 内容槽（从上到下、从左到右顺序）。 */
    public static List<Integer> contentSlots(int size) {
        int rows = switch (size) {
            case SIZE_MAIN -> 1;
            case SIZE_LIST -> 4;
            default -> throw new IllegalArgumentException("unsupported size: " + size);
        };
        List<Integer> slots = new ArrayList<>(rows * 9);
        for (int row = 1; row <= rows; row++) {
            for (int col = 0; col < 9; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    /** 第 {@code index} 个内容项所在槽位；越界返回 -1。 */
    public static int slotForIndex(int size, int index) {
        List<Integer> slots = contentSlots(size);
        return index >= 0 && index < slots.size() ? slots.get(index) : -1;
    }

    /** 槽位对应的内容项序号；非内容槽返回 -1。 */
    public static int indexOfSlot(int size, int slot) {
        return contentSlots(size).indexOf(slot);
    }

    /** 返回键槽位（左下角）。 */
    public static int backSlot(int size) {
        return size - 9;
    }

    /** 前进键槽位（右下角）。 */
    public static int nextSlot(int size) {
        return size - 1;
    }

    private GuiLayout() {
    }
}
