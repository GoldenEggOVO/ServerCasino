package dev.server.casino.model;

import dev.server.casino.api.MachineModelResolver;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public final class ModelItems {
    public static ItemStack resolve(String reference) {
        var provider = Bukkit.getServicesManager().load(MachineModelResolver.class);
        if (provider != null) {
            var result = provider.resolve(reference);
            if (result != null) return result.clone();
        }
        if (reference.startsWith("material:"))
            return new ItemStack(Material.valueOf(reference.substring(9)));
        var item = new ItemStack(Material.PAPER);
        var meta = item.getItemMeta();
        var key = NamespacedKey.fromString(reference);
        if (key == null)
            throw new IllegalArgumentException("Invalid model reference: " + reference);
        meta.setItemModel(key);
        item.setItemMeta(meta);
        return item;
    }

    private ModelItems() {}
}
