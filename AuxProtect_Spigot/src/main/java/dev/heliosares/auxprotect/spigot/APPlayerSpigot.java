package dev.heliosares.auxprotect.spigot;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.adapters.sender.SpigotSenderAdapter;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.Activity;
import dev.heliosares.auxprotect.core.ActivityRecord;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.PosEntry;
import dev.heliosares.auxprotect.database.SpigotDbEntry;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.PosEncoder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class APPlayerSpigot extends APPlayer<Player> {
    private final List<ActivityRecord> activityStack = new ArrayList<>();
    private ArrayList<Activity> currentActivity;
    private final Player player;
    private final List<Byte> posBlob = new ArrayList<>();
    public long lastLoggedMoney;
    public long lastLoggedInventory;
    public long lastLoggedInventoryDiff;
    public long lastLoggedPos;
    public long lastMoved;
    public Location lastLocation;
    public long lastCheckedMovement;
    private double movedAmountThisMinute;
    public boolean hasMovedThisMinute;
    public long lastNotifyInactive;
    // hotbar, main, armor, offhand, echest
    private List<ItemStack> invDiffItems;
    private Location lastLocationDiff;
    private PosEncoder.Posture lastPosture;

    public APPlayerSpigot(AuxProtectSpigot plugin, Player player) {
        super(plugin, player);

        this.player = player;
    }

    @Override
    public String getName() {
        return player.getName();
    }

    public void addActivity(Activity a) {
        synchronized (activityStack) {
            if (currentActivity == null) currentActivity = new ArrayList<>();
            currentActivity.add(a);
        }
    }

    public String concludeActivityForMinute() {
        synchronized (activityStack) {
            while (activityStack.size() >= 30) {
                activityStack.remove(0);
            }
            ActivityRecord record = null;
            if (currentActivity != null || movedAmountThisMinute > 1E-6) {
                if (currentActivity == null) currentActivity = new ArrayList<>();
                record = new ActivityRecord(currentActivity, 0, movedAmountThisMinute);
            }
            activityStack.add(record);
            currentActivity = null;
            movedAmountThisMinute = 0;
            hasMovedThisMinute = false;

            if (record == null) return null;
            return record.toString();
        }
    }

    public void move() {
        synchronized (activityStack) {
            if (lastLocation != null && Objects.equals(lastLocation.getWorld(), getPlayer().getWorld())) {
                movedAmountThisMinute += Math.min(lastLocation.distance(getPlayer().getLocation()), 10);
            }
            lastLocation = getPlayer().getLocation();
            lastCheckedMovement = System.currentTimeMillis();
        }
    }

    public List<ActivityRecord> getActivityStack() {
        return activityStack;
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
        DbEntry entry = new SpigotDbEntry(AuxProtectSpigot.getLabel(getPlayer()), EntryAction.INVENTORY, false, loc, reason, "");
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
                getPlugin().getSqlManager().getInvDiffManager().logInvDiff(getPlayer().getUniqueId(), i, qty, item);
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

    @Nullable
    @Override
    public String getIPAddress() {
        if (player.getAddress() != null) return player.getAddress().getHostString();
        return null;
    }

    @Override
    public SenderAdapter getSenderAdapter() {
        return new SpigotSenderAdapter(getPlugin(), player);
    }

    @Override
    protected AuxProtectSpigot getPlugin() {
        return (AuxProtectSpigot) plugin;
    }
}
