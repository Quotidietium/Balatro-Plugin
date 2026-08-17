package cn.quotidietium.balatro.command;

import java.util.ArrayList;
import java.util.List;

/**
 * /balatro use 的目标令牌解析（纯逻辑零 Bukkit，可单测）。
 *
 * <p>全息「确认使用」按钮以 {@code @id1,id2,...} 令牌携带确认时刻的选中牌快照
 * （手牌从左到右序）。与手牌序号不同，卡 id 在确认到点击之间不会漂移：命令层
 * 校验快照 id 仍全部在手牌，缺一即取消——序号错位类 TOCTOU（R52 同族）被结构性
 * 排除。手动输入仍走原来的手牌序号路径（向后兼容，不带 {@code @} 则不校验）。
 */
final class UseTargets {

    private UseTargets() {
    }

    /**
     * 解析 {@code @id1,id2} 形式的目标令牌。
     *
     * @param token 以 '@' 开头的令牌
     * @return 卡 id 列表（保持给定次序）；任何非法形态（空段/非数字/≤0/重复/超长）返回 null
     */
    static List<Integer> parseAtIds(String token) {
        if (token == null || token.length() < 2 || token.charAt(0) != '@') return null;
        String body = token.substring(1);
        if (body.length() > 63) return null; // 防超长注入（选中至多 5 张，正常远小于此）
        String[] parts = body.split(",", -1);
        if (parts.length > 5) return null;
        List<Integer> ids = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 9) return null; // 对齐 parseIntSafe 限长，防溢出攻击面
            for (int i = 0; i < p.length(); i++) {
                char ch = p.charAt(i);
                if (ch < '0' || ch > '9') return null;
            }
            int v = Integer.parseInt(p);
            if (v < 1) return null; // 卡 id 从 1 起（RunState.cardIdSeq=1）
            if (ids.contains(v)) return null; // 重复 id 视为非法（引擎 targets 也会拒，这里给出更早的明确失败）
            ids.add(v);
        }
        return ids;
    }
}
