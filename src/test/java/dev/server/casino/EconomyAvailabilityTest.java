package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

class EconomyAvailabilityTest {
    @TempDir Path dir;

    private MinesService.Wallet unavailable() {
        return new MinesService.Wallet() {
            public boolean available() {
                return false;
            }

            public boolean take(UUID player, long cents) {
                fail("No debit allowed");
                return false;
            }

            public boolean give(UUID player, long cents) {
                fail("No credit allowed");
                return false;
            }
        };
    }

    @Test
    void minesWithoutEconomyAllowsPracticeAndRejectsMoneyBeforeWritingPending() throws Exception {
        var service = new MinesService(new RoundStore(dir), unavailable());
        UUID player = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> service.start(player, 100, 24, false));
        assertNull(service.get(player));
        var round = service.start(player, 100, 24, true);
        int safe = 0;
        while ((round.mask & (1 << safe)) != 0) safe++;
        assertEquals(
                MinesRound.Phase.PAID,
                service.reveal(player, round.id, round.revision, safe).phase);
    }

    @Test
    void casinoWithoutEconomyAllowsPracticeAndRejectsMoneyBeforeWritingPending() throws Exception {
        var service = new CasinoService(dir, unavailable());
        UUID player = UUID.randomUUID();
        assertThrows(
                IllegalStateException.class,
                () -> service.start(player, null, CasinoRound.Game.PLINKO, 100, 0, false));
        assertNull(service.get(player));
        assertEquals(
                CasinoRound.Phase.PAID,
                service.start(player, null, CasinoRound.Game.PLINKO, 100, 0, true).phase);
    }

    @Test
    void unavailableEconomyCannotChangeExistingPendingRecord() throws Exception {
        UUID player = UUID.randomUUID();
        var original =
                new MinesService(
                        new RoundStore(dir),
                        new MinesService.Wallet() {
                            public boolean take(UUID id, long cents) {
                                throw new IllegalStateException("Unknown debit");
                            }

                            public boolean give(UUID id, long cents) {
                                fail("No credit allowed");
                                return false;
                            }
                        });
        assertThrows(IllegalStateException.class, () -> original.start(player, 100, 3, false));
        var service = new MinesService(new RoundStore(dir), unavailable());
        var pending = service.get(player);
        assertThrows(IllegalStateException.class, () -> service.start(player, 100, 3, false));
        assertEquals(pending.id, service.get(player).id);
        assertEquals(pending.revision, service.get(player).revision);
        assertEquals(MinesRound.Phase.DEBIT_PENDING, service.get(player).phase);
    }
}
