package cn.quotidietium.balatro.render;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

/**
 * 全息实体生成辅助（TextDisplay），Display 技术栈：
 * 非持久、CENTER 朝向、满亮度、scoreboard tag 追踪、可选私有可见（仅指定玩家可见）。
 */
public final class Holo {
    private Holo() {
    }

    /** 生成一个文本全息实体。bg=null 表示透明背景。 */
    public static TextDisplay text(Plugin plugin, Player owner, Location loc, String tag,
                                   Component content, Color bg, boolean privateVisible) {
        TextDisplay d = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        d.addScoreboardTag(tag);
        d.addScoreboardTag("balatro_board"); // 统一标记，便于批量清理
        d.setPersistent(false);
        d.setBillboard(Display.Billboard.CENTER);
        d.setAlignment(TextDisplay.TextAlignment.CENTER);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(2.0f);
        d.setShadowed(true);
        d.text(content);
        if (bg != null) {
            d.setBackgroundColor(bg);
            d.setDefaultBackground(false);
        } else {
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        }
        if (privateVisible) {
            d.setVisibleByDefault(false);
            owner.showEntity(plugin, d);
        }
        return d;
    }

    /**
     * 生成一个 {@link Interaction} 命中盒实体（不可见，承载点击）。
     *
     * <p>Interaction 是 MC 1.19.4+ 专为"可点击全息"设计的实体：有明确的 width×height 原生命中盒，
     * 玩家右键它**确定触发** {@code PlayerInteractEntityEvent}——不依赖 Display 实体那套极小的命中盒，
     * 也不依赖手动射线计算。{@code action} 编进 scoreboard tag（{@code balatro_i_<action>}）供监听器派发。
     *
     * @param loc   实体 foot 位置（命中盒自此向上 height，横向居中）
     * @param width 命中盒宽（格）
     * @param height 命中盒高（格）
     */
    public static Interaction interaction(Plugin plugin, Player owner, Location loc, String action,
                                          float width, float height, boolean privateVisible) {
        Interaction inter = (Interaction) loc.getWorld().spawnEntity(loc, EntityType.INTERACTION);
        inter.setInteractionWidth(width);
        inter.setInteractionHeight(height);
        inter.setPersistent(false);
        inter.addScoreboardTag("balatro_i_" + action);
        inter.addScoreboardTag("balatro_board");
        if (privateVisible) {
            inter.setVisibleByDefault(false);
            owner.showEntity(plugin, inter);
        }
        return inter;
    }
}
