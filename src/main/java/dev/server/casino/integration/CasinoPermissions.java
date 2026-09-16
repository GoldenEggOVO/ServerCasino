package dev.server.casino.integration;

import org.bukkit.permissions.Permissible;

public final class CasinoPermissions {
    private CasinoPermissions() {}

    public static boolean allowed(Permissible player, String suffix) {
        String current = "casino." + suffix;
        String legacy = "servermines." + suffix;
        if (player.isPermissionSet(legacy) && !player.hasPermission(legacy)) return false;
        if (player.isPermissionSet(current)) return player.hasPermission(current);
        if (player.isPermissionSet(legacy)) return player.hasPermission(legacy);
        return player.hasPermission(current);
    }
}
