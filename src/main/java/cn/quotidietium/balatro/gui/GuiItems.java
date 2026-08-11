package cn.quotidietium.balatro.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * GUI 物品工厂：统一命名/ lore 的 Adventure 组件封装（去掉默认斜体），
 * 以及牌组/赌注/挑战的展示材质映射（仅外观，不影响逻辑）。
 */
final class GuiItems {

    /** 构造展示物品：名称 + lore（均为组件，自动去斜体）。 */
    static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore != null && !lore.isEmpty()) {
            List<Component> fixed = new ArrayList<>(lore.size());
            for (Component c : lore) {
                fixed.add(c.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(fixed);
        }
        it.setItemMeta(meta);
        return it;
    }

    /** 便捷：文本名称 + 灰色 lore 行。 */
    static ItemStack item(Material material, String name, NamedTextColor color, String... loreLines) {
        List<Component> lore = new ArrayList<>(loreLines.length);
        for (String line : loreLines) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        return item(material, Component.text(name, color), lore);
    }

    /** 选中态：附魔光效（1.20.5+ 数据驱动光效覆盖，无需伪附魔）。 */
    static ItemStack glint(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    /** 边框玻璃板。 */
    static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), null);
    }

    /** 牌组展示材质（按 key；未知 key 兜底）。 */
    static Material deckMaterial(String key) {
        return switch (key) {
            case "red" -> Material.RED_CONCRETE;
            case "blue" -> Material.BLUE_CONCRETE;
            case "yellow" -> Material.YELLOW_CONCRETE;
            case "green" -> Material.GREEN_CONCRETE;
            case "black" -> Material.BLACK_CONCRETE;
            case "magic" -> Material.PURPLE_CONCRETE;
            case "nebula" -> Material.LIGHT_BLUE_CONCRETE;
            case "ghost" -> Material.WHITE_CONCRETE;
            case "abandoned" -> Material.GRAY_CONCRETE;
            case "checkered" -> Material.LIGHT_GRAY_CONCRETE;
            case "zodiac" -> Material.ORANGE_CONCRETE;
            case "painted" -> Material.PINK_CONCRETE;
            case "anaglyph" -> Material.CYAN_CONCRETE;
            case "plasma" -> Material.MAGENTA_CONCRETE;
            case "erratic" -> Material.LIME_CONCRETE;
            default -> Material.BOOK;
        };
    }

    /** 赌注展示材质（按下标 0~7）。 */
    static Material stakeMaterial(int idx) {
        return switch (idx) {
            case 0 -> Material.WHITE_WOOL;
            case 1 -> Material.RED_WOOL;
            case 2 -> Material.GREEN_WOOL;
            case 3 -> Material.BLACK_WOOL;
            case 4 -> Material.BLUE_WOOL;
            case 5 -> Material.PURPLE_WOOL;
            case 6 -> Material.ORANGE_WOOL;
            case 7 -> Material.GOLD_BLOCK;
            default -> Material.BOOK;
        };
    }

    /** 挑战展示材质（按 key；未知 key 兜底 BOOK）。 */
    static Material challengeMaterial(String key) {
        return switch (key) {
            case "omelette" -> Material.EGG;
            case "city15" -> Material.CLOCK;
            case "rich" -> Material.GOLD_INGOT;
            case "knife" -> Material.IRON_SWORD;
            case "xray" -> Material.SPYGLASS;
            case "madworld" -> Material.TNT;
            case "luxury" -> Material.EMERALD;
            case "nonperish" -> Material.END_CRYSTAL;
            case "medusa" -> Material.STONE;
            case "double" -> Material.DIAMOND;
            case "typecast" -> Material.OBSIDIAN;
            case "inflation" -> Material.GOLD_NUGGET;
            case "bram" -> Material.ZOMBIE_HEAD;
            case "fragile" -> Material.GLASS;
            case "monolith" -> Material.DEEPSLATE;
            case "blastoff" -> Material.FIREWORK_ROCKET;
            case "fivecard" -> Material.MAP;
            case "golden" -> Material.GOLDEN_CARROT;
            case "cruelty" -> Material.IRON_AXE;
            case "jokerless" -> Material.GRAY_DYE;
            default -> Material.BOOK;
        };
    }

    private GuiItems() {
    }
}
