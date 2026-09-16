package dev.server.casino.machine;

import static org.junit.jupiter.api.Assertions.*;

import dev.server.casino.model.MachineDefinition;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlacementStoreTest {
    @TempDir Path directory;

    @Test
    void allGameTypesAndSettingsSurviveRestartAndDeletion() throws Exception {
        var store = new PlacementStore(directory.resolve("placements.json"));
        UUID owner = UUID.randomUUID(), world = UUID.randomUUID();
        var placements = MachineDefinition.games().stream()
                .map(game -> new PlacementStore.Placement(owner, world, 12.5, 64, -7.5,
                        90, MachineDefinition.builtin(game), 2500)).toList();
        store.save(placements);
        assertEquals(new HashSet<>(placements), new HashSet<>(new PlacementStore(directory.resolve("placements.json")).load()));
        store.save(placements.subList(1, placements.size()));
        assertEquals(11, store.load().size());
        store.save(List.of());
        assertTrue(store.load().isEmpty());
    }

    @Test
    void corruptedDataIsRejectedWithoutOverwritingIt() throws Exception {
        Path file = directory.resolve("placements.json");
        Files.writeString(file, "{broken");
        assertThrows(java.io.IOException.class, () -> new PlacementStore(file).load());
        assertEquals("{broken", Files.readString(file));
    }

    @Test
    void failedWriteKeepsPreviousFile() throws Exception {
        var store = new PlacementStore(directory.resolve("placements.json"));
        store.save(List.of());
        String original = Files.readString(directory.resolve("placements.json"));
        Files.createDirectory(directory.resolve("placements.json.tmp"));
        assertThrows(java.io.IOException.class, () -> store.save(List.of()));
        assertEquals(original, Files.readString(directory.resolve("placements.json")));
    }
}
