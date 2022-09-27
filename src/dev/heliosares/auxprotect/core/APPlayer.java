package dev.heliosares.auxprotect.core;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.InvSerialization;

public class APPlayer {
	public long lastLoggedMoney;
	public long lastLoggedInventory;
	public long lastLoggedPos;
	public long lastMoved;

	public long lastLoggedActivity;
	public Location lastLocation;
	public long lastCheckedMovement;
	public double movedAmountThisMinute;
	public boolean hasMovedThisMinute;
	public double[] activity = new double[30];
	public int activityIndex;
	public long lastNotifyInactive;

	public void addActivity(double d) {
		activity[activityIndex] += d;
	}

	public final Player player;

	private final IAuxProtect plugin;

	public APPlayer(IAuxProtect plugin, Player player) {
		this.player = player;
		this.plugin = plugin;

		for (int i = 0; i < activity.length; i++) {
			activity[i] = -1;
		}
	}

	public long logInventory(String reason) {
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
}
