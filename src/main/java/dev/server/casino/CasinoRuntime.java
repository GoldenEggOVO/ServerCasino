package dev.server.casino;

import dev.server.casino.economy.EconomyAccess;
import dev.server.casino.economy.Wallet;

import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Owns persisted settlement independently of whether a menu is installed. */
final class CasinoRuntime implements AutoCloseable {
    private final CasinoPlugin plugin;
    private final MinesService mines;
    private final CasinoService games;
    private final BukkitTask task;
    private Consumer<CasinoRound> updates = round -> {};

    CasinoRuntime(CasinoPlugin plugin, EconomyAccess economy) throws IOException {
        this.plugin = plugin;
        Wallet wallet =
                new Wallet() {
                    public boolean available() {
                        return economy.available();
                    }

                    public boolean take(UUID player, long cents) {
                        return economy.take(player, cents);
                    }

                    public boolean give(UUID player, long cents) {
                        return economy.give(player, cents);
                    }
                };
        var data = plugin.getDataFolder().toPath();
        mines = new MinesService(new RoundStore(data.resolve("rounds")), wallet);
        games = new CasinoService(data.resolve("casino-rounds"), wallet);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 5, 5);
    }

    CasinoService games() {
        return games;
    }

    void onRoundUpdated(Consumer<CasinoRound> listener) {
        updates = listener;
    }

    void resolve(boolean minesRound, UUID player, UUID round, boolean applied) throws IOException {
        if (minesRound) mines.resolve(player, round, applied);
        else games.resolve(player, round, applied);
    }

    private void tick() {
        for (UUID player : games.players()) {
            var before = games.get(player);
            if (before.finished()
                    || before.phase == CasinoRound.Phase.CREDIT_PENDING
                    || before.phase == CasinoRound.Phase.DEBIT_PENDING
                    || before.phase == CasinoRound.Phase.DOUBLE_PENDING) continue;
            try {
                updates.accept(games.advance(player));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Settlement paused: player=" + player, ex);
            }
        }
    }

    @Override
    public void close() {
        task.cancel();
        updates = round -> {};
    }
}
