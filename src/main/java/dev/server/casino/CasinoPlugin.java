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
    private CasinoRuntime runtime;
    private CasinoMenus menus;
    private EconomyAccess economy;
    private MachineManager machines;
    private MachineSettingsTargets machineSettings;

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
            runtime = new CasinoRuntime(this, economy);
            if (menusEnabled()) {
                menus = new CasinoMenus(this, runtime);
                runtime.onRoundUpdated(menus::roundUpdated);
            }
            machineSettings = new MachineSettingsTargets(this);
            machines = new MachineManager(this);
            getServer().getPluginManager().registerEvents(this, this);
            getLogger().info("ServerCasino 已启用：实体机器免费练习，历史金币对局记录保留。");
        } catch (Exception | LinkageError ex) {
            getLogger().log(Level.SEVERE, "Casino 启动失败", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (machines != null) machines.close();
        if (machineSettings != null) machineSettings.close();
        if (menus != null) menus.close();
        if (runtime != null) runtime.close();
    }

    void machineCommand(Player player, String[] args) {
        machines.command(player, args);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        if (menus != null) menus.forget(event.getPlayer().getUniqueId());
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

    public boolean menusEnabled() {
        return getConfig().getBoolean("menu-enabled", true);
    }

    public MachineSettingsTargets machineSettings() {
        return machineSettings;
    }

    public void openMachineSettings(
            Player player,
            String game,
            java.util.function.LongSupplier stake,
            java.util.function.LongConsumer setStake,
            java.util.function.BooleanSupplier canEdit,
            java.util.function.BooleanSupplier exists,
            Runnable remove) {
        if (menus == null || !menusEnabled()) {
            menuHelp(player);
            return;
        }
        menus.machineSettings(player, game, stake, setStake, canEdit, exists, remove);
    }

    private void menuHelp(Player player) {
        player.sendMessage(
                "§e菜单已关闭。使用 /casino create <game>、/casino bet <game> <1-100>、/casino remove"
                    + " <game>。");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("casino-demo")
                || args.length > 0
                        && Set.of("create", "remove", "bet", "reload-models")
                                .contains(args[0].toLowerCase(java.util.Locale.ROOT))) {
            if (sender instanceof Player player) machineCommand(player, args);
            else sender.sendMessage("机器管理指令需要玩家在游戏内执行。");
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
                            (mines ? "casino mines" : "casino")
                                    + " resolve <玩家UUID> <对局UUID> <applied|not-applied>");
                }
                runtime.resolve(
                        mines,
                        UUID.fromString(args[1]),
                        UUID.fromString(args[2]),
                        args[3].equals("applied"));
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
            } else if (mines) {
                player.sendMessage("§eMines 请使用实体机器游玩。");
            } else {
                if (menus != null && menusEnabled()) menus.open(player);
                else menuHelp(player);
            }
        } else {
            sender.sendMessage("玩家使用 /casino；核对命令见 README。");
        }
        return true;
    }
}
