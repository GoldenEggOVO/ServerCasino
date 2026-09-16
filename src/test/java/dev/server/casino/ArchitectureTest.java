package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

class ArchitectureTest {
    @Test
    void pluginManagersAreNotPublicMutableFields() {
        for (var field : CasinoPlugin.class.getDeclaredFields()) {
            assertFalse(
                    Modifier.isPublic(field.getModifiers())
                            && !Modifier.isFinal(field.getModifiers()),
                    field.getName());
        }
    }

    @Test
    void optionalMenusDoNotOwnSettlementServicesOrJobs() throws Exception {
        for (var field : Class.forName("dev.server.casino.CasinoMenus").getDeclaredFields()) {
            assertNotEquals(CasinoService.class, field.getType());
            assertFalse(BukkitTask.class.isAssignableFrom(field.getType()));
        }
    }
}
