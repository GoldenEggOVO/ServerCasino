package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MachineGeometryTest {
    @Test
    void compactMinesFitsTwoBlocksAndKeepsGridBelowEyeLevel() {
        double scale = MachineGeometry.machineScale("mines");
        assertEquals(2, 3.5 * scale, 1e-6);
        assertTrue(3.3 * scale <= 2);
        assertTrue(1.02 * scale < .65);
        assertTrue((MachineGeometry.mineCell(0).y() + .16) * scale < .9);
        assertTrue(.43 * scale < .51 * scale);
    }

    @Test
    void clientItemRotationMatchesInteractionCoordinatesAndPressesInward() {
        for (int yaw = 0; yaw < 360; yaw += 90) {
            var pose = MachineGeometry.itemPose(1, 0, -.055);
            // Minecraft DisplayRenderer uses -yaw; ItemDisplayRenderer adds PI around Y.
            var matrix =
                    new org.joml.Matrix4f()
                            .rotateY((float) Math.toRadians(-yaw))
                            .translate(pose.getTranslation())
                            .rotate(pose.getLeftRotation())
                            .scale(pose.getScale())
                            .rotate(pose.getRightRotation())
                            .rotateY((float) Math.PI);
            var actual = matrix.transformPosition(new org.joml.Vector3f(.4f, 1, .8f));
            var expected = MachineGeometry.rotate(.4, 1, .8 - .055, yaw);
            assertEquals(expected.x(), actual.x, 1e-6);
            assertEquals(expected.y(), actual.y, 1e-6);
            assertEquals(expected.z(), actual.z, 1e-6);
            assertEquals(0, pose.getTranslation().y);
            assertTrue(pose.getTranslation().z < 0);
        }
    }

    @Test
    void flippingMineCubesRemainInFrontOfPanel() {
        assertTrue(MachineGeometry.mineCell(0).y() - .32 / 2 * Math.sqrt(2) > 1.02);
    }

    @Test
    void tiltedPressFollowsPanelNormal() {
        double pitch = -Math.toRadians(35);
        var p = MachineGeometry.buttonPose(.6, pitch, .055);
        var normal = new org.joml.Vector3f(0, 0, 1).rotateX((float) pitch);
        assertEquals(-.055, p.getTranslation().dot(normal), 1e-6);
        assertEquals(
                0,
                p.getTranslation().dot(new org.joml.Vector3f(0, 1, 0).rotateX((float) pitch)),
                1e-6);
    }

    @Test
    void frontControlsRotateWithCabinetInEveryCardinalDirection() {
        double[][] expected = {{0, .8}, {-.8, 0}, {0, -.8}, {.8, 0}};
        for (int i = 0; i < 4; i++) {
            var p = MachineGeometry.rotate(0, 1, .8, i * 90);
            assertEquals(expected[i][0], p.x(), 1e-6);
            assertEquals(expected[i][1], p.z(), 1e-6);
            assertEquals(1, p.y());
        }
    }

    @Test
    void miningCellsRemainDistinctAndAboveControls() {
        var first = MachineGeometry.mineCell(0);
        var last = MachineGeometry.mineCell(24);
        assertEquals(-1.06, first.x(), 1e-6);
        assertEquals(1.29, first.y(), 1e-6);
        assertEquals(-1.02, first.z(), 1e-6);
        assertEquals(1.06, last.x(), 1e-6);
        assertEquals(first.y(), last.y());
        assertEquals(1.02, last.z(), 1e-6);
        for (int i = 0; i < 24; i++)
            assertNotEquals(MachineGeometry.mineCell(i), MachineGeometry.mineCell(i + 1));
    }

    @Test
    void rocketKeepsClimbingUntilLargeCrashPoint() {
        assertTrue(MachineGeometry.rocketHeight(1000) < MachineGeometry.rocketHeight(9000));
        assertTrue(MachineGeometry.rocketHeight(9000) < MachineGeometry.rocketHeight(10000));
        assertTrue(MachineGeometry.rocketHeight(10000) < 2.65);
    }

    @Test
    void cardsAndRocketStayInsideTheirDisplayAreas() {
        for (int n = 1; n <= 12; n++)
            for (int i = 0; i < n; i++)
                assertTrue(Math.abs(MachineGeometry.cardX(i, n)) <= 1.2 + 1e-6);
        assertEquals(1.65, MachineGeometry.rocketHeight(100), 1e-6);
        assertTrue(MachineGeometry.rocketHeight(100000) <= 3.05);
    }
}
