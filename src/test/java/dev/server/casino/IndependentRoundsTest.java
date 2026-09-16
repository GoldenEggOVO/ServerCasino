package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import dev.server.casino.game.crash.CrashRound;
import dev.server.casino.game.keno.KenoRound;
import dev.server.casino.game.mines.MinesDemoRound;
import dev.server.casino.game.penguin_cross.PenguinCrossRound;
import dev.server.casino.game.slots.SlotsRound;
import dev.server.casino.game.wheel_of_fortune.WheelOfFortuneRound;

import org.junit.jupiter.api.Test;

import java.util.Random;

class IndependentRoundsTest {
    @Test
    void publishedStakeLimitsAndActiveLockApplyToConcreteRounds() {
        PenguinCrossRound round = new PenguinCrossRound(new Random(4));
        for (long invalid : new long[] {Long.MIN_VALUE, 99, 10001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> round.setStake(invalid));
        }
        round.setStake(2400);
        round.action("play");
        assertThrows(IllegalStateException.class, () -> round.setStake(100));
        assertEquals(2400, round.stake());
    }

    @Test
    void fortuneReplayPreservesStakeAndUsesOneDrawPerAnimationRequest() {
        Random random =
                new Random(0) {
                    int call;

                    @Override
                    public int nextInt(int bound) {
                        return new int[] {0, 10, 4}[call++];
                    }
                };
        WheelOfFortuneRound round = new WheelOfFortuneRound(random);
        round.setStake(2400);
        round.action("play");
        assertTrue(round.active());
        assertEquals(0, round.segment());
        round.replayFortune();
        assertEquals(10, round.segment());
        assertTrue(round.active());
        round.replayFortune();
        assertEquals(600, round.payout());
        assertFalse(round.active());
        round.replayFortune();
        assertEquals(600, round.payout());
    }

    @Test
    void minesSnapshotsCannotRevealOrSettleTheLivePracticeRound() {
        MinesDemoRound round = new MinesDemoRound(new Random(7));
        round.start(0);
        MinesRound snapshot = round.mines();
        int safe = 0;
        while ((snapshot.mask() & (1 << safe)) != 0) {
            safe++;
        }
        snapshot.reveal(safe);
        snapshot.payPractice();
        assertEquals(0, round.mines().revealed());
        assertEquals(MinesRound.Phase.ACTIVE, round.mines().phase());
        assertFalse(round.cash(0));
        round.reveal(safe);
        assertTrue(round.cash(0));
        assertFalse(round.cash(0));
    }

    @Test
    void seededGamesAreRepeatableAndHaveIndependentState() {
        SlotsRound first = new SlotsRound(new Random(41));
        SlotsRound second = new SlotsRound(new Random(41));
        KenoRound keno = new KenoRound(new Random(41));
        keno.action("select:7");
        first.action("play");
        second.action("play");
        assertEquals(first.symbols(), second.symbols());
        assertEquals(first.payout(), second.payout());
        assertEquals(java.util.Set.of(7), keno.selected());
        assertTrue(keno.drawnNumbers().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> first.symbols().clear());
    }

    @Test
    void collectingCrashDoesNotEndFlightOrAllowRestart() {
        Random random =
                new Random(0) {
                    @Override
                    public long nextLong(long bound) {
                        return 804_000_000L;
                    }
                };
        CrashRound round = new CrashRound(random);
        round.setConfiguredStake(2500);
        round.start(100);
        assertEquals(500, round.crashPoint());
        assertTrue(round.cash(400));
        assertTrue(round.active());
        assertEquals(2750, round.payout());
        round.tick(700);
        assertEquals(120, round.multiplier());
        round.start(800);
        assertEquals(100, round.started());
        assertFalse(round.cash(800));
        round.tick(12100);
        assertFalse(round.active());
        assertEquals(2750, round.payout());
    }
}
