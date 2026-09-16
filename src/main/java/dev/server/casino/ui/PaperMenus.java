package dev.server.casino.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/** Native Paper rendering of the plugin's existing declarative menu fields. */
public final class PaperMenus {
    private static final Pattern INLINE =
            Pattern.compile("<text=\"(.*?)\";actions=([^;>]+);hover=\"(.*?)\">", Pattern.DOTALL);
    private static final String[] COLORS = {
        "black",
        "dark_blue",
        "dark_green",
        "dark_aqua",
        "dark_red",
        "dark_purple",
        "gold",
        "gray",
        "dark_gray",
        "blue",
        "green",
        "aqua",
        "red",
        "light_purple",
        "yellow",
        "white"
    };

    private PaperMenus() {}

    public static void open(
            Plugin plugin,
            Player player,
            YamlConfiguration config,
            BiConsumer<String, Map<String, String>> handler) {
        if (!plugin.getConfig().getBoolean("menu-enabled", true)) {
            player.sendMessage("§e菜单已关闭。使用 /casino create <game>、/casino bet <game> <1-100>、/casino remove <game>。");
            return;
        }
        BiConsumer<String, Map<String, String>> dispatch =
                (action, values) ->
                        Bukkit.getScheduler()
                                .runTask(
                                        plugin,
                                        () -> {
                                            if (plugin.isEnabled() && player.isOnline()
                                                    && plugin.getConfig().getBoolean("menu-enabled", true))
                                                handler.accept(action, values);
                                        });
        List<DialogBody> body = new ArrayList<>();
        ConfigurationSection sections = config.getConfigurationSection("Body");
        if (sections != null)
            for (String key : sections.getKeys(false)) {
                ConfigurationSection section = sections.getConfigurationSection(key);
                if (section == null) continue;
                String value =
                        section.isList("text")
                                ? String.join("\n", section.getStringList("text"))
                                : section.getString("text", "");
                body.add(
                        DialogBody.plainMessage(
                                inline(value, player, config, dispatch),
                                section.getInt("width", 300)));
            }
        List<DialogInput> inputs = new ArrayList<>();
        ConfigurationSection fields = config.getConfigurationSection("Inputs");
        if (fields != null)
            for (String key : fields.getKeys(false)) {
                String path = "Inputs." + key + ".";
                inputs.add(
                        DialogInput.text(
                                key,
                                300,
                                text(config.getString(path + "text", key)),
                                true,
                                config.getString(path + "default", ""),
                                config.getInt(path + "max_length", 6),
                                null));
            }
        List<ActionButton> buttons = new ArrayList<>();
        ConfigurationSection bottom = config.getConfigurationSection("Bottom.buttons");
        if (bottom != null)
            for (String key : bottom.getKeys(false)) {
                buttons.add(button(player, config, "Bottom.buttons." + key, inputs, dispatch));
            }
        ActionButton exit = button(player, config, "Bottom.exit", inputs, dispatch);
        DialogBase base =
                DialogBase.create(
                        text(config.getString("Title", "Casino")),
                        null,
                        true,
                        false,
                        DialogBase.DialogAfterAction.NONE,
                        body,
                        inputs);
        player.showDialog(
                Dialog.create(
                        factory ->
                                factory.empty()
                                        .base(base)
                                        .type(
                                                DialogType.multiAction(
                                                        buttons,
                                                        exit,
                                                        config.getInt("Bottom.columns", 2)))));
    }

    private static ActionButton button(
            Player player,
            YamlConfiguration config,
            String path,
            List<DialogInput> inputs,
            BiConsumer<String, Map<String, String>> dispatch) {
        List<String> actions = config.getStringList(path + ".actions");
        DialogAction action =
                actions.isEmpty()
                        ? null
                        : DialogAction.customClick(
                                (response, audience) -> {
                                    if (!(audience instanceof Player actor)
                                            || !actor.getUniqueId().equals(player.getUniqueId()))
                                        return;
                                    Map<String, String> values = new HashMap<>();
                                    for (DialogInput input : inputs) {
                                        String value = response.getText(input.key());
                                        if (value != null) values.put(input.key(), value);
                                    }
                                    dispatch.accept(actions.getFirst(), Map.copyOf(values));
                                },
                                options());
        return ActionButton.create(
                text(config.getString(path + ".text", "关闭")),
                null,
                config.getInt(path + ".width", 150),
                action);
    }

    private static Component inline(
            String value,
            Player player,
            YamlConfiguration config,
            BiConsumer<String, Map<String, String>> dispatch) {
        var matcher = INLINE.matcher(value);
        Component result = Component.empty();
        int end = 0;
        while (matcher.find()) {
            result = result.append(text(value.substring(end, matcher.start())));
            Component span = text(matcher.group(1)).hoverEvent(Component.text(matcher.group(3)));
            List<String> actions = config.getStringList("Events.Click." + matcher.group(2));
            if (!actions.isEmpty())
                span =
                        span.clickEvent(
                                ClickEvent.callback(
                                        audience -> {
                                            if (audience instanceof Player actor
                                                    && actor.getUniqueId()
                                                            .equals(player.getUniqueId()))
                                                dispatch.accept(actions.getFirst(), Map.of());
                                        },
                                        options()));
            result = result.append(span);
            end = matcher.end();
        }
        return result.append(text(value.substring(end)));
    }

    public static Component text(String value) {
        for (int i = 0; i < COLORS.length; i++)
            value = value.replace("&" + Integer.toHexString(i), "<" + COLORS[i] + ">");
        value =
                value.replace("&l", "<bold>")
                        .replace("&o", "<italic>")
                        .replace("&n", "<underlined>")
                        .replace("&m", "<strikethrough>")
                        .replace("&k", "<obfuscated>")
                        .replace("&r", "<reset>");
        return MiniMessage.miniMessage().deserialize(value);
    }

    private static ClickCallback.Options options() {
        return ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();
    }
}
