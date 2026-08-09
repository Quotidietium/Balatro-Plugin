package cn.quotidietium.balatro.engine;

/** 游戏阶段，对应 balatro state.phase。 */
public enum Phase {
    BLIND_SELECT, // 选择/跳过盲注
    ROUND,        // 出牌回合
    SHOP,         // 商店（0.2.0）
    PACK,         // 开补充包（0.2.0）
    END           // 本局结束（通关或失败）
}
