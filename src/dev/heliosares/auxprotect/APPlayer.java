package dev.heliosares.auxprotect;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class APPlayer {
	public long lastLoggedMoney;
	public long lastLoggedInventory;
	public long lastLoggedPos;
	public long lastMoved;

	public long lastLoggedActivity;
	public Location lastLocation;
	public long lastCheckedMovement;
	public double moved;
	public double[] activity = new double[20];
	public int activityIndex;
	public long lastNotifyInactive;

	public void addActivity(double d) {
		activity[activityIndex] += d;
	}

	public final Player player;

	public APPlayer(Player player) {
		this.player = player;

		for (int i = 0; i < activity.length; i++) {
			activity[i] = -1;
		}
	}
}
