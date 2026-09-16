package dev.server.casino.machine;

import com.google.gson.Gson;
import dev.server.casino.model.MachineDefinition;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Machine layouts only; in-progress practice rounds are deliberately not persisted. */
final class PlacementStore {
    record Placement(UUID owner, UUID world, double x, double y, double z, float yaw,
                     MachineDefinition definition, long stake) {
        Placement {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(world);
            Objects.requireNonNull(definition);
            if (!MachineDefinition.games().contains(definition.game())
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(yaw) || stake < 100 || stake > 10000 || stake % 100 != 0) {
                throw new IllegalArgumentException("Invalid machine placement");
            }
        }

        Placement withStake(long amount) {
            return new Placement(owner, world, x, y, z, yaw, definition, amount);
        }
    }

    private record Document(int version, List<Placement> machines) {}
    private final Path file;
    private final Gson gson = new Gson();

    PlacementStore(Path file) {
        this.file = file;
    }

    List<Placement> load() throws IOException {
        if (!Files.exists(file)) return List.of();
        try {
            var document = gson.fromJson(Files.readString(file), Document.class);
            if (document == null || document.version != 1 || document.machines == null) {
                throw new IllegalArgumentException("Unsupported placement document");
            }
            var keys = new HashSet<String>();
            for (var placement : document.machines) {
                if (!keys.add(placement.owner + ":" + placement.definition.game())) {
                    throw new IllegalArgumentException("Duplicate owner/game placement");
                }
            }
            return List.copyOf(document.machines);
        } catch (RuntimeException ex) {
            throw new IOException("Invalid machine placements: " + file, ex);
        }
    }

    void save(Collection<Placement> placements) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        var bytes = ByteBuffer.wrap(gson.toJson(new Document(1, List.copyOf(placements)))
                .getBytes(StandardCharsets.UTF_8));
        try (var channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
