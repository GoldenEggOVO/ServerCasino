package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class MinesRoundTest {
    @Test
    void safeRevealDuplicateAndCashout() {
        var r = MinesRound.create(UUID.randomUUID(), 1000, 3, false, 7);
        r.phase = MinesRound.Phase.ACTIVE;
        r.reveal(5);
        assertEquals(1, r.safeCount());
        assertEquals(1113, r.payout());
        assertThrows(IllegalStateException.class, () -> r.reveal(5));
        assertEquals(1, r.safeCount());
    }

    @Test
    void mineLosesAndStopsFurtherMoves() {
        var r = MinesRound.create(UUID.randomUUID(), 1000, 3, false, 7);
        r.phase = MinesRound.Phase.ACTIVE;
        r.reveal(0);
        assertEquals(MinesRound.Phase.LOST, r.phase);
        assertEquals(0, r.payout());
        assertThrows(IllegalStateException.class, () -> r.reveal(5));
    }

    @Test
    void allSafeCellsPayExactlyOnce() {
        var r = MinesRound.create(UUID.randomUUID(), 100, 24, true, (1 << 24) - 1);
        r.phase = MinesRound.Phase.ACTIVE;
        r.reveal(24);
        assertEquals(2450, r.payout());
        assertTrue(r.allSafe());
    }

    @Test
    void validatesStakeMinesAndCells() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MinesRound.create(UUID.randomUUID(), 0, 3, false, 7));
        assertThrows(
                IllegalArgumentException.class,
                () -> MinesRound.create(UUID.randomUUID(), 10001, 3, false, 7));
        assertThrows(
                IllegalArgumentException.class,
                () -> MinesRound.create(UUID.randomUUID(), 100, 0, false, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> MinesRound.create(UUID.randomUUID(), 100, 3, false, 3));
        var r = MinesRound.create(UUID.randomUUID(), 100, 3, true, 7);
        r.phase = MinesRound.Phase.ACTIVE;
        assertThrows(IllegalArgumentException.class, () -> r.reveal(25));
        assertThrows(IllegalStateException.class, r::payout);
    }

    @Test
    void payoutsMatchProbabilityAcrossEveryDifficulty() {
        for (int m = 1; m < 25; m++) {
            var r = MinesRound.create(UUID.randomUUID(), 10000, m, true, (1 << m) - 1);
            r.phase = MinesRound.Phase.ACTIVE;
            double probability = 1;
            for (int k = 1; k <= 25 - m; k++) {
                r.reveal(m + k - 1);
                probability *= (double) (26 - m - k) / (26 - k);
                double expected = r.payout() * probability;
                assertTrue(expected <= 9800.00001 && expected > 9799, "m=" + m + " k=" + k);
            }
        }
    }
}
