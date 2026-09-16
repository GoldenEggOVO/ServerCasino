package dev.server.casino.integration;

import org.bukkit.permissions.Permissible;

public final class CasinoPermissions {
    private CasinoPermissions() {}

    public static boolean allowed(Permissible player, String suffix) {
        return player.hasPermission("casino." + suffix);
    }
}
