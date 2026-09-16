package dev.server.casino.integration;

import dev.server.casino.api.EconomyProvider;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalLong;
import java.util.UUID;

/** Optional Vault API adapter; no Vault classes are linked when Vault is absent. */
public final class VaultEconomyProvider implements EconomyProvider {
    private final Class<?> economyType;
    private final Method balance;
    private final Method withdraw;
    private final Method deposit;
    private final Method success;

    private VaultEconomyProvider(ClassLoader loader) throws ReflectiveOperationException {
        economyType = Class.forName("net.milkbowl.vault.economy.Economy", true, loader);
        balance = economyType.getMethod("getBalance", OfflinePlayer.class);
        withdraw = economyType.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
        deposit = economyType.getMethod("depositPlayer", OfflinePlayer.class, double.class);
        success = withdraw.getReturnType().getMethod("transactionSuccess");
    }

    public static EconomyProvider discover() {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) return null;
        try {
            return new VaultEconomyProvider(vault.getClass().getClassLoader());
        } catch (ReflectiveOperationException | LinkageError ex) {
            return null;
        }
    }

    private Object provider() {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) return null;
        var registration = Bukkit.getServicesManager().getRegistration(economyType);
        return registration == null ? null : registration.getProvider();
    }

    @Override
    public boolean available() {
        return provider() != null;
    }

    @Override
    public OptionalLong balance(UUID player) {
        Object provider = provider();
        if (provider == null) return OptionalLong.empty();
        try {
            double amount =
                    ((Number) balance.invoke(provider, Bukkit.getOfflinePlayer(player)))
                            .doubleValue();
            return OptionalLong.of(
                    BigDecimal.valueOf(amount)
                            .movePointRight(2)
                            .setScale(0, RoundingMode.DOWN)
                            .longValueExact());
        } catch (ReflectiveOperationException | ArithmeticException | IllegalArgumentException ex) {
            return OptionalLong.empty();
        }
    }

    @Override
    public Result withdraw(UUID player, long cents) {
        return transfer(withdraw, player, cents);
    }

    @Override
    public Result deposit(UUID player, long cents) {
        return transfer(deposit, player, cents);
    }

    private Result transfer(Method method, UUID player, long cents) {
        Object provider = provider();
        if (provider == null) return Result.UNAVAILABLE;
        try {
            Object response =
                    method.invoke(provider, Bukkit.getOfflinePlayer(player), cents / 100.0);
            return Boolean.TRUE.equals(success.invoke(response)) ? Result.SUCCESS : Result.DECLINED;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return Result.UNKNOWN;
        }
    }
}
