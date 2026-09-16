package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import dev.server.casino.integration.CasinoPermissions;

import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

class CasinoPermissionsTest {
    private Permissible player(Map<String, Boolean> explicit) {
        return (Permissible)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {Permissible.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "isPermissionSet" -> explicit.containsKey(args[0]);
                                    case "hasPermission" ->
                                            explicit.getOrDefault(
                                                    args[0], args[0].equals("casino.use"));
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }

    @Test
    void explicitOldDenyCannotBeBypassedByNewDefaultOrGrant() {
        assertFalse(CasinoPermissions.allowed(player(Map.of("servermines.use", false)), "use"));
        assertFalse(
                CasinoPermissions.allowed(
                        player(Map.of("servermines.use", false, "casino.use", true)), "use"));
        assertFalse(
                CasinoPermissions.allowed(
                        player(Map.of("servermines.machine", false, "casino.machine", true)),
                        "machine"));
    }

    @Test
    void explicitNewDenyWinsAndOldGrantsStillWork() {
        assertFalse(
                CasinoPermissions.allowed(
                        player(Map.of("casino.machine", false, "servermines.machine", true)),
                        "machine"));
        assertTrue(
                CasinoPermissions.allowed(player(Map.of("servermines.machine", true)), "machine"));
        assertTrue(CasinoPermissions.allowed(player(Map.of()), "use"));
        assertFalse(CasinoPermissions.allowed(player(Map.of()), "machine"));
    }
}
