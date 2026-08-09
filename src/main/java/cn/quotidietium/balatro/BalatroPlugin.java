package cn.quotidietium.balatro;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 小丑牌 (Balatro) 插件主类。
 *
 * <p>架构总览见 {@code note/项目计划书.md}：
 * <ul>
 *   <li>{@code engine/} 纯逻辑（移植 balatro 网页，零 Bukkit 依赖，可单测）；</li>
 *   <li>{@code api/} 预留扩展面（自定义事件 + 服务接口）；</li>
 *   <li>{@code session/} 每玩家会话；{@code render/} 全息渲染；{@code listener/} 射线交互。</li>
 * </ul>
 */
public final class BalatroPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // 后续 step 逐步装配：SessionManager / 命令 / 监听器 / 服务
        getLogger().info("Balatro v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        // 后续 step：SessionManager.shutdownAll()
        getLogger().info("Balatro disabled.");
    }
}
