package dev.server.casino.api;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * Register with Bukkit ServicesManager. All calls run on the server thread. Amounts use integer
 * hundredths of the server's virtual currency. Never report SUCCESS unless applied, or DECLINED
 * unless definitely not applied. UNKNOWN and thrown exceptions retain the persisted transaction for
 * manual reconciliation.
 */
public interface EconomyProvider {
    enum Result {
        SUCCESS,
        DECLINED,
        UNAVAILABLE,
        UNKNOWN
    }

    boolean available();

    OptionalLong balance(UUID player);

    Result withdraw(UUID player, long cents);

    Result deposit(UUID player, long cents);
}
