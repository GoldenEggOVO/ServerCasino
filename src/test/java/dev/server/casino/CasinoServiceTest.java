package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class CasinoServiceTest {
    @TempDir Path dir;
    final UUID player = UUID.randomUUID();

    static class Wallet implements MinesService.Wallet {
        long balance = 100000;
        int debits, credits;
        boolean uncertain, uncertainCredit, refuseCredit;

        public boolean take(UUID p, long cents) {
            debits++;
            balance -= cents;
            if (uncertain) throw new IllegalStateException("uncertain debit");
            return true;
        }

        public boolean give(UUID p, long cents) {
            credits++;
            if (refuseCredit) return false;
            balance += cents;
            if (uncertain || uncertainCredit) throw new IllegalStateException("uncertain credit");
            return true;
        }
    }

    @Test
    void duplicateStartCannotSpendAgainAndPracticeUsesNoWallet() throws Exception {
        Wallet w = new Wallet();
        var s = new CasinoService(dir, w, () -> 0L, new Random(4));
        var r = s.start(player, null, CasinoRound.Game.DICE, 100, 50, false);
        assertThrows(
                IllegalStateException.class,
                () -> s.start(player, null, CasinoRound.Game.DICE, 100, 50, false));
        assertEquals(1, w.debits);
        s.start(player, r.id, CasinoRound.Game.PLINKO, 100, 0, true);
        assertEquals(1, w.debits);
    }

    @Test
    void uncertainDebitPersistsAndNeverReplaysOnRestart() throws Exception {
        Wallet w = new Wallet();
        w.uncertain = true;
        var s = new CasinoService(dir, w, () -> 0L, new Random(2));
        assertThrows(
                IllegalStateException.class,
                () -> s.start(player, null, CasinoRound.Game.LIMBO, 100, 200, false));
        var recovered = new CasinoService(dir, w, () -> 10000L, new Random(3));
        assertEquals(CasinoRound.Phase.DEBIT_PENDING, recovered.get(player).phase);
        assertEquals(1, w.debits);
        assertThrows(
                IllegalStateException.class,
                () -> recovered.start(player, null, CasinoRound.Game.DICE, 100, 50, false));
    }

    @Test
    void crashOfflineAutocashoutUsesSavedPointAndRejectsStaleActions() throws Exception {
        Wallet w = new Wallet();
        AtomicLong now = new AtomicLong(1000);
        var s = new CasinoService(dir, w, now::get, new Random(8));
        var r = s.start(player, null, CasinoRound.Game.CRASH, 100, 200, false);
        now.set(1000000);
        var recovered = new CasinoService(dir, w, now::get, new Random(9));
        var done = recovered.advance(player);
        assertTrue(done.finished());
        assertEquals(CasinoRules.targetPayout(100, 200, r.point), done.payout);
        int credits = w.credits;
        recovered.advance(player);
        assertEquals(credits, w.credits);
        assertThrows(
                IllegalStateException.class,
                () -> recovered.action(player, r.id, r.revision, "cash"));
    }

    @Test
    void blackjackHandAndDeckPersistAndDuplicateHitRejected() throws Exception {
        Wallet w = new Wallet();
        var s = new CasinoService(dir, w, () -> 0L, new Random(6));
        var r = s.start(player, null, CasinoRound.Game.BLACKJACK, 100, 0, true);
        assertEquals(CasinoRound.Phase.ACTIVE, r.phase);
        var recovered = new CasinoService(dir, w, () -> 0L, new Random(9));
        assertEquals(r.hand, recovered.get(player).hand);
        assertEquals(r.deck, recovered.get(player).deck);
        recovered.action(player, r.id, r.revision, "hit");
        assertThrows(
                IllegalStateException.class,
                () -> recovered.action(player, r.id, r.revision, "hit"));
    }

    @Test
    void rejectsOutOfRangeStakeBeforeAnySpend() throws Exception {
        Wallet w = new Wallet();
        var s = new CasinoService(dir, w, () -> 0L, new Random(1));
        assertThrows(
                IllegalArgumentException.class,
                () -> s.start(player, null, CasinoRound.Game.DICE, 10001, 50, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> s.start(player, null, CasinoRound.Game.LIMBO, 100, 100, false));
        assertEquals(0, w.debits);
    }

    static class FixedRandom extends Random {
        private final long sample;

        FixedRandom(long sample) {
            this.sample = sample;
        }

        @Override
        public long nextLong(long bound) {
            return sample;
        }

        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    }

    @Test
    void manualCashLosesWhenTimeCrossesExactlyToCrashBoundary() throws Exception {
        Wallet w = new Wallet();
        AtomicLong now = new AtomicLong();
        // point=196; start and initial advance see 0. Manual advance sees195, cash sees196.
        var s = new CasinoService(dir, w, () -> now.getAndAdd(30), new FixedRandom(500_000_000));
        var r = s.start(player, null, CasinoRound.Game.CRASH, 100, 300, true);
        now.set(2850);
        var done = s.action(player, r.id, r.revision, "cash");
        assertEquals(0, done.payout);
    }

    @Test
    void autoTargetEqualToCrashPointWinsAndPaysOnlyOnce() throws Exception {
        Wallet w = new Wallet();
        AtomicLong now = new AtomicLong();
        var s = new CasinoService(dir, w, now::get, new FixedRandom(500_000_000));
        s.start(player, null, CasinoRound.Game.CRASH, 100, 196, false);
        now.set(2880);
        assertEquals(196, s.advance(player).payout);
        s.advance(player);
        assertEquals(1, w.credits);
    }

    @Test
    void uncertainCreditPersistsAndAppliedResolutionNeverRepays() throws Exception {
        Wallet w = new Wallet();
        w.uncertainCredit = true;
        var s = new CasinoService(dir, w, () -> 0L, new FixedRandom(500_000_000));
        assertThrows(
                IllegalStateException.class,
                () -> s.start(player, null, CasinoRound.Game.LIMBO, 100, 150, false));
        var r = s.get(player);
        assertEquals(CasinoRound.Phase.CREDIT_PENDING, r.phase);
        var recovered = new CasinoService(dir, w, () -> 5000L, new Random());
        recovered.advance(player);
        assertEquals(1, w.credits);
        recovered.resolve(player, r.id, true);
        assertEquals(1, w.credits);
        assertEquals(CasinoRound.Phase.PAID, recovered.get(player).phase);
    }

    @Test
    void rejectedCreditRequiresReconciliationAndManualRetry() throws Exception {
        Wallet w = new Wallet();
        w.refuseCredit = true;
        var s = new CasinoService(dir, w, () -> 0L, new FixedRandom(500_000_000));
        assertThrows(
                IllegalStateException.class,
                () -> s.start(player, null, CasinoRound.Game.LIMBO, 100, 150, false));
        var r = s.get(player);
        s.resolve(player, r.id, false);
        s.advance(player);
        assertEquals(1, w.credits);
        assertEquals(CasinoRound.Phase.CREDIT_READY, s.get(player).phase);
        w.refuseCredit = false;
        var ready = s.get(player);
        s.action(player, ready.id, ready.revision, "retry");
        assertEquals(2, w.credits);
        assertThrows(
                IllegalStateException.class,
                () -> s.action(player, ready.id, ready.revision, "retry"));
    }

    @Test
    void blackjackDoubleAddsOneCardAndPushReturnsTotalStake() throws Exception {
        Wallet w = new Wallet();
        var s = new CasinoService(dir, w, () -> 0L, new FixedRandom(0));
        var r = s.start(player, null, CasinoRound.Game.BLACKJACK, 100, 0, false);
        var done = s.action(player, r.id, r.revision, "double");
        assertEquals(200, done.stake);
        assertEquals(3, done.hand.size());
        assertEquals(200, done.payout);
        assertEquals(100000, w.balance);
        assertEquals(2, w.debits);
        assertEquals(1, w.credits);
        assertThrows(
                IllegalStateException.class, () -> s.action(player, r.id, r.revision, "double"));
    }

    @Test
    void uncertainDoubleRecoversWithoutSecondDebit() throws Exception {
        Wallet w = new Wallet();
        var s = new CasinoService(dir, w, () -> 0L, new FixedRandom(0));
        var r = s.start(player, null, CasinoRound.Game.BLACKJACK, 100, 0, false);
        w.uncertain = true;
        assertThrows(
                IllegalStateException.class, () -> s.action(player, r.id, r.revision, "double"));
        var recovered = new CasinoService(dir, w, () -> 100L, new Random());
        assertEquals(CasinoRound.Phase.DOUBLE_PENDING, recovered.get(player).phase);
        w.uncertain = false;
        recovered.resolve(player, r.id, true);
        assertEquals(2, w.debits);
        assertEquals(0, w.credits);
        var done = recovered.advance(player);
        assertTrue(done.finished());
        assertEquals(200, done.stake);
        assertEquals(2, w.debits);
        assertEquals(1, w.credits);
    }

    @Test
    void storageFailurePreventsAnyWithdrawal() throws Exception {
        Wallet w = new Wallet();
        var s = new CasinoService(dir, w, () -> 0L, new FixedRandom(0));
        java.nio.file.Files.createDirectory(dir.resolve(player + ".tmp"));
        assertThrows(
                java.io.IOException.class,
                () -> s.start(player, null, CasinoRound.Game.DICE, 100, 50, false));
        assertEquals(0, w.debits);
    }
}
