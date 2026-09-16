package dev.server.casino.economy;

import java.util.UUID;

/** Internal settlement access in integer cents. Public providers implement EconomyProvider. */
public interface Wallet {
    default boolean available() {
        return true;
    }

    boolean take(UUID player, long cents);

    boolean give(UUID player, long cents);
}
