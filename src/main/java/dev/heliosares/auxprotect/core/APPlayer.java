package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.PosEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.PosEncoder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class APPlayer {
    public final Player player;
    public final double[] activity = new double[30];
    private final IAuxProtect plugin;
    private final List<Byte> posBlob = new ArrayList<>();
    public long lastLoggedMoney;
    public long lastLoggedInventory;
    public long lastLoggedInventoryDiff;
    public long lastLoggedPos;
    public long lastMoved;
    public long lastLoggedActivity;
    public Location lastLocation;
    public long lastCheckedMovement;
    public double movedAmountThisMinute;
    public boolean hasMovedThisMinute;
    public int activityIndex;
    public long lastNotifyInactive;
    // hotbar, main, armor, offhand, echest
    private List<ItemStack> invDiffItems;
    private Location lastLocationDiff;
    private PosEncoder.Posture lastPosture;

    public APPlayer(IAuxProtect plugin, Player player) {
        this.player = player;
        this.plugin = plugin;

        Arrays.fill(activity, -1);
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

    public long logInventory(String reason, Location loc, byte[] inventory) {
        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(player), EntryAction.INVENTORY, false, loc, reason, "");
        entry.setBlob(inventory);
        plugin.add(entry);

        lastLoggedInventory = System.currentTimeMillis();
        return entry.getTime();
    }

    public synchronized void tickDiffInventory() {
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
            } catch (Exception e) {
                plugin.print(e);
                return;
            }
            invDiffItems.set(i, newItem);
        }
    }

    public void tickDiffPos() {
        if (lastLocationDiff != null) {
            synchronized (posBlob) {
                PosEncoder.Posture posture = PosEncoder.Posture.fromPlayer(player);
                for (byte b : PosEncoder.encode(lastLocationDiff, player.getLocation(), posture, lastPosture)) {
                    posBlob.add(b);
                }
                lastPosture = posture;
            }
        }
        lastLocationDiff = player.getLocation().clone();
    }

    public void logPos(Location location) {
        logPos(location, false);
    }

    public void logPreTeleportPos(Location location) {
        logPos(location, true);
    }

    public void logPostTeleportPos(Location location) {
        plugin.add(new PosEntry(AuxProtectSpigot.getLabel(player), EntryAction.TP, true, location, ""));
        lastLocationDiff = null; // Set to null to force a one-tick pause before checking again
    }

    private void logPos(Location location, boolean tp) {
        lastLoggedPos = System.currentTimeMillis();
        DbEntry entry = new PosEntry("$" + player.getUniqueId(), tp ? EntryAction.TP : EntryAction.POS, false, location, "");

        if (!tp) lastLocationDiff = player.getLocation().clone();

        synchronized (posBlob) {
            byte[] blob = new byte[posBlob.size()];
            for (int i = 0; i < blob.length; i++) blob[i] = posBlob.get(i);
            entry.setBlob(blob);
            posBlob.clear();
        }

        plugin.add(entry);

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
}
