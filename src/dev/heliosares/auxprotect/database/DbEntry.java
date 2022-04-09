package dev.heliosares.auxprotect.database;

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

	private String userUuid;
	private String user;
	private int uid;

	private String targetUuid;
	private String target;
	private int target_id;

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

	public DbEntry(String userUuid, EntryAction action, boolean state, String target, String data) {
		this.time = DatabaseRunnable.getTime();
		this.userUuid = userUuid;
		this.action = action;
		this.state = state;
		this.world = null;
		this.x = 0;
		this.y = 0;
		this.z = 0;
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

	public DbEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z,
			String target, String data) {
		this.time = time;
		this.uid = uid;
		this.action = action;
		this.state = state;
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.targetUuid = target;
		this.data = data;
	}

	public DbEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z,
			int target_id, String data) {
		this.time = time;
		this.uid = uid;
		this.action = action;
		this.state = state;
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.target_id = target_id;
		this.data = data;
	}

	public String getUser() {
		if (user != null) {
			return user;
		}
		if (!getUserUUID().startsWith("$") || getUserUUID().length() != 37) {
			return user = getUserUUID();
		}
		user = SQLManager.getInstance().getUsernameFromUID(getUid());
		if (user == null) {
			user = getUserUUID();
		}
		return user;
	}

	public int getUid() {
		if (uid > 0) {
			return uid;
		}
		return uid = SQLManager.getInstance().getUIDFromUUID(getUserUUID(), true);
	}

	public int getTargetId() {
		if (action.getTable().hasStringTarget()) {
			return -1;
		}
		if (target_id > 0) {
			return target_id;
		}
		return target_id = SQLManager.getInstance().getUIDFromUUID(getTargetUUID(), true);
	}

	public String getTarget() {
		if (target != null) {
			return target;
		}
		if (action.getTable().hasStringTarget() || !getTargetUUID().startsWith("$") || getTargetUUID().length() != 37) {
			return target = getTargetUUID();
		}
		target = SQLManager.getInstance().getUsernameFromUID(getTargetId());
		if (target == null) {
			target = getTargetUUID();
		}
		return target;
	}

	public String getTargetUUID() {
		if (targetUuid != null) {
			return targetUuid;
		}
		if (target_id > 0) {
			targetUuid = SQLManager.getInstance().getUUIDFromUID(target_id);
		} else if (target_id == 0) {
			return targetUuid = "";
		}
		if (targetUuid == null) {
			targetUuid = "#null";
		}
		return targetUuid;
	}

	public String getUserUUID() {
		if (userUuid != null) {
			return userUuid;
		}
		if (uid > 0) {
			userUuid = SQLManager.getInstance().getUUIDFromUID(uid);
		} else if (uid == 0) {
			return userUuid = "";
		}
		if (userUuid == null) {
			userUuid = "#null";
		}
		return userUuid;
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
