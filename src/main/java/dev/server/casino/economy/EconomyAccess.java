package dev.server.casino.economy;

import dev.server.casino.api.EconomyProvider;

import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;

/** Resolves the current registered provider for every operation, including unloads. */
public final class EconomyAccess {
    private final Supplier<EconomyProvider> providers;

    public EconomyAccess(Supplier<EconomyProvider> providers) {
        this.providers = providers;
    }

    public boolean available() {
        EconomyProvider provider = providers.get();
        return provider != null && provider.available();
    }

    public OptionalLong balance(UUID player) {
        EconomyProvider provider = providers.get();
        return provider == null || !provider.available()
                ? OptionalLong.empty()
                : provider.balance(player);
    }

    public boolean take(UUID player, long cents) {
        return transfer(player, cents, true);
    }

    public boolean give(UUID player, long cents) {
        return transfer(player, cents, false);
    }

    private boolean transfer(UUID player, long cents, boolean debit) {
        if (cents < 0) throw new IllegalArgumentException("Negative amount");
        EconomyProvider provider = providers.get();
        if (provider == null || !provider.available())
            throw new IllegalStateException("经济服务不可用，操作未完成");
        EconomyProvider.Result result =
                debit ? provider.withdraw(player, cents) : provider.deposit(player, cents);
        if (result == EconomyProvider.Result.SUCCESS) return true;
        if (result == EconomyProvider.Result.DECLINED) return false;
        throw new IllegalStateException("经济操作结果未确认，请联系管理员核对");
    }
}
