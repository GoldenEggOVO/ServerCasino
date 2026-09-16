package dev.server.casino;

import dev.server.casino.api.EconomyProvider;
import dev.server.casino.economy.EconomyAccess;
import dev.server.casino.integration.AuthMeAccess;
import dev.server.casino.integration.CasinoPermissions;
import dev.server.casino.integration.VaultEconomyProvider;
import dev.server.casino.machine.MachineManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class CasinoPlugin extends JavaPlugin implements Listener {
    MinesService games;
    MinesUi ui;
    public CasinoUi casino;
    EconomyAccess economy;
    public MachineManager machines;
    public MachineSettingsTargets machineSettings;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            EconomyProvider vault = VaultEconomyProvider.discover();
            if (vault != null) {
                getServer()
                        .getServicesManager()
                        .register(EconomyProvider.class, vault, this, ServicePriority.Lowest);
            }
            economy =
                    new EconomyAccess(
                            () -> getServer().getServicesManager().load(EconomyProvider.class));
            games =
                    new MinesService(
                            new RoundStore(getDataFolder().toPath().resolve("rounds")), wallet());
            ui = new MinesUi(this);
            casino = new CasinoUi(this);
            machineSettings = new MachineSettingsTargets(this);
            machines = new MachineManager(this);
            getServer().getPluginManager().registerEvents(this, this);
            getLogger().info("ServerCasino 已启用：实体测试机免费试玩，历史金币对局记录保留。");
        } catch (Exception | LinkageError ex) {
            getLogger().log(Level.SEVERE, "Casino 启动失败", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (machines != null) machines.close();
        if (machineSettings != null) machineSettings.close();
        if (casino != null) casino.close();
        if (ui != null) ui.close();
    }

    void testMachine(Player player, String[] args) {
        machines.command(player, args);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        if (ui != null) ui.forget(event.getPlayer().getUniqueId());
        if (casino != null) casino.forget(event.getPlayer().getUniqueId());
    }

    public boolean allowed(Player player) {
        return player.isOnline()
                && !player.isDead()
                && CasinoPermissions.allowed(player, "use")
                && AuthMeAccess.authenticated(player);
    }

    public boolean machineAllowed(Player player) {
        return allowed(player) && CasinoPermissions.allowed(player, "machine");
    }

    boolean moneyAvailable() {
        return getConfig().getBoolean("money-enabled", false) && economy.available();
    }

    String balanceLabel(Player player) {
        var balance = economy.balance(player.getUniqueId());
        return balance.isPresent() ? CasinoUi.money(balance.getAsLong()) : "经济服务不可用";
    }

    MinesService.Wallet wallet() {
        return new MinesService.Wallet() {
            public boolean available() {
                return economy.available();
            }

            public boolean take(UUID player, long cents) {
                return economy.take(player, cents);
            }

            public boolean give(UUID player, long cents) {
                return economy.give(player, cents);
            }
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("casino-demo")) {
            if (sender instanceof Player player) testMachine(player, args);
            return true;
        }
        boolean mines = args.length > 0 && args[0].equalsIgnoreCase("mines");
        if (mines) args = java.util.Arrays.copyOfRange(args, 1, args.length);
        if (args.length > 0 && args[0].equals("resolve")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage("仅控制台可核对结算。");
                return true;
            }
            try {
                if (args.length != 4 || !Set.of("applied", "not-applied").contains(args[3])) {
                    throw new IllegalArgumentException(
                            (mines ? "casino mines" : "casino") + " resolve <玩家UUID> <对局UUID> <applied|not-applied>");
                }
                if (!mines) {
                    casino.resolve(
                            UUID.fromString(args[1]),
                            UUID.fromString(args[2]),
                            args[3].equals("applied"));
                } else {
                    games.resolve(
                            UUID.fromString(args[1]),
                            UUID.fromString(args[2]),
                            args[3].equals("applied"));
                }
                getLogger()
                        .warning("管理员核对 " + command.getName() + " 结算: " + String.join(" ", args));
                sender.sendMessage("状态已记录；该命令本身不转账。");
            } catch (Exception ex) {
                sender.sendMessage("核对失败: " + ex.getMessage());
            }
            return true;
        }
        if (sender instanceof Player player) {
            if (!allowed(player)) {
                player.sendMessage("§c请先登录，或联系管理员确认权限。");
            } else if (!mines) {
                ui.forget(player.getUniqueId());
                casino.open(player);
            } else {
                casino.forget(player.getUniqueId());
                ui.open(player);
            }
        } else {
            sender.sendMessage("玩家使用 /casino 或 /casino mines；核对命令见 README。");
        }
        return true;
    }
}
