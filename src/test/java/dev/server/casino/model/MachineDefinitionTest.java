package dev.server.casino.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Map;

class MachineDefinitionTest {
    @Test
    void customSkinInheritsGameAndChangesOnlySpecifiedFields() {
        var defaults = MachineDefinition.builtin("mines");
        var skin =
                MachineDefinition.parse(
                        Map.of(
                                "schema-version",
                                1,
                                "id",
                                "blue_mines",
                                "game",
                                "mines",
                                "models",
                                Map.of("cabinet_mines", "example:blue")),
                        defaults);
        assertEquals("blue_mines", skin.id());
        assertEquals("example:blue", skin.model("cabinet_mines"));
        assertEquals(defaults.button("start"), skin.button("start"));
        assertThrows(UnsupportedOperationException.class, () -> skin.models().put("x", "y"));
    }

    @Test
    void rejectsUnknownActionsAndNonFiniteTransforms() {
        var defaults = MachineDefinition.builtin("mines");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MachineDefinition.parse(
                                Map.of(
                                        "schema-version",
                                        1,
                                        "id",
                                        "bad",
                                        "game",
                                        "mines",
                                        "buttons",
                                        Map.of("withdraw", Map.of("width", 1))),
                                defaults));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelTransform(0, 0, 0, Double.NaN, 0, 0, 1));
        assertThrows(
                IllegalArgumentException.class, () -> new ModelTransform(0, 0, 0, 0, 0, 0, -1));
        assertThrows(
                IllegalArgumentException.class, () -> new ModelTransform(0, 0, 0, 0, 0, 0, 1e-300));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MachineDefinition.parse(
                                Map.of(
                                        "schema-version",
                                        1,
                                        "id",
                                        "bad",
                                        "game",
                                        "mines",
                                        "models",
                                        Map.of("cabinet_mines", "material:stone")),
                                defaults));
    }

    @Test
    void buttonPressFollowsSurfaceNormalAndRotatedRayHitsTheSameSurface() {
        var transform = new ModelTransform(1, 2, 3, -35, 40, 15, 1);
        var point = transform.apply(0, 0, -.04);
        var local = transform.inverse(point.x(), point.y(), point.z());
        assertEquals(0, local.x(), 1e-6);
        assertEquals(0, local.y(), 1e-6);
        assertEquals(-.04, local.z(), 1e-6);
    }

    @Test
    void retainsOriginalPressDepthAndSettingsBounds() {
        for (String game : java.util.List.of("mines", "blackjack", "crash", "plinko")) {
            var definition = MachineDefinition.builtin(game);
            double scale =
                    game.equals("mines")
                            ? 2.0 / 3.5
                            : game.equals("crash") ? .8 : game.equals("plinko") ? .75 : 1;
            var button = definition.button(game.equals("plinko") ? "play" : "start");
            assertEquals(.055, button.press() * scale, 1e-9);
        }
        assertEquals(
                java.util.List.of(-1.8, 0.0, -1.75, 1.8, 3.4, 1.75),
                MachineDefinition.builtin("blackjack").settingsBounds());
    }
}
