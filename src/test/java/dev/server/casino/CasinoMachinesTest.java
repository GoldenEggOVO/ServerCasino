package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import dev.server.casino.game.blackjack.BlackjackMachine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

class CasinoMachinesTest {
    @Test
    void screenHidesDealerTotalUntilRoundEnds() {
        var round = new FrozenCasinoDemoRound("blackjack", new Random(3));
        round.player.addAll(List.of(2, 3));
        round.dealer.addAll(List.of(9, 6));
        round.active = true;
        assertEquals(
                "PLAYER  7\nDEALER  ?",
                BlackjackMachine.readout(round.player, round.dealer, round.active));
        round.active = false;
        assertEquals(
                "PLAYER  7\nDEALER  17",
                BlackjackMachine.readout(round.player, round.dealer, round.active));
    }

    @Test
    void emptyScreenHasBothLabelsWithoutInventedTotals() {
        assertEquals("PLAYER  —\nDEALER  —", BlackjackMachine.readout(List.of(), List.of(), false));
    }
}
