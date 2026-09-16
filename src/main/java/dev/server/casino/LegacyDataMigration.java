package dev.server.casino;

import java.io.IOException;
import java.nio.file.*;

/** Preserve pending settlements when the plugin data folder changes name. */
final class LegacyDataMigration {
    private LegacyDataMigration() {}

    static void migrate(Path current) throws IOException {
        Path legacy = current.resolveSibling("ServerMines");
        if (!Files.exists(legacy)) return;
        if (!Files.isDirectory(legacy)
                || Files.isSymbolicLink(legacy)
                || Files.isSymbolicLink(current))
            throw new IOException("Plugin data migration requires ordinary directories");
        if (Files.exists(current)) {
            try (var entries = Files.list(current)) {
                if (entries.findAny().isPresent())
                    throw new IOException(
                            "Both ServerMines and ServerCasino contain data; migration stopped"
                                    + " without overwriting");
            }
            Files.delete(current);
        }
        Files.move(legacy, current);
    }
}
