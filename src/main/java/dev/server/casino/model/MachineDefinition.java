package dev.server.casino.model;

import java.util.*;

/** Immutable machine skin. Live machines retain this snapshot during registry reloads. */
public record MachineDefinition(
        String id,
        String game,
        Map<String, String> models,
        Map<String, ModelTransform> anchors,
        Map<String, ButtonDefinition> buttons,
        List<Part> parts,
        List<Double> settingsBounds) {
    public MachineDefinition {
        models = Map.copyOf(models);
        anchors = Map.copyOf(anchors);
        buttons = Map.copyOf(buttons);
        parts = List.copyOf(parts);
        settingsBounds = List.copyOf(settingsBounds);
    }

    public record Part(String model, ModelTransform transform) {}

    public static Set<String> games() {
        return BuiltinLayouts.GAMES;
    }

    public static MachineDefinition builtin(String game) {
        if (!games().contains(game)) throw new IllegalArgumentException("Unknown game: " + game);
        List<Double> bounds;
        if (game.equals("plinko"))
            bounds = List.of(-1.9 / .75, 0.0, -.8 / .75, 1.9 / .75, 3.8 / .75, .8 / .75);
        else if (Set.of("mines", "blackjack", "crash").contains(game)) {
            double depth = game.equals("mines") ? 2 : game.equals("blackjack") ? 1.75 : 1.1;
            bounds = List.of(-1.8, 0.0, -depth, 1.8, 3.4, depth);
        } else bounds = List.of(-1.7, 0.0, game.equals("duck_race") ? -4.85 : -1.2, 1.7, 3.3, 1.6);
        return new MachineDefinition(
                game,
                game,
                Map.of(),
                Map.of("body", ModelTransform.IDENTITY, "playfield", ModelTransform.IDENTITY),
                BuiltinLayouts.buttons(game),
                List.of(),
                bounds);
    }

    public String model(String logicalName) {
        return models.getOrDefault(logicalName, "casino:" + logicalName);
    }

    public ModelTransform anchor(String name) {
        return anchors.getOrDefault(name, ModelTransform.IDENTITY);
    }

    public ButtonDefinition button(String action) {
        var button = buttons.get(action);
        if (button == null) throw new IllegalArgumentException("Unknown button action: " + action);
        return button;
    }

    public static MachineDefinition parse(Map<String, Object> input, MachineDefinition base) {
        keys(
                input,
                Set.of(
                        "schema-version",
                        "id",
                        "game",
                        "models",
                        "anchors",
                        "buttons",
                        "parts",
                        "settings-bounds"),
                "root");
        if (number(input, "schema-version", 0) != 1)
            throw new IllegalArgumentException("schema-version must be 1");
        String id = Objects.toString(input.get("id"), "");
        if (!id.matches("[a-z0-9_-]+"))
            throw new IllegalArgumentException("id must use lowercase letters, numbers, _ or -");
        if (!base.game.equals(input.get("game")))
            throw new IllegalArgumentException("game must match inherited defaults");
        var models = new LinkedHashMap<>(base.models);
        map(input.get("models"), "models")
                .forEach(
                        (key, value) -> {
                            if (!key.matches("[a-z0-9_./-]+"))
                                throw new IllegalArgumentException("Invalid logical model: " + key);
                            String reference = Objects.toString(value, "");
                            validateModel(reference);
                            models.put(key, reference);
                        });
        var anchors = new LinkedHashMap<>(base.anchors);
        map(input.get("anchors"), "anchors")
                .forEach(
                        (key, value) -> {
                            if (!anchors.containsKey(key))
                                throw new IllegalArgumentException("Unknown anchor: " + key);
                            anchors.put(
                                    key, transform(map(value, "anchors." + key), anchors.get(key)));
                        });
        var buttons = new LinkedHashMap<>(base.buttons);
        map(input.get("buttons"), "buttons")
                .forEach(
                        (action, value) -> {
                            var previous = buttons.get(action);
                            if (previous == null)
                                throw new IllegalArgumentException(
                                        "Unknown button action: " + action);
                            var data = map(value, "buttons." + action);
                            keys(
                                    data,
                                    Set.of(
                                            "position",
                                            "rotation",
                                            "width",
                                            "height",
                                            "depth",
                                            "size",
                                            "press"),
                                    "buttons." + action);
                            var poseData = new LinkedHashMap<String, Object>();
                            if (data.containsKey("position"))
                                poseData.put("position", data.get("position"));
                            if (data.containsKey("rotation"))
                                poseData.put("rotation", data.get("rotation"));
                            buttons.put(
                                    action,
                                    new ButtonDefinition(
                                            transform(poseData, previous.transform()),
                                            number(data, "width", previous.width()),
                                            number(data, "height", previous.height()),
                                            number(data, "depth", previous.depth()),
                                            number(data, "size", previous.size()),
                                            number(data, "press", previous.press())));
                        });
        var parts = new ArrayList<>(base.parts);
        if (input.containsKey("parts")) {
            if (!(input.get("parts") instanceof List<?> list))
                throw new IllegalArgumentException("parts must be a list");
            if (list.size() > 64) throw new IllegalArgumentException("parts limited to 64");
            for (Object entry : list) {
                var data = new LinkedHashMap<>(map(entry, "parts"));
                String reference = Objects.toString(data.remove("model"), "");
                validateModel(reference);
                parts.add(new Part(reference, transform(data, ModelTransform.IDENTITY)));
            }
        }
        List<Double> bounds = base.settingsBounds;
        if (input.containsKey("settings-bounds")) {
            bounds = vector(input.get("settings-bounds"), 6, "settings-bounds");
            for (int i = 0; i < 3; i++)
                if (bounds.get(i) >= bounds.get(i + 3))
                    throw new IllegalArgumentException(
                            "settings-bounds minimum must be smaller than maximum");
        }
        return new MachineDefinition(id, base.game, models, anchors, buttons, parts, bounds);
    }

    private static ModelTransform transform(Map<String, Object> data, ModelTransform base) {
        keys(data, Set.of("position", "rotation", "scale"), "transform");
        var position =
                data.containsKey("position")
                        ? vector(data.get("position"), 3, "position")
                        : List.of(base.x(), base.y(), base.z());
        var rotation =
                data.containsKey("rotation")
                        ? vector(data.get("rotation"), 3, "rotation")
                        : List.of(base.pitch(), base.yaw(), base.roll());
        return new ModelTransform(
                position.get(0),
                position.get(1),
                position.get(2),
                rotation.get(0),
                rotation.get(1),
                rotation.get(2),
                number(data, "scale", base.scale()));
    }

    private static void validateModel(String value) {
        if (value.startsWith("material:")) {
            if (!value.matches("material:[A-Z0-9_]+"))
                throw new IllegalArgumentException("Material names must be uppercase: " + value);
            var material = org.bukkit.Material.getMaterial(value.substring(9));
            if (material == null || material.isAir() || !material.isItem())
                throw new IllegalArgumentException("Material must be an item: " + value);
        } else if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid model reference: " + value);
        }
    }

    private static List<Double> vector(Object value, int length, String field) {
        if (!(value instanceof List<?> list) || list.size() != length)
            throw new IllegalArgumentException(field + " requires " + length + " numbers");
        var result = new ArrayList<Double>();
        for (Object entry : list) {
            if (!(entry instanceof Number n)
                    || !Double.isFinite(n.doubleValue())
                    || Math.abs(n.doubleValue()) > 360)
                throw new IllegalArgumentException(field + " contains invalid number");
            result.add(n.doubleValue());
        }
        return List.copyOf(result);
    }

    private static double number(Map<String, Object> input, String field, double fallback) {
        if (!input.containsKey(field)) return fallback;
        if (!(input.get(field) instanceof Number n) || !Double.isFinite(n.doubleValue()))
            throw new IllegalArgumentException(field + " must be finite number");
        return n.doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String field) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)
                || map.keySet().stream().anyMatch(k -> !(k instanceof String)))
            throw new IllegalArgumentException(field + " must be a mapping");
        return (Map<String, Object>) map;
    }

    private static void keys(Map<String, Object> data, Set<String> allowed, String field) {
        for (String key : data.keySet())
            if (!allowed.contains(key))
                throw new IllegalArgumentException("Unknown field " + field + "." + key);
    }
}
