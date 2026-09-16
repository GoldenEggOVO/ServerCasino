package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Random;

class CasinoDemoRoundTest {
    @Test
    void configuredStakeIsCapturedAndLockedDuringRound() {
        var r = new FrozenCasinoDemoRound("crash", new Random(7));
        r.setConfiguredStake(2500);
        r.start(0);
        r.point = 500;
        r.setConfiguredStake(5000);
        assertEquals(2500, r.configuredStake);
        assertTrue(r.cash(300));
        assertEquals(2750, r.payout);
        r.tick(999999);
        r.setConfiguredStake(10000);
        r.start(1000000);
        assertEquals(10000, r.stake);
    }

    @Test
    void minesCountIsBoundedAndLockedUntilSettlement() {
        var r = new FrozenCasinoDemoRound("mines", new Random(7));
        r.changeMines(-100);
        assertEquals(1, r.mineCount);
        r.changeMines(100);
        assertEquals(24, r.mineCount);
        r.setConfiguredStake(2500);
        r.start(0);
        assertEquals(24, Integer.bitCount(r.mines.mask));
        assertEquals(2500, r.mines.stake);
        r.changeMines(-1);
        assertEquals(24, r.mineCount);
        int safe = 0;
        while ((r.mines.mask & (1 << safe)) != 0) safe++;
        r.reveal(safe);
        assertFalse(r.active);
        assertEquals(r.mines.payout(), r.payout);
        r.changeMines(-1);
        assertEquals(23, r.mineCount);
    }

    @Test
    void stakeRangeRejectsOutOfBoundsAndDoubleUsesConfiguredAmount() {
        var r = new FrozenCasinoDemoRound("blackjack", new Random(3));
        r.setConfiguredStake(99);
        r.setConfiguredStake(10001);
        r.setConfiguredStake(150);
        assertEquals(1000, r.configuredStake);
        r.setConfiguredStake(3500);
        r.start(1);
        r.player.clear();
        r.player.addAll(java.util.List.of(2, 3));
        r.dealer.clear();
        r.dealer.addAll(java.util.List.of(9, 6));
        r.active = true;
        r.doubleDown();
        assertEquals(7000, r.stake);
        assertEquals(CasinoRules.blackjackPayout(7000, r.player, r.dealer), r.payout);
    }

    @Test
    void minesRequiresSafeRevealAndPaysOnlyOnce() {
        var r = new FrozenCasinoDemoRound("mines", new Random(7));
        r.start(0);
        assertFalse(r.cash(0));
        int safe = 0;
        while ((r.mines.mask & (1 << safe)) != 0) safe++;
        r.reveal(safe);
        assertTrue(r.cash(0));
        assertFalse(r.cash(0));
        assertTrue(r.payout > 0);
    }

    @Test
    void crashCannotCashAfterItsPointEvenWithoutAnAnimationTick() {
        var r = new FrozenCasinoDemoRound("crash", new Random(7));
        r.start(100);
        r.point = 150;
        assertFalse(r.cash(1700));
        assertFalse(r.active);
        assertEquals(0, r.payout);
    }

    @Test
    void crashCashIsFinalAndRestartIsExplicit() {
        var r = new FrozenCasinoDemoRound("crash", new Random(7));
        r.start(0);
        r.point = 500;
        assertTrue(r.cash(300));
        assertEquals(1100, r.payout);
        assertTrue(r.active);
        r.tick(600);
        assertTrue(r.value > 110);
        r.start(700);
        assertEquals(0, r.started);
        assertFalse(r.cash(700));
        r.tick(999999);
        assertEquals(1100, r.payout);
        assertFalse(r.cash(300));
    }

    @Test
    void blackjackDoubleDrawsExactlyOneCardAndSettlesDoubleStake() {
        var r = new FrozenCasinoDemoRound("blackjack", new Random(3));
        r.start(1);
        r.player.clear();
        r.player.addAll(java.util.List.of(2, 3));
        r.dealer.clear();
        r.dealer.addAll(java.util.List.of(9, 6));
        r.active = true;
        r.doubleDown();
        assertEquals(3, r.player.size());
        assertFalse(r.active);
        assertEquals(CasinoRules.blackjackPayout(2000, r.player, r.dealer), r.payout);
        int next = r.next;
        r.doubleDown();
        assertEquals(next, r.next);
    }

    @Test
    void blackjackCannotDoubleAfterHitting() {
        var r = new FrozenCasinoDemoRound("blackjack", new Random(3));
        r.start(1);
        r.player.clear();
        r.player.addAll(java.util.List.of(1, 2, 3));
        r.active = true;
        int next = r.next;
        r.doubleDown();
        assertEquals(next, r.next);
        assertTrue(r.active);
    }

    @Test
    void blackjackUsesOneDeckAndSettlesAgainstRules() {
        var r = new FrozenCasinoDemoRound("blackjack", new Random(3));
        r.start(0);
        assertEquals(52, r.deck.stream().distinct().count());
        if (r.active) r.stand();
        assertEquals(CasinoRules.blackjackPayout(1000, r.player, r.dealer), r.payout);
    }
}
