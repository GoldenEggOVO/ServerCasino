package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ShowcaseGeometryTest {
    @Test
    void hiloUsesActualPercentageForTheRailBoundary() {
        assertEquals(-.55, ShowcaseGeometry.sliderX(25), 1e-9);
        assertEquals(.55, ShowcaseGeometry.sliderX(75), 1e-9);
    }

    @Test
    void hiloControlsShareThePanelsPlane() {
        for (double y : new double[] {-.53, .20}) {
            var p = ShowcaseGeometry.hiloPoint(1, y, .055);
            var local =
                    MachineGeometry.panelPoint(
                            p.x(), p.y() - 1.1, p.z(), -ShowcaseGeometry.HILO_PITCH);
            assertEquals(.055, local.z(), 1e-9);
            assertEquals(y, local.y(), 1e-9);
        }
    }

    @Test
    void dragonHasAShallowSymmetricArcWithSeparatedCells() {
        for (int row = 0; row < 6; row++)
            for (int col = 0; col < 4; col++) {
                var p = ShowcaseGeometry.dragonCell(row, col);
                var opposite = ShowcaseGeometry.dragonCell(row, 3 - col);
                assertEquals(p.x(), -opposite.x(), 1e-9);
                assertEquals(p.z(), opposite.z(), 1e-9);
                assertEquals(1.65, Math.hypot(p.x(), p.z() - .4 + 1.65), 1e-9);
                if (col < 3)
                    assertTrue(ShowcaseGeometry.dragonCell(row, col + 1).x() - p.x() > .32);
            }
    }

    @Test
    void wideFlatBetDoesNotStealTheAdjacentNumberRow() {
        double width = .64, size = .42, pitch = -Math.PI / 2;
        int count = ShowcaseGeometry.hitSlices(width, pitch, size);
        double slice = (width + .015) / count;
        double dozenZ = -.99 - .2 * size;
        double numberZ = -.70 - .2 * size;
        assertTrue(count > 1);
        assertTrue(dozenZ + slice / 2 < numberZ - (.17 + .015) / 2);
        assertEquals(width + .015, count * slice, 1e-9);
    }

    @Test
    void railRoundTripsEverySelectableValue() {
        for (int n = 1; n <= 98; n++)
            assertEquals(n, ShowcaseGeometry.threshold(ShowcaseGeometry.sliderX(n)));
        assertEquals(1, ShowcaseGeometry.threshold(-100));
        assertEquals(98, ShowcaseGeometry.threshold(100));
    }

    @Test
    void rayUsesTheSlopedSurfaceAndRejectsBehindOrParallel() {
        var target = new MachineGeometry.Point(.55, 1.1, 0);
        var eye = new MachineGeometry.Point(0, 1.7, 3);
        double length = Math.sqrt(.55 * .55 + .6 * .6 + 9);
        assertEquals(
                ShowcaseGeometry.threshold(target.x()),
                ShowcaseGeometry.aimedThreshold(
                        eye, new MachineGeometry.Point(.55 / length, -.6 / length, -3 / length)));
        assertEquals(-1, ShowcaseGeometry.aimedThreshold(eye, new MachineGeometry.Point(0, 0, 1)));
        assertEquals(-1, ShowcaseGeometry.aimedThreshold(eye, new MachineGeometry.Point(1, 0, 0)));
    }
}
