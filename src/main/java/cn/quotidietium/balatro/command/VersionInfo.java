package cn.quotidietium.balatro.command;

import java.util.List;

/**
 * {@code /balatro version} 的版本与版权信息（纯逻辑，零 Bukkit 依赖，可单测）。
 *
 * <p>版本号由调用方从 plugin.yml（构建时注入 Gradle 版本）读取后传入，
 * 本类不硬编码版本号，杜绝发版时两处漂移。
 */
final class VersionInfo {

    /** 版本与版权信息行；{@code version} 为当前插件版本。 */
    static List<String> lines(String version) {
        return List.of(
                "§6━━ 小丑牌 (Balatro) ━━",
                "§f当前版本：§e" + version,
                "§f插件作者：§eZTF3",
                "§f协作者：§eDalict",
                "§f本项目遵循 §eApache-2.0 license§f 开源协议",
                "§f开源地址：§bhttps://github.com/hershate/Balatro-Plugin",
                "§f本项目的商业版本由 §eDalict§f 负责管理");
    }

    private VersionInfo() {
    }
}
