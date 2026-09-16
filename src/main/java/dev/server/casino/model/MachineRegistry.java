package dev.server.casino.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Reload commits only after every file has passed validation. */
public final class MachineRegistry {
    private Map<String, MachineDefinition> definitions = defaults();

    public MachineDefinition get(String game, String id) {
        var definition = definitions.get(id);
        if (definition == null || !definition.game().equals(game))
            throw new IllegalArgumentException("Unknown machine skin " + id + " for " + game);
        return definition;
    }

    public Set<String> ids() {
        return definitions.keySet();
    }

    public void reload(Path directory) throws IOException {
        Files.createDirectories(directory);
        var candidate = new LinkedHashMap<>(defaults());
        try (var stream = Files.list(directory)) {
            for (Path file :
                    stream.filter(p -> p.getFileName().toString().endsWith(".yml"))
                            .sorted()
                            .toList()) {
                try {
                    var yaml = new YamlConfiguration();
                    yaml.load(file.toFile());
                    String game = yaml.getString("game", "");
                    var definition =
                            MachineDefinition.parse(values(yaml), MachineDefinition.builtin(game));
                    if (candidate.putIfAbsent(definition.id(), definition) != null)
                        throw new IllegalArgumentException(
                                "Duplicate/reserved machine id: " + definition.id());
                } catch (InvalidConfigurationException | IllegalArgumentException ex) {
                    throw new IOException(file.getFileName() + ": " + ex.getMessage(), ex);
                }
            }
        }
        definitions = Map.copyOf(candidate);
    }

    private static Map<String, MachineDefinition> defaults() {
        var result = new LinkedHashMap<String, MachineDefinition>();
        MachineDefinition.games().stream()
                .sorted()
                .forEach(game -> result.put(game, MachineDefinition.builtin(game)));
        return Map.copyOf(result);
    }

    private static Map<String, Object> values(ConfigurationSection section) {
        var result = new LinkedHashMap<String, Object>();
        section.getValues(false)
                .forEach(
                        (key, value) ->
                                result.put(
                                        key,
                                        value instanceof ConfigurationSection nested
                                                ? values(nested)
                                                : value));
        return result;
    }
}
