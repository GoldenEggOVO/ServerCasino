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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.*;

/** Owns the machine registry, event routing and a single tick task for all games. */
public final class MachineManager implements Listener {
    private final CasinoPlugin plugin;
    private final Map<Key, PracticeMachine<?>> machines = new LinkedHashMap<>();
    private final Map<Key, PlacementStore.Placement> placements = new LinkedHashMap<>();
    private final PlacementStore store;
    private boolean restoreScheduled;
    private boolean closed;
    private final MachineRegistry registry = new MachineRegistry();
    private final BukkitTask task;

    public MachineManager(CasinoPlugin plugin) throws IOException {
        this.plugin = plugin;
        registry.reload(plugin.getDataFolder().toPath().resolve("machines"));
        store = new PlacementStore(plugin.getDataFolder().toPath().resolve("placements.json"));
        for (var placement : store.load()) {
            placements.put(new Key(placement.owner(), placement.definition().game()), placement);
        }
        scheduleRestore();
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
                var machine = machines.get(new Key(player.getUniqueId(), game));
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
                var candidate = new LinkedHashMap<>(placements);
                candidate.keySet().removeIf(key -> key.owner.equals(player.getUniqueId())
                        && (args.length == 1 || key.game.equalsIgnoreCase(args[1])));
                save(candidate);
                for (var machine : List.copyOf(machines.values())) {
                    if (!placements.containsKey(new Key(machine.owner(), machine.game()))) detach(machine);
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
            var key = new Key(player.getUniqueId(), game);
            if (placements.containsKey(key)) {
                player.sendMessage("§e你已放置这种游戏的机器，请先删除再重新放置。");
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
                var candidate = new LinkedHashMap<>(placements);
                candidate.put(key, new PlacementStore.Placement(player.getUniqueId(), origin.getWorld().getUID(),
                        origin.getX(), origin.getY(), origin.getZ(), origin.getYaw(), definition, 1000));
                store.save(candidate.values());
                placements.putAll(candidate);
                machines.put(key, machine);
            } catch (IOException | RuntimeException ex) {
                machine.clear();
                throw ex;
            }
            player.sendMessage("§b机器已永久保存：右键操作，Shift＋右键设置；可同时放置不同游戏。");
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

    boolean contains(PracticeMachine<?> machine) {
        return machines.get(new Key(machine.owner(), machine.game())) == machine;
    }

    private void save(Map<Key, PlacementStore.Placement> candidate) {
        try {
            store.save(candidate.values());
        } catch (IOException ex) {
            throw new IllegalArgumentException("机器保存失败，原记录未修改。", ex);
        }
        placements.clear();
        placements.putAll(candidate);
    }

    void saveStake(PracticeMachine<?> machine, long amount) {
        if (!contains(machine)) throw new IllegalArgumentException("机器已失效。");
        var key = new Key(machine.owner(), machine.game());
        var candidate = new LinkedHashMap<>(placements);
        candidate.put(key, candidate.get(key).withStake(amount));
        save(candidate);
    }

    void remove(PracticeMachine<?> machine) {
        if (!contains(machine)) return;
        var candidate = new LinkedHashMap<>(placements);
        candidate.remove(new Key(machine.owner(), machine.game()));
        save(candidate);
        detach(machine);
    }

    private void detach(PracticeMachine<?> machine) {
        machines.remove(new Key(machine.owner(), machine.game()), machine);
        machine.clear();
    }

    private void scheduleRestore() {
        if (closed || restoreScheduled) return;
        restoreScheduled = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            restoreScheduled = false;
            if (!closed) restoreLoaded();
        });
    }

    private void restoreLoaded() {
        for (var entry : placements.entrySet()) {
            if (machines.containsKey(entry.getKey())) continue;
            var saved = entry.getValue();
            World world = plugin.getServer().getWorld(saved.world());
            if (world == null || !world.isChunkLoaded(((int) Math.floor(saved.x())) >> 4,
                    ((int) Math.floor(saved.z())) >> 4)) continue;
            var origin = new Location(world, saved.x(), saved.y(), saved.z(), saved.yaw(), 0);
            var machine = create(saved.definition().game(), saved.owner(), origin, saved.definition());
            try {
                machine.restoreStake(saved.stake());
                machine.build();
                machines.put(entry.getKey(), machine);
            } catch (RuntimeException ex) {
                machine.clear();
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Machine restore failed; placement retained: " + entry.getKey(), ex);
            }
        }
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unload(ChunkUnloadEvent event) {
        for (var machine : List.copyOf(machines.values()))
            if (machine.touchesChunk(event.getChunk())) detach(machine);
    }

    @EventHandler
    public void load(ChunkLoadEvent event) {
        scheduleRestore();
    }

    @EventHandler
    public void loadWorld(WorldLoadEvent event) {
        scheduleRestore();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unloadWorld(WorldUnloadEvent event) {
        for (var machine : List.copyOf(machines.values()))
            if (machine.origin().getWorld().equals(event.getWorld())) detach(machine);
    }

    private void tick() {
        for (var machine : List.copyOf(machines.values())) {
            if (machine.expired()) {
                detach(machine);
                continue;
            }
            try {
                machine.tick();
            } catch (RuntimeException ex) {
                detach(machine);
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.WARNING,
                                "Machine display stopped after animation failure; placement retained: " + machine.game(),
                                ex);
            }
        }
    }

    public void close() {
        closed = true;
        task.cancel();
        for (var machine : machines.values()) machine.clear();
        machines.clear();
        HandlerList.unregisterAll(this);
    }

    private record Key(UUID owner, String game) {}
}
