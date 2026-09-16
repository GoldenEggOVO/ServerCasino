package dev.server.casino.model;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Local block coordinates; Euler angles in degrees, applied X then Y then Z. */
public record ModelTransform(
        double x, double y, double z, double pitch, double yaw, double roll, double scale) {
    public static final ModelTransform IDENTITY = new ModelTransform(0, 0, 0, 0, 0, 0, 1);

    public ModelTransform {
        for (double value : new double[] {x, y, z, pitch, yaw, roll, scale}) {
            if (!Double.isFinite(value))
                throw new IllegalArgumentException("Transform values must be finite");
        }
        if (scale < .001 || scale > 32)
            throw new IllegalArgumentException("scale must be in [0.001, 32]");
        if (Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) > 64)
            throw new IllegalArgumentException("position must be within 64 blocks of the origin");
    }

    public Quaternionf rotation() {
        return new Quaternionf()
                .rotateZ((float) Math.toRadians(roll))
                .rotateY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(pitch));
    }

    public Point apply(double px, double py, double pz) {
        var point = new Vector3f((float) (px * scale), (float) (py * scale), (float) (pz * scale));
        rotation().transform(point);
        return new Point(x + point.x, y + point.y, z + point.z);
    }

    public Point inverse(double px, double py, double pz) {
        var point = new Vector3f((float) (px - x), (float) (py - y), (float) (pz - z));
        rotation().conjugate().transform(point);
        return new Point(point.x / scale, point.y / scale, point.z / scale);
    }

    public record Point(double x, double y, double z) {}
}
