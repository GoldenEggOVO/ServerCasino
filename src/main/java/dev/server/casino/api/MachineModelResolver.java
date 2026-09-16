package dev.server.casino.api;

import org.bukkit.inventory.ItemStack;

/**
 * Register via Bukkit ServicesManager. Return null to fall back to vanilla item_model resolution.
 */
public interface MachineModelResolver {
    ItemStack resolve(String namespacedModel);
}
