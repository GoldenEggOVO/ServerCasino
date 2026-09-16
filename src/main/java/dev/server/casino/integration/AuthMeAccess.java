package dev.server.casino.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Keeps an installed but disabled or incompatible AuthMe fail-closed. */
public final class AuthMeAccess {
    private AuthMeAccess() {}

    public static boolean authenticated(Player player) {
        var auth = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (auth == null) return true;
        if (!auth.isEnabled()) return false;
        try {
            var api =
                    Class.forName(
                            "fr.xephi.authme.api.v3.AuthMeApi",
                            true,
                            auth.getClass().getClassLoader());
            return Boolean.TRUE.equals(
                    api.getMethod("isAuthenticated", Player.class)
                            .invoke(api.getMethod("getInstance").invoke(null), player));
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }
}
