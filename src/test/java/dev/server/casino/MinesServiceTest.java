package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class MinesServiceTest {
    @TempDir Path dir;

    static class Wallet implements MinesService.Wallet {
        int takes, gives;
        long balance = 100000;
        boolean throwGive, throwTake, denyTake;

        public boolean take(UUID p, long c) {
            takes++;
            if (throwTake) throw new IllegalStateException("provider failure");
            if (denyTake) return false;
            balance -= c;
            return true;
        }

        public boolean give(UUID p, long c) {
            gives++;
            if (throwGive) throw new IllegalStateException("provider failure");
            balance += c;
            return true;
        }
    }

    @Test
    void restartRetainsBoardAndDuplicateCashoutCannotPayTwice() throws Exception {
        var store = new RoundStore(dir);
        var w = new Wallet();
        var s = new MinesService(store, w);
        UUID p = UUID.randomUUID();
        var r = s.start(p, 1000, 3, false);
        assertEquals(3, Integer.bitCount(r.mask));
        assertEquals(1, w.takes);
        var initial = s;
        assertThrows(IllegalStateException.class, () -> initial.start(p, 1000, 3, false));
        s = new MinesService(store, w);
        assertEquals(r.mask, s.get(p).mask);
        assertEquals(1, w.takes);
        int safe = 0;
        while ((r.mask & (1 << safe)) != 0) safe++;
        var revealed = s.reveal(p, r.id, r.revision, safe);
        var service = s;
        assertThrows(IllegalStateException.class, () -> service.reveal(p, r.id, r.revision, 24));
        var paid = s.cashout(p, r.id, revealed.revision);
        assertEquals(MinesRound.Phase.PAID, paid.phase);
        assertThrows(
                IllegalStateException.class, () -> service.cashout(p, r.id, revealed.revision));
        assertEquals(1, w.gives);
    }

    @Test
    void ambiguousCreditRemainsPendingAfterRestartUntilAdminResolves() throws Exception {
        var store = new RoundStore(dir);
        var w = new Wallet();
        var s = new MinesService(store, w);
        UUID p = UUID.randomUUID();
        var r = s.start(p, 100, 1, false);
        int safe = 0;
        while ((r.mask & (1 << safe)) != 0) safe++;
        r = s.reveal(p, r.id, r.revision, safe);
        w.throwGive = true;
        var active = r;
        assertThrows(IllegalStateException.class, () -> s.cashout(p, active.id, active.revision));
        var recovered = new MinesService(store, w);
        assertEquals(MinesRound.Phase.CREDIT_PENDING, recovered.get(p).phase);
        assertThrows(IllegalStateException.class, () -> recovered.start(p, 100, 1, false));
        assertEquals(1, w.gives);
        recovered.resolve(p, r.id, true);
        assertEquals(MinesRound.Phase.PAID, recovered.get(p).phase);
        assertEquals(1, w.gives);
    }

    @Test
    void failedDebitDoesNotStartAndUncertainDebitIsNotRetried() throws Exception {
        var store = new RoundStore(dir);
        var w = new Wallet();
        var s = new MinesService(store, w);
        UUID p = UUID.randomUUID();
        w.denyTake = true;
        assertThrows(IllegalStateException.class, () -> s.start(p, 100, 3, false));
        assertEquals(MinesRound.Phase.CANCELLED, s.get(p).phase);
        w.denyTake = false;
        w.throwTake = true;
        assertThrows(IllegalStateException.class, () -> s.start(p, 100, 3, false));
        var recovered = new MinesService(store, w);
        assertEquals(MinesRound.Phase.DEBIT_PENDING, recovered.get(p).phase);
        assertThrows(IllegalStateException.class, () -> recovered.start(p, 100, 3, false));
        assertEquals(2, w.takes);
    }

    @Test
    void practiceNeverTouchesWalletAndBoardIsFixed() throws Exception {
        var w = new Wallet();
        var s = new MinesService(new RoundStore(dir), w);
        UUID p = UUID.randomUUID();
        var r = s.start(p, 100, 24, true);
        int safe = 0;
        while ((r.mask & (1 << safe)) != 0) safe++;
        var paid = s.reveal(p, r.id, r.revision, safe);
        assertEquals(MinesRound.Phase.PAID, paid.phase);
        assertEquals(2450, paid.payoutDue);
        assertEquals(0, w.takes + w.gives);
    }

    @Test
    void anotherPlayersRoundAndInvalidInputsAreRejected() throws Exception {
        var s = new MinesService(new RoundStore(dir), new Wallet());
        var r = s.start(UUID.randomUUID(), 100, 3, true);
        assertThrows(
                IllegalStateException.class,
                () -> s.reveal(UUID.randomUUID(), r.id, r.revision, 4));
        assertThrows(IllegalArgumentException.class, () -> CasinoUi.parse("NaN", 100));
        assertThrows(IllegalArgumentException.class, () -> CasinoUi.parse("-1", 100));
        assertThrows(IllegalArgumentException.class, () -> CasinoUi.parse("101", 100));
    }
}
