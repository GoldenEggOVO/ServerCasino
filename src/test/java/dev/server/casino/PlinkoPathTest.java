package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlinkoPathTest {
    @Test
    void everyPathEndsAtItsAwardSlot() {
        for (int p = 0; p < 4096; p++) {
            var end = PlinkoPath.at(p, 12);
            assertEquals((PlinkoPath.slot(p) - 6) * .36, end.x(), 1e-9);
            assertEquals(1.26, end.y(), 1e-9);
        }
    }

    @Test
    void interpolationIsContinuousAtEveryPeg() {
        for (int p = 0; p < 4096; p++)
            for (int i = 1; i < 12; i++) {
                var before = PlinkoPath.at(p, i - 1e-7);
                var after = PlinkoPath.at(p, i + 1e-7);
                assertEquals(before.x(), after.x(), 1e-6);
                assertEquals(before.y(), after.y(), 1e-6);
            }
    }
}
