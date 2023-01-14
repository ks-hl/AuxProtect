package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryListener implements Listener {

    private final AuxProtectSpigot plugin;

    public InventoryListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpenEvent(InventoryOpenEvent e) {
        log(e.getPlayer(), e.getInventory(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryCloseEvent(InventoryCloseEvent e) {
        log(e.getPlayer(), e.getInventory(), false);
    }

    private void log(HumanEntity player, Inventory inv, boolean state) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item == null)
                continue;
            count += item.getAmount();
        }
        String label = AuxProtectSpigot.getLabel(inv.getHolder());
        if (label.equals("#null")) {
            if (inv.getLocation() != null) {
                Block block = inv.getLocation().getBlock();
                if (block != null) {
                    label = "#" + block.getType().toString().toLowerCase();
                }
            }
        }
        Location location = inv.getLocation();
        if (location == null) {
            location = player.getLocation();
        }
        if (location == null) {
            return;
        }
        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(player), EntryAction.INV, state, location, label,
                count + " items in inventory.");
        plugin.add(entry);
    }
}
