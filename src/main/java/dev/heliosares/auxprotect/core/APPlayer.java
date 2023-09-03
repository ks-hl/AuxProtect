package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.PosEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.IPService;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.PosEncoder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

public class APPlayer {
    public final double[] activity = new double[30];
    private final Player player;
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

    private TimeZone timeZone;

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
            return logInventory(reason, getPlayer().getLocation(), InvSerialization.playerToByteArray(getPlayer()));
        } catch (Exception e) {
            plugin.print(e);
        }
        return -1;
    }

    public long logInventory(String reason, Location loc, byte[] inventory) {
        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(getPlayer()), EntryAction.INVENTORY, false, loc, reason, "");
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
                plugin.getSqlManager().getInvDiffManager().logInvDiff(getPlayer().getUniqueId(), i, qty, item);
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
                PosEncoder.Posture posture = PosEncoder.Posture.fromPlayer(getPlayer());
                for (byte b : PosEncoder.encode(lastLocationDiff, getPlayer().getLocation(), posture, lastPosture)) {
                    posBlob.add(b);
                }
                lastPosture = posture;
            }
        }
        lastLocationDiff = getPlayer().getLocation().clone();
    }

    public void logPos(Location location) {
        logPos(location, false);
    }

    public void logPreTeleportPos(Location location) {
        logPos(location, true);
    }

    public void logPostTeleportPos(Location location) {
        plugin.add(new PosEntry(AuxProtectSpigot.getLabel(getPlayer()), EntryAction.TP, true, location, ""));
        lastLocationDiff = null; // Set to null to force a one-tick pause before checking again
    }

    private void logPos(Location location, boolean tp) {
        lastLoggedPos = System.currentTimeMillis();
        DbEntry entry = new PosEntry("$" + getPlayer().getUniqueId(), tp ? EntryAction.TP : EntryAction.POS, false, location, "");

        if (!tp) lastLocationDiff = getPlayer().getLocation().clone();

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
        ItemStack[] array = getPlayer().getInventory().getStorageContents();
        for (int i = 9; i < array.length; i++) {
            ItemStack item = array[i];
            contents.add(item == null ? null : item.clone());
        }
        for (int i = 0; i < 9; i++) {
            ItemStack item = array[i];
            contents.add(item == null ? null : item.clone());
        }
        array = getPlayer().getInventory().getArmorContents();
        for (int i = array.length - 1; i >= 0; i--) {
            ItemStack item = array[i];
            contents.add(item == null ? null : item.clone());
        }
        for (ItemStack item : getPlayer().getInventory().getExtraContents()) {
            contents.add(item == null ? null : item.clone());
        }
        for (ItemStack item : getPlayer().getEnderChest().getContents()) {
            contents.add(item == null ? null : item.clone());
        }
        return contents;
    }

    public Player getPlayer() {
        return player;
    }

    public TimeZone getTimeZone() {
        if (timeZone != null) return timeZone;

        if (player.getAddress() == null) return null;
        try {
            return timeZone = IPService.getTimeZoneForIP(player.getAddress().getHostString());
        } catch (IOException ex) {
            plugin.warning("Failed to get timezone for " + player.getName() + ", " + ex.getMessage());
            if (plugin.getAPConfig().getDebug() > 0) plugin.print(ex);
        }

        return timeZone = TimeZone.getDefault();
    }
}
