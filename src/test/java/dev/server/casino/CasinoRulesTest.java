package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CasinoRulesTest {
    @Test
    void diceBoundaryAndInclusiveStakePayout() {
        assertEquals(196, CasinoRules.dicePayout(100, 50, 4999));
        assertEquals(0, CasinoRules.dicePayout(100, 50, 5000));
    }

    @Test
    void limboHasImmediateLossAndExactThreshold() {
        assertEquals(98, CasinoRules.point(0));
        assertEquals(196, CasinoRules.point(500_000_000));
        assertEquals(200, CasinoRules.targetPayout(100, 200, 200));
        assertEquals(0, CasinoRules.targetPayout(100, 200, 199));
    }

    @Test
    void plinkoExpectedReturnAndSymmetry() {
        long weighted = 0;
        int combinations = 1;
        for (int slot = 0; slot <= 12; slot++) {
            long p = CasinoRules.plinkoPayout(10000, slot);
            assertEquals(p, CasinoRules.plinkoPayout(10000, 12 - slot));
            weighted += p * combinations;
            if (slot < 12) combinations = combinations * (12 - slot) / (slot + 1);
        }
        assertTrue(weighted / 4096.0 <= 9800);
        assertTrue(weighted / 4096.0 > 9799);
    }

    @Test
    void blackjackSoftAcesAndNatural() {
        assertEquals(21, CasinoRules.total(java.util.List.of(0, 13, 8)));
        assertEquals(17, CasinoRules.total(java.util.List.of(0, 5)));
        assertTrue(CasinoRules.natural(java.util.List.of(0, 12)));
        assertFalse(CasinoRules.natural(java.util.List.of(0, 13, 8)));
        assertEquals(
                250,
                CasinoRules.blackjackPayout(
                        100, java.util.List.of(0, 12), java.util.List.of(9, 8)));
        assertEquals(
                100,
                CasinoRules.blackjackPayout(
                        100, java.util.List.of(0, 12), java.util.List.of(13, 25)));
    }
}
