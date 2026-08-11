package cn.quotidietium.balatro.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * GUI 容器标识。事件层只认 {@code instanceof GuiHolder}，不认界面标题
 * （标题可被改名/伪造，Holder 由插件自己构造，无法伪造）。
 */
public final class GuiHolder implements InventoryHolder {

    private final MenuType type;
    private Inventory inventory;

    public GuiHolder(MenuType type) {
        this.type = type;
    }

    public MenuType type() {
        return type;
    }

    /** 由 {@link GuiManager} 在 createInventory 后回绑。 */
    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
