package cn.quotidietium.balatro.render;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

/**
 * 全息实体生成辅助（TextDisplay），沿用 doudizhu 的 Display 技术栈：
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
}
