package dev.server.casino;

/** Twelve binary choices, identical to the existing Plinko rules. */
public final class PlinkoPath {
    public record Point(double x, double y) {}

    public static Point at(int path, double progress) {
        double time = Math.max(0, Math.min(12, progress));
        int complete = (int) time;
        double x = 0;
        for (int i = 0; i < complete; i++) {
            x += (path & (1 << i)) == 0 ? -.18 : .18;
        }
        double fraction = time - complete;
        if (complete < 12) {
            x += ((path & (1 << complete)) == 0 ? -.18 : .18) * fraction;
        }
        return new Point(x, 4.5 - time * .27 + .08 * Math.sin(fraction * Math.PI));
    }

    public static int slot(int path) {
        return Integer.bitCount(path & 4095);
    }
}
