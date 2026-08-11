package cn.quotidietium.balatro.command;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 聊天帮助文本增强：把行内的 {@code /balatro <命令>} 令牌转换为
 * <b>可悬浮</b>（显示该命令的标题、详细说明与使用举例）+ <b>可点击</b>（回填命令到输入框）的组件。
 *
 * <p>转换基于正则切分：令牌之间的片段按 § 颜色码原样反序列化，颜色与排版不丢失；
 * 未知命令名不做悬浮（保持纯文本），保证任何输入都不会产生悬空组件。
 * 本类不触碰 Bukkit 服务端对象，可独立单测。
 */
public final class HoverText {

    private static final Pattern TOKEN = Pattern.compile("/balatro\\s+([a-zA-Z?]+)");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /**
     * 把一行 § 颜色码文本转换为 Adventure 组件；
     * 其中出现的 {@code /balatro <命令>} 令牌获得悬浮详情与点击回填。
     */
    public static Component commandify(String legacyLine) {
        Matcher m = TOKEN.matcher(legacyLine);
        if (!m.find()) {
            return LEGACY.deserialize(legacyLine);
        }
        m.reset();
        Component out = Component.empty();
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                out = out.append(LEGACY.deserialize(legacyLine.substring(last, m.start())));
            }
            out = out.append(token(m.group(1), m.group()));
            last = m.end();
        }
        if (last < legacyLine.length()) {
            out = out.append(LEGACY.deserialize(legacyLine.substring(last)));
        }
        return out;
    }

    /**
     * 单个命令令牌组件：黄色显示。
     * 在帮助注册表中找到该命令时：悬浮 = 标题 + 正文（含用法与举例），点击 = 回填 {@code /balatro <主键>}；
     * 未找到时仅返回黄色纯文本（无悬浮、无点击）。
     *
     * @param key     命令名（主键或别名，大小写不敏感）
     * @param display 显示文本（通常为原文中的 {@code /balatro xxx} 或裸命令名）
     */
    public static Component token(String key, String display) {
        BalatroHelp.CmdHelp help = BalatroHelp.findCommand(key);
        if (help == null) {
            return Component.text(display, NamedTextColor.YELLOW);
        }
        return Component.text(display, NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(hoverFor(help)))
                .clickEvent(ClickEvent.suggestCommand("/balatro " + help.key()));
    }

    /** 悬浮内容：标题行 + 该命令的全部正文行（用法/说明/举例），§ 颜色码保留。 */
    static Component hoverFor(BalatroHelp.CmdHelp help) {
        Component out = Component.text("■ /balatro " + help.key() + " — " + help.title(), NamedTextColor.GOLD);
        for (String line : help.body()) {
            out = out.append(Component.newline()).append(LEGACY.deserialize(line));
        }
        return out;
    }

    private HoverText() {
    }
}
