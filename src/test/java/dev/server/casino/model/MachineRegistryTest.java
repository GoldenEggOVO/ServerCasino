package dev.server.casino.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class MachineRegistryTest {
    @TempDir Path directory;

    @Test
    void failedReloadKeepsPreviousDefinitionsAndLiveSnapshotsStayImmutable() throws Exception {
        var registry = new MachineRegistry();
        var file = directory.resolve("skin.yml");
        Files.writeString(file, skin("example:first"));
        registry.reload(directory);
        var running = registry.get("blackjack", "custom");
        Files.writeString(file, skin("example:second"));
        registry.reload(directory);
        assertEquals("example:first", running.model("cabinet_blackjack"));
        assertEquals(
                "example:second", registry.get("blackjack", "custom").model("cabinet_blackjack"));
        Files.writeString(
                directory.resolve("broken.yml"), "schema-version: 1\nid: broken\ngame: unknown\n");
        assertThrows(IOException.class, () -> registry.reload(directory));
        assertEquals(
                "example:second", registry.get("blackjack", "custom").model("cabinet_blackjack"));
        assertEquals(13, registry.ids().size());
    }

    @Test
    void rejectsDuplicateAndReservedIdsWithoutChangingBuiltins() throws Exception {
        var registry = new MachineRegistry();
        Files.writeString(directory.resolve("a.yml"), skin("example:a"));
        Files.writeString(directory.resolve("b.yml"), skin("example:b"));
        assertThrows(IOException.class, () -> registry.reload(directory));
        assertEquals(12, registry.ids().size());
        assertThrows(IllegalArgumentException.class, () -> registry.get("mines", "blackjack"));
    }

    private static String skin(String model) {
        return "schema-version: 1\nid: custom\ngame: blackjack\nmodels:\n  cabinet_blackjack: "
                + model
                + "\n";
    }
}
