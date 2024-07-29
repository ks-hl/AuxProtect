package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.Activity;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SingleItemEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.InventoryUtil_1_20;
import dev.heliosares.auxprotect.utils.InventoryUtil_1_21;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;

public class InventoryListener implements Listener {

    private final AuxProtectSpigot plugin;

    public InventoryListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpenEvent(InventoryOpenEvent e) {
        plugin.getAPPlayer((Player) e.getPlayer()).addActivity(Activity.OPEN_INVENTORY);
        log(e.getPlayer(), e.getInventory(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryCloseEvent(InventoryCloseEvent e) {
        log(e.getPlayer(), e.getInventory(), false);
    }

    private void log(HumanEntity player, Inventory inv, boolean state) {
        if (inv == null) {
            return;
        }

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
                label = "#" + block.getType().toString().toLowerCase();
            }
        }
        Location location = inv.getLocation();
        if (location == null) {
            location = player.getLocation();
        }
        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(player), EntryAction.INV, state, location, label,
                count + " items in inventory.");
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItemEvent(EnchantItemEvent e) {
        ItemStack item = e.getItem().clone();
        item.addUnsafeEnchantments(e.getEnchantsToAdd());
        plugin.add(new SingleItemEntry(AuxProtectSpigot.getLabel(e.getEnchanter()), EntryAction.ENCHANT, false, e.getEnchanter().getLocation(), item.getType().toString(), "", item));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        plugin.getAPPlayer((Player) e.getWhoClicked()).addActivity(Activity.CLICK_ITEM);

        InventoryType type;

        if (plugin.getCompatabilityVersion() < 21) {
            type = InventoryUtil_1_20.getTopInventory(e.getWhoClicked()).getType();
        } else {
            type = InventoryUtil_1_21.getTopInventory(e.getWhoClicked()).getType();
        }


        if (e.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

        EntryAction action;
        String data = "";
        ItemStack[] entryItems;
        if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
            boolean fits = false;
            for (ItemStack item : e.getWhoClicked().getInventory().getStorageContents()) {
                if (item == null || item.isSimilar(e.getCurrentItem()) && item.getAmount() < item.getMaxStackSize()) {
                    fits = true;
                    break;
                }
            }
            if (!fits) return;
        }

        if (type == InventoryType.ANVIL) {
            action = EntryAction.ANVIL;
            entryItems = new ItemStack[e.getInventory().getSize()];
            for (int i = 0; i < e.getInventory().getSize(); i++) {
                ItemStack item = e.getInventory().getItem(i);
                if (item == null) continue;
                entryItems[i] = item.clone();
            }
        } else if (e instanceof CraftItemEvent craftItemEvent) {
            action = EntryAction.CRAFT;
            final int recipeResultSize = e.getCurrentItem().getAmount();

            entryItems = null;
            int itemsChecked = 0;
            int possibleCreations = Integer.MAX_VALUE;

            if (craftItemEvent.isShiftClick()) {
                for (ItemStack item : craftItemEvent.getInventory().getMatrix()) {
                    if (item != null && item.getType() != Material.AIR) {
                        possibleCreations = Math.min(possibleCreations, item.getAmount());
                        itemsChecked++;
                    }
                }
            }
            if (itemsChecked == 0) possibleCreations = 1;
            possibleCreations *= craftItemEvent.getRecipe().getResult().getAmount();

            int spaceFor = 0;
            int maxStackSize = e.getCurrentItem().getMaxStackSize();
            for (ItemStack item : e.getWhoClicked().getInventory().getStorageContents()) {
                if (item == null) {
                    spaceFor += maxStackSize;
                } else if (item.isSimilar(e.getCurrentItem())) {
                    spaceFor += maxStackSize - item.getAmount();
                }
            }

            int actuallyCrafted = Math.min(spaceFor, possibleCreations);
            actuallyCrafted = (int) Math.ceil(actuallyCrafted / (double) recipeResultSize) * recipeResultSize;

            ItemStack result = e.getCurrentItem().clone();
            if (InvSerialization.isCustom(result)) {
                int numStacks = (int) Math.ceil(actuallyCrafted / (double) maxStackSize);
                if (numStacks == 0) return;
                entryItems = new ItemStack[numStacks];
                for (int i = 0; i < numStacks - 1; i++) {
                    entryItems[i] = e.getCurrentItem().clone();
                    entryItems[i].setAmount(maxStackSize);
                }
                ItemStack last = e.getCurrentItem().clone();
                last.setAmount(actuallyCrafted % maxStackSize);
                entryItems[entryItems.length - 1] = last;
            } else {
                data = "x" + actuallyCrafted;
            }
        } else if (type == InventoryType.SMITHING) {
            action = EntryAction.SMITH;
            entryItems = new ItemStack[]{e.getCurrentItem()};
        } else return;

        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getWhoClicked()), action, false, e.getWhoClicked().getLocation(), e.getCurrentItem().getType().toString().toLowerCase(), data);

        if (entryItems != null) {
            try {
                entry.setBlob(InvSerialization.toByteArray(entryItems));
            } catch (IOException ex) {
                AuxProtectAPI.warning("Failed to serialize item: " + e.getCurrentItem() + " at " + entry.getTime() + "e");
                AuxProtectAPI.getInstance().print(ex);
            }
        }

        plugin.add(entry);
    }

}
