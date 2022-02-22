package dev.heliosares.auxprotect.database;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;

public class DbEntry {

	private final long time;

	public long getTime() {
		return time;
	}

	public EntryAction getAction() {
		return action;
	}

	public boolean getState() {
		return state;
	}

	public String getData() {
		return data;
	}

	private final EntryAction action;
	private final boolean state;
	private final String data;

	public final String world;
	public final int x;
	public final int y;
	public final int z;

	public final String userUuid;
	private String user;
	public final String targetUuid;
	private String target;

	public DbEntry(String userUuid, EntryAction action, boolean state, String world, int x, int y, int z, String target,
			String data) {
		this.time = DatabaseRunnable.getTime();
		this.userUuid = userUuid;
		this.action = action;
		this.state = state;
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.targetUuid = target;
		this.data = data;
	}

	public DbEntry(String userUuid, EntryAction action, boolean state, Location location, String target,
			String suplmemental) {
		this(userUuid, action, state, location.getWorld().getName(), location.getBlockX(), location.getBlockY(),
				location.getBlockZ(), target, suplmemental);
	}

	public DbEntry(long time, String userUuid, EntryAction action, boolean state, String world, int x, int y, int z,
			String target, String data) {
		this.time = time;
		this.userUuid = userUuid;
		this.action = action;
		this.state = state;
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.targetUuid = target;
		this.data = data;
	}

	public String getUser(SQLManager sqlManager) {
		if (user != null) {
			return user;
		}
		if (!userUuid.startsWith("$") || userUuid.length() != 37) {
			return user = userUuid;
		}
		user = sqlManager.getUsernameFromUUID(userUuid);
		if (user == null) {
			user = userUuid;
		}
		return user;
	}

	public String getTarget() {
		if (target != null) {
			return target;
		}
		if (!targetUuid.startsWith("$") || targetUuid.length() != 37) {
			return target = targetUuid;
		}
		try {
			target = Bukkit.getServer().getOfflinePlayer(UUID.fromString(targetUuid.substring(1))).getName();
		} catch (NoClassDefFoundError e) {
			target = targetUuid;
		}
		return target;
	}

	public double getBoxDistance(DbEntry entry) {
		if (!entry.world.equals(world)) {
			return -1;
		}
		return Math.max(Math.max(Math.abs(entry.x - x), Math.abs(entry.y - y)), Math.abs(entry.z - z));
	}

	public double getDistance(DbEntry entry) {
		return Math.sqrt(getDistanceSq(entry));
	}

	public double getDistanceSq(DbEntry entry) {
		return Math.pow(x - entry.x, 2) + Math.pow(y - entry.y, 2) + Math.pow(z - entry.z, 2);
	}

	public Location getLocation() {
		if (world == null || world.equals("$null") || world.equals("null")) {
			return null;
		}
		return new Location(Bukkit.getWorld(world), x, y, z);
	}
}
