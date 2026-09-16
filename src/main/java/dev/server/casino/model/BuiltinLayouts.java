package dev.server.casino.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The approved cabinet defaults. Custom files override only the fields they contain. */
final class BuiltinLayouts {
    static final Set<String> GAMES =
            Set.of(
                    "mines",
                    "blackjack",
                    "crash",
                    "plinko",
                    "slots",
                    "duck_race",
                    "wheel_of_fortune",
                    "money_wheel",
                    "penguin_cross",
                    "keno",
                    "hilo",
                    "dragon_tower");

    static Map<String, ButtonDefinition> buttons(String game) {
        var buttons = new LinkedHashMap<String, ButtonDefinition>();
        switch (game) {
            case "mines" -> {
                put(buttons, "minus", -.79, 1.18, 1.57, .30, -35, .45);
                put(buttons, "plus", -.31, 1.18, 1.57, .30, -35, .45);
                put(buttons, "start", -.55, .72, 1.80, .92, -35, 1);
                put(buttons, "cash", .55, .72, 1.80, .92, -35, 1);
            }
            case "blackjack" -> {
                String[] actions = {"double", "stand", "hit", "start"};
                for (int i = 0; i < actions.length; i++)
                    put(buttons, actions[i], (i - 1.5) * .5, .55, 1.45, .46, -35, 3.1 / 4);
            }
            case "crash" -> {
                put(buttons, "start", -.65, 1, .86, 1.05, -20, 1);
                put(buttons, "cash", .65, 1, .86, 1.05, -20, 1);
            }
            case "plinko" -> {
                // Model geometry is scaled independently of cabinet coordinates in this original
                // machine.
                put(buttons, "play", 0, .30, .79, .90 / .75, -25, .65 / .75);
            }
            case "slots" -> put(buttons, "play", 0, .84, .76, 1.5, -35, .75);
            case "duck_race" -> {
                for (int i = 0; i < 4; i++)
                    put(buttons, "select:" + i, -.9 + i * .6, .78, 1.18, .5, -35, .8);
                put(buttons, "play", 1.43, 1.05, .9, .65, -35, 1.2);
            }
            case "wheel_of_fortune", "money_wheel" -> {
                put(buttons, "play", 0, 1.86, .36, .42, 0, .7);
                if (game.equals("money_wheel")) {
                    for (int i = 0; i < 4; i++)
                        put(buttons, "select:" + i, (i - 1.5) * .58, .65, 1.22, .5, -35, 1);
                }
            }
            case "hilo" -> {
                hilo(buttons, "under", -1, -.53);
                hilo(buttons, "over", 1, -.53);
                hilo(buttons, "play", 0, .20);
            }
            case "keno" -> put(buttons, "play", 0, .43, 1.22, .55, -35, .8);
            case "penguin_cross" -> {
                put(buttons, "play", -.58, .65, 1.22, .46, -35, 1);
                put(buttons, "step", 0, .65, 1.22, .46, -35, 1);
                put(buttons, "cash", .58, .65, 1.22, .46, -35, 1);
            }
            case "dragon_tower" -> {
                put(buttons, "play", -.29, .65, 1.22, .46, -35, 1);
                put(buttons, "cash", .29, .65, 1.22, .46, -35, 1);
            }
            default -> throw new IllegalArgumentException("Unknown game: " + game);
        }
        if (Set.of("mines", "blackjack", "crash", "plinko").contains(game)) {
            double cabinetScale =
                    game.equals("mines")
                            ? 2.0 / 3.5
                            : game.equals("crash") ? .8 : game.equals("plinko") ? .75 : 1;
            buttons.replaceAll(
                    (action, button) ->
                            new ButtonDefinition(
                                    button.transform(),
                                    button.width(),
                                    button.height(),
                                    button.depth(),
                                    button.size(),
                                    .055 / cabinetScale));
        }
        return Map.copyOf(buttons);
    }

    private static void hilo(
            Map<String, ButtonDefinition> buttons, String action, double x, double y) {
        var point = new ModelTransform(0, 1.1, 0, -35, 0, 0, 1).apply(x, y, .055);
        put(buttons, action, point.x(), point.y(), point.z(), .55, -35, .65);
    }

    private static void put(
            Map<String, ButtonDefinition> buttons,
            String action,
            double x,
            double y,
            double z,
            double width,
            double pitch,
            double size) {
        buttons.put(action, ButtonDefinition.at(x, y, z, width, pitch, size));
    }

    private BuiltinLayouts() {}
}
