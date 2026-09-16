package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import dev.server.casino.ui.PaperMenus;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PaperMenusTest {
    @Test
    void disabledMenuDoesNotBuildOrShowDialog() {
        var config = new YamlConfiguration();
        config.set("menu-enabled", false);
        var messages = new AtomicInteger();
        Plugin plugin = (Plugin) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Plugin.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getConfig")) return config;
                    throw new AssertionError(method.getName());
                });
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage")) {
                        messages.incrementAndGet();
                        return null;
                    }
                    throw new AssertionError(method.getName());
                });
        PaperMenus.open(plugin, player, new YamlConfiguration(), (action, values) -> fail());
        assertEquals(1, messages.get());
    }
}
