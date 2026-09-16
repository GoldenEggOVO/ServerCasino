package dev.server.casino;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class RoundStore {
    private final Path dir;
    private final Gson gson = new Gson();

    RoundStore(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
    }

    List<MinesRound> load() throws IOException {
        List<MinesRound> rounds = new ArrayList<>();
        try (var files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                MinesRound r = gson.fromJson(Files.readString(f), MinesRound.class);
                if (r == null || r.id == null || r.player == null || r.phase == null)
                    throw new IOException("无效对局存档: " + f);
                rounds.add(r);
            }
        }
        return rounds;
    }

    void save(MinesRound round) throws IOException {
        Path target = dir.resolve(round.id + ".json"), temp = dir.resolve(round.id + ".tmp");
        byte[] bytes = gson.toJson(round).getBytes(StandardCharsets.UTF_8);
        try (var ch =
                FileChannel.open(
                        temp,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }
        Files.move(
                temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        // Linux 正式服同步目录项；Windows 文件系统不提供目录 FileChannel。
        if (!System.getProperty("os.name").startsWith("Windows"))
            try (var ch = FileChannel.open(dir, StandardOpenOption.READ)) {
                ch.force(true);
            }
    }
}
