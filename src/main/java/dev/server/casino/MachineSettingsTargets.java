package dev.server.casino;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Consumer;

/** Sneaking ray targets for display-only cabinets; never adds a hitbox over game buttons. */
public final class MachineSettingsTargets implements Listener {
    private record Target(UUID owner, Location origin, BoundingBox bounds, Consumer<Player> open) {}

    private final CasinoPlugin plugin;
    private final Map<Object, Target> targets = new HashMap<>();

    public MachineSettingsTargets(CasinoPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void register(
            Object machine,
            UUID owner,
            Location origin,
            BoundingBox bounds,
            Consumer<Player> open) {
        targets.put(machine, new Target(owner, origin.clone(), bounds.clone(), open));
    }

    public void unregister(Object machine) {
        targets.remove(machine);
    }

    public static double distance(
            Location origin, BoundingBox bounds, Location eye, Vector direction) {
        var start =
                MachineGeometry.rotate(
                        eye.getX() - origin.getX(),
                        eye.getY() - origin.getY(),
                        eye.getZ() - origin.getZ(),
                        -origin.getYaw());
        var ray =
                MachineGeometry.rotate(
                        direction.getX(), direction.getY(), direction.getZ(), -origin.getYaw());
        var hit =
                bounds.rayTrace(
                        new Vector(start.x(), start.y(), start.z()),
                        new Vector(ray.x(), ray.y(), ray.z()),
                        5);
        return hit == null
                ? Double.POSITIVE_INFINITY
                : hit.getHitPosition().distance(new Vector(start.x(), start.y(), start.z()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND
                || !p.isSneaking()
                || !plugin.allowed(p)
                || !plugin.machineAllowed(p)
                || event.getAction() != Action.RIGHT_CLICK_AIR
                        && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var eye = p.getEyeLocation();
        var direction = eye.getDirection();
        double nearest = 5.001;
        Target chosen = null;
        var block =
                p.getWorld()
                        .rayTraceBlocks(
                                eye, direction, 5, org.bukkit.FluidCollisionMode.NEVER, true);
        if (block != null) nearest = block.getHitPosition().distance(eye.toVector()) + .01;
        for (var target : targets.values()) {
            if (!target.owner.equals(p.getUniqueId())
                    || !target.origin.getWorld().equals(p.getWorld())) continue;
            double distance = distance(target.origin, target.bounds, eye, direction);
            if (distance < nearest) {
                nearest = distance;
                chosen = target;
            }
        }
        if (chosen != null) {
            event.setCancelled(true);
            chosen.open.accept(p);
        }
    }

    public void close() {
        targets.clear();
        HandlerList.unregisterAll(this);
    }
}
