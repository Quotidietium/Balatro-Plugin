package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.service.EconomyService;
import java.util.UUID;

/** 默认经济：空操作（余额恒 0）。接 Vault/PlayerPoints 时替换实现。 */
public final class NoOpEconomy implements EconomyService {
    @Override
    public long balance(UUID player) {
        return 0;
    }

    @Override
    public boolean has(UUID player, long amount) {
        return amount <= 0;
    }

    @Override
    public void deposit(UUID player, long amount) {
    }

    @Override
    public void withdraw(UUID player, long amount) {
    }
}
