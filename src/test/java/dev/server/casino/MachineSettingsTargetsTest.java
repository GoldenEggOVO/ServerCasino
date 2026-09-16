package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class MachineSettingsTargetsTest {
    @Test
    void cabinetRayWorksForAllCardinalOrientations() {
        var bounds = new BoundingBox(-1, 0, -.2, 1, 2, .2);
        for (int yaw = 0; yaw < 360; yaw += 90) {
            var origin = new Location(null, 12, 64, -7, yaw, 0);
            var offset = MachineGeometry.rotate(0, 1, 3, yaw);
            var direction = MachineGeometry.rotate(0, 0, -1, yaw);
            double distance =
                    MachineSettingsTargets.distance(
                            origin,
                            bounds,
                            origin.clone().add(offset.x(), offset.y(), offset.z()),
                            new Vector(direction.x(), direction.y(), direction.z()));
            assertEquals(2.8, distance, 1e-8);
        }
    }

    @Test
    void missesAndDistantCabinetsDoNotOpen() {
        var origin = new Location(null, 0, 0, 0);
        var bounds = new BoundingBox(-1, 0, -.2, 1, 2, .2);
        assertEquals(
                Double.POSITIVE_INFINITY,
                MachineSettingsTargets.distance(
                        origin, bounds, new Location(null, 2, 1, 3), new Vector(0, 0, -1)));
        assertEquals(
                Double.POSITIVE_INFINITY,
                MachineSettingsTargets.distance(
                        origin, bounds, new Location(null, 0, 1, 8), new Vector(0, 0, -1)));
    }
}
