package dev.server.casino.machine;

import dev.server.casino.CasinoPlugin;
import dev.server.casino.game.blackjack.BlackjackMachine;
import dev.server.casino.game.crash.CrashMachine;
import dev.server.casino.game.dragon_tower.DragonTowerMachine;
import dev.server.casino.game.duck_race.DuckRaceMachine;
import dev.server.casino.game.hilo.HiloMachine;
import dev.server.casino.game.keno.KenoMachine;
import dev.server.casino.game.mines.MinesMachine;
import dev.server.casino.game.money_wheel.MoneyWheelMachine;
import dev.server.casino.game.penguin_cross.PenguinCrossMachine;
import dev.server.casino.game.plinko.PlinkoMachine;
import dev.server.casino.game.slots.SlotsMachine;
import dev.server.casino.game.wheel_of_fortune.WheelOfFortuneMachine;
import dev.server.casino.model.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.*;

/** Owns the machine registry, event routing and a single tick task for all games. */
public final class MachineManager implements Listener {
    private final CasinoPlugin plugin;
    private final Map<Key, PracticeMachine<?>> machines = new LinkedHashMap<>();
    private final MachineRegistry registry = new MachineRegistry();
    private final BukkitTask task;

    public MachineManager(CasinoPlugin plugin) throws IOException {
        this.plugin = plugin;
        registry.reload(plugin.getDataFolder().toPath().resolve("machines"));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    CasinoPlugin plugin() {
        return plugin;
    }

    public void command(Player player, String[] args) {
        if (!plugin.machineAllowed(player)) {
            player.sendMessage("§c你没有操作测试机的权限。");
            return;
        }
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload-models")) {
                registry.reload(plugin.getDataFolder().toPath().resolve("machines"));
                player.sendMessage("§a模型配置已加载，新创建的机器使用新配置。");
                return;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("bet")) {
                if (args.length != 3 || !args[2].matches("[0-9]{1,3}")) {
                    throw new IllegalArgumentException("用法：/casino bet <game> <1-100>");
                }
                String game = args[1].toLowerCase(Locale.ROOT);
                var machine = machines.get(new Key(player.getUniqueId(), group(game)));
                if (machine == null || !machine.game().equals(game)) {
                    throw new IllegalArgumentException("你没有这种类型的机器。");
                }
                if (!canManage(player, machine)) {
                    throw new IllegalArgumentException("请靠近自己的机器后设置金额。");
                }
                machine.setStake(Long.parseLong(args[2]) * 100);
                player.sendMessage("§a练习下注已设为 " + args[2] + "，下一局生效；不扣款、不发放余额。");
                return;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("remove")) {
                if (args.length > 2) throw new IllegalArgumentException("用法：/casino remove [game]");
                for (var machine : List.copyOf(machines.values())) {
                    if (machine.owner().equals(player.getUniqueId())
                            && (args.length == 1 || machine.game().equalsIgnoreCase(args[1])))
                        remove(machine);
                }
                player.sendMessage("§e已拆除匹配的测试机。");
                return;
            }
            if (args.length < 2 || args.length > 3 || !args[0].equalsIgnoreCase("create")) {
                player.sendMessage(
                        "/casino create <game> [skin-id] | bet <game> <1-100> | remove [game] | reload-models");
                return;
            }
            String game = args[1].toLowerCase(Locale.ROOT);
            var definition = registry.get(game, args.length == 3 ? args[2] : game);
            var key = new Key(player.getUniqueId(), group(game));
            if (machines.containsKey(key)) {
                player.sendMessage("§e请先拆除这一组已有的测试机。");
                return;
            }
            if (machines.keySet().stream().filter(k -> k.group.equals(key.group)).count() >= 8) {
                player.sendMessage("§e测试机数量已达上限。");
                return;
            }
            var origin = player.getLocation();
            float yaw = Math.round(origin.getYaw() / 90f) * 90f;
            origin.setYaw(yaw);
            origin.setPitch(0);
            origin.add(origin.getDirection().multiply(4));
            origin.setX(Math.floor(origin.getX()) + .5);
            origin.setY(Math.floor(origin.getY()));
            origin.setZ(Math.floor(origin.getZ()) + .5);
            origin.setYaw(yaw + 180);
            if (origin.getY() + 6 >= origin.getWorld().getMaxHeight()) {
                player.sendMessage("§c这里太接近建筑高度上限。");
                return;
            }
            var machine = create(game, player.getUniqueId(), origin, definition);
            try {
                machine.build();
                machines.put(key, machine);
            } catch (RuntimeException ex) {
                machine.clear();
                throw ex;
            }
            player.sendMessage("§b免费测试机已生成：右键操作，Shift＋右键设置；离线或20分钟后清理。");
        } catch (IOException | IllegalArgumentException ex) {
            player.sendMessage("§c机器配置未应用：" + ex.getMessage());
        }
    }

    private PracticeMachine<?> create(
            String game, UUID owner, Location origin, MachineDefinition definition) {
        return switch (game) {
            case "mines" -> new MinesMachine(this, owner, origin, definition);
            case "blackjack" -> new BlackjackMachine(this, owner, origin, definition);
            case "crash" -> new CrashMachine(this, owner, origin, definition);
            case "plinko" -> new PlinkoMachine(this, owner, origin, definition);
            case "slots" -> new SlotsMachine(this, owner, origin, definition);
            case "duck_race" -> new DuckRaceMachine(this, owner, origin, definition);
            case "wheel_of_fortune" -> new WheelOfFortuneMachine(this, owner, origin, definition);
            case "money_wheel" -> new MoneyWheelMachine(this, owner, origin, definition);
            case "penguin_cross" -> new PenguinCrossMachine(this, owner, origin, definition);
            case "keno" -> new KenoMachine(this, owner, origin, definition);
            case "hilo" -> new HiloMachine(this, owner, origin, definition);
            case "dragon_tower" -> new DragonTowerMachine(this, owner, origin, definition);
            default -> throw new IllegalArgumentException("Unknown game: " + game);
        };
    }

    private static String group(String game) {
        return switch (game) {
            case "mines", "blackjack", "crash" -> "original";
            case "plinko" -> "plinko";
            default -> "showcase";
        };
    }

    boolean contains(PracticeMachine<?> machine) {
        return machines.get(new Key(machine.owner(), group(machine.game()))) == machine;
    }

    void remove(PracticeMachine<?> machine) {
        if (machines.remove(new Key(machine.owner(), group(machine.game())), machine))
            machine.clear();
    }

    boolean canUse(Player player, PracticeMachine<?> machine) {
        return player.getWorld().equals(machine.origin().getWorld())
                && plugin.allowed(player)
                && (machine.game().equals("plinko")
                        || player.getUniqueId().equals(machine.owner())
                                && plugin.machineAllowed(player));
    }

    boolean canManage(Player player, PracticeMachine<?> machine) {
        return player.getUniqueId().equals(machine.owner())
                && plugin.machineAllowed(player)
                && player.getWorld().equals(machine.origin().getWorld())
                && machine.nearSettings(player);
    }

    double blockDistance(Player player) {
        var block =
                player.getWorld()
                        .rayTraceBlocks(
                                player.getEyeLocation(),
                                player.getEyeLocation().getDirection(),
                                5,
                                FluidCollisionMode.NEVER,
                                true);
        return block == null
                ? 5
                : block.getHitPosition().distance(player.getEyeLocation().toVector()) + .001;
    }

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getPlayer().isSneaking()
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                        && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        click(event.getPlayer(), () -> event.setCancelled(true));
    }

    @EventHandler
    public void interactEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getPlayer().isSneaking()) return;
        click(event.getPlayer(), () -> event.setCancelled(true));
    }

    private void click(Player player, Runnable cancel) {
        double nearest = blockDistance(player);
        PracticeMachine<?> chosen = null;
        PracticeMachine.TargetHit chosenHit = null;
        for (var machine : machines.values()) {
            if (!canUse(player, machine)) continue;
            var hit = machine.ray(player, nearest);
            if (hit != null && hit.distance() <= nearest) {
                nearest = hit.distance();
                chosen = machine;
                chosenHit = hit;
            }
        }
        if (chosen != null) {
            cancel.run();
            chosen.click(chosenHit);
        } else
            for (var machine : machines.values())
                if (canUse(player, machine)) machine.confirmInput();
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        for (var machine : List.copyOf(machines.values()))
            if (machine.owner().equals(event.getPlayer().getUniqueId())) remove(machine);
    }

    @EventHandler
    public void unload(ChunkUnloadEvent event) {
        for (var machine : List.copyOf(machines.values()))
            if (machine.touchesChunk(event.getChunk())) remove(machine);
    }

    private void tick() {
        for (var machine : List.copyOf(machines.values())) {
            if (machine.expired()) {
                remove(machine);
                continue;
            }
            try {
                machine.tick();
            } catch (RuntimeException ex) {
                remove(machine);
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.WARNING,
                                "Machine removed after animation failure: " + machine.game(),
                                ex);
            }
        }
    }

    public void close() {
        task.cancel();
        for (var machine : machines.values()) machine.clear();
        machines.clear();
        HandlerList.unregisterAll(this);
    }

    private record Key(UUID owner, String group) {}
}
