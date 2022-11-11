package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.InvSerialization;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class APPlayer {
    public final Player player;
    private final IAuxProtect plugin;
    public long lastLoggedMoney;
    public long lastLoggedInventory;
    public long lastLoggedInventoryDiff;
    public long lastLoggedPos;
    public long lastMoved;
    public long lastLoggedActivity;
    public Location lastLocation;
    public Location lastLocationDiff;
    public long lastCheckedMovement;
    public double movedAmountThisMinute;
    public boolean hasMovedThisMinute;
    public double[] activity = new double[30];
    public int activityIndex;
    public long lastNotifyInactive;
    // hotbar, main, armor, offhand, echest
    private List<ItemStack> invDiffItems;

    public APPlayer(IAuxProtect plugin, Player player) {
        this.player = player;
        this.plugin = plugin;

        for (int i = 0; i < activity.length; i++) {
            activity[i] = -1;
        }
    }

    public void addActivity(double d) {
        activity[activityIndex] += d;
    }

    public long logInventory(String reason) {
        if (!reason.equals("quit")) {
            invDiffItems = getInventory();
        }
        try {
            return logInventory(reason, player.getLocation(), InvSerialization.playerToByteArray(player));
        } catch (Exception e) {
            plugin.print(e);
        }
        return -1;
    }

    private List<ItemStack> getInventory() {
        List<ItemStack> contents = new ArrayList<>();
        ItemStack[] array = player.getInventory().getStorageContents();
        for (int i = 9; i < array.length; i++) {
            ItemStack item = array[i];
            contents.add(item == null ? null : item.clone());
        }
        for (int i = 0; i < 9; i++) {
            ItemStack item = array[i];
            contents.add(item == null ? null : item.clone());
        }
        array = player.getInventory().getArmorContents();
        for (int i = array.length - 1; i >= 0; i--) {
            ItemStack item = array[i];
            contents.add(item == null ? null : item.clone());
        }
        for (ItemStack item : player.getInventory().getExtraContents()) {
            contents.add(item == null ? null : item.clone());
        }
        for (ItemStack item : player.getEnderChest().getContents()) {
            contents.add(item == null ? null : item.clone());
        }
        return contents;
    }

    public long logInventory(String reason, Location loc, byte[] inventory) {
        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(player), EntryAction.INVENTORY, false, loc, reason, "");
        entry.setBlob(inventory);
        plugin.add(entry);

        lastLoggedInventory = System.currentTimeMillis();
        return entry.getTime();
    }

    public synchronized void diff() {
        lastLoggedInventoryDiff = System.currentTimeMillis();
        if (invDiffItems == null) {
            logInventory("diff");
            return;
        }
        List<ItemStack> current = getInventory();
        for (int i = 0; i < current.size(); i++) {
            ItemStack newItem = current.get(i);
            ItemStack oldItem = invDiffItems.get(i);
            if (newItem == null && oldItem == null) {
                continue;
            }
            boolean similar;
            boolean sameqty;
            if (newItem == null || oldItem == null) {
                similar = false;
                sameqty = false;
            } else {
                similar = newItem.isSimilar(oldItem);
                sameqty = newItem.getAmount() == oldItem.getAmount();
            }
            if (similar && sameqty) {
                continue;
            }
            ItemStack item = null;
            int qty = -1;

            if (newItem == null) {
                qty = 0;
                item = null;
            } else {
                if (!sameqty) {
                    qty = newItem.getAmount();
                }
                if (!similar) {
                    item = newItem.clone();
                    item.setAmount(1);
                }
            }
            try {
                plugin.getSqlManager().getInvDiffManager().logInvDiff(player.getUniqueId(), i, qty, item);
                plugin.debug("Found diff for " + player.getName() + ": slot=" + i + " qty=" + qty + " item="
                        + (item == null ? null : item.getType().toString().toLowerCase()), 5);
            } catch (Exception e) {
                plugin.print(e);
                return;
            }
            invDiffItems.set(i, newItem);
        }
    }
}
