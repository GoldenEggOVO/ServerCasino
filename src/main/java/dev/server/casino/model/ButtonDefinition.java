package dev.server.casino.model;

/** Button geometry shared by the renderer, press animation and local ray target. */
public record ButtonDefinition(
        ModelTransform transform,
        double width,
        double height,
        double depth,
        double size,
        double press) {
    public ButtonDefinition {
        for (double value : new double[] {width, height, depth, size, press}) {
            if (!Double.isFinite(value))
                throw new IllegalArgumentException("Button values must be finite");
        }
        if (width < .001
                || height < .001
                || depth < .001
                || size < .001
                || press < 0
                || width > 8
                || height > 8
                || depth > 8
                || size > 8
                || press > 1)
            throw new IllegalArgumentException("Invalid button dimensions or press distance");
    }

    public static ButtonDefinition at(
            double x, double y, double z, double width, double pitch, double size) {
        return new ButtonDefinition(
                new ModelTransform(x, y, z, pitch, 0, 0, 1),
                width,
                .4 * size,
                .18 * size,
                size,
                .04);
    }
}
