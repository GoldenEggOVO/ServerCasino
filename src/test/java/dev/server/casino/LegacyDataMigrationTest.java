package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class LegacyDataMigrationTest {
    @TempDir Path root;

    @Test
    void keepsRoundBytesAndDoesNotMigrateTwice() throws Exception {
        Path old = Files.createDirectories(root.resolve("ServerMines/rounds"));
        Files.writeString(old.resolve("pending.json"), "pending-payment");
        LegacyDataMigration.migrate(root.resolve("ServerCasino"));
        assertEquals(
                "pending-payment",
                Files.readString(root.resolve("ServerCasino/rounds/pending.json")));
        assertFalse(Files.exists(root.resolve("ServerMines")));
        LegacyDataMigration.migrate(root.resolve("ServerCasino"));
    }

    @Test
    void conflictingDataStopsWithoutOverwritingEitherDirectory() throws Exception {
        Files.createDirectories(root.resolve("ServerMines"));
        Files.writeString(root.resolve("ServerMines/config.yml"), "old");
        Files.createDirectories(root.resolve("ServerCasino"));
        Files.writeString(root.resolve("ServerCasino/config.yml"), "new");
        assertThrows(
                java.io.IOException.class,
                () -> LegacyDataMigration.migrate(root.resolve("ServerCasino")));
        assertEquals("old", Files.readString(root.resolve("ServerMines/config.yml")));
        assertEquals("new", Files.readString(root.resolve("ServerCasino/config.yml")));
    }

    @Test
    void emptyNewDataDirectoryDoesNotBlockMigration() throws Exception {
        Files.createDirectories(root.resolve("ServerMines"));
        Files.writeString(root.resolve("ServerMines/config.yml"), "old");
        Files.createDirectories(root.resolve("ServerCasino"));
        LegacyDataMigration.migrate(root.resolve("ServerCasino"));
        assertEquals("old", Files.readString(root.resolve("ServerCasino/config.yml")));
    }
}
