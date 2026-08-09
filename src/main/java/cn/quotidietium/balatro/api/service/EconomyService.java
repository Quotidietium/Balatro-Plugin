package cn.quotidietium.balatro.api.service;

import java.util.UUID;

/**
 * 外部经济抽象（Vault/PlayerPoints 等），用于把局内奖励发放到服务器经济系统。
 * 默认实现 {@code NoOpEconomy} 为空操作；后续接 Vault 时替换实现即可。
 *
 * <p>注意：Balatro 局内金钱（state.money）与此独立；本接口仅用于"过关奖励 → 服务器经济"。
 */
public interface EconomyService {

    /** 玩家当前外部余额。 */
    long balance(UUID player);

    /** 是否拥有至少 amount。 */
    boolean has(UUID player, long amount);

    /** 给玩家加钱。 */
    void deposit(UUID player, long amount);

    /** 从玩家扣钱（调用方应先 {@link #has} 判断）。 */
    void withdraw(UUID player, long amount);
}
