package dev.heliosares.auxprotect.database;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import dev.heliosares.auxprotect.core.IAuxProtect;

public class EntryAction {
	private static final HashMap<String, EntryAction> values = new HashMap<>();
	private static final Set<Integer> usedids = new HashSet<>();

	public static EntryAction[] values() {
		return values.values().toArray(new EntryAction[0]);
	}

	public static EntryAction getAction(String key) {
		return values.get(key);
	}

	public static EntryAction getAction(int id) {
		if (id == 0)
			return null;
		for (EntryAction action : values.values()) {
			if (action.id == id || action.idPos == id) {
				return action;
			}
		}
		return null;
	}

	// START MAIN (0)
	public static final EntryAction LEASH = new EntryAction("leash", 2, 3);
	public static final EntryAction SESSION = new EntryAction("session", 4, 5);
	public static final EntryAction KICK = new EntryAction("kick", 6);
	public static final EntryAction SHOP = new EntryAction("shop", 8, 9);
	public static final EntryAction BUCKET = new EntryAction("bucket", 10, 11);
	public static final EntryAction MOUNT = new EntryAction("mount", 12, 13);

	public static final EntryAction ALERT = new EntryAction("alert", 128);
	public static final EntryAction RESPAWN = new EntryAction("respawn", 129);
//	public static final EntryAction XRAYCHECK = new EntryAction("xraycheck", 130);
	public static final EntryAction CENSOR = new EntryAction("censor", 132);
	public static final EntryAction MSG = new EntryAction("msg", 133);
	public static final EntryAction CONSUME = new EntryAction("consume", 134);
	// SKIPPED 135
	public static final EntryAction RECOVER = new EntryAction("recover", 136);
	public static final EntryAction MONEY = new EntryAction("money", 137);
	public static final EntryAction GAMEMODE = new EntryAction("gamemode", 138);
	public static final EntryAction TAME = new EntryAction("tame", 139);
	public static final EntryAction JOBS = new EntryAction("jobs", 140);
	public static final EntryAction PAY = new EntryAction("pay", 141);
	public static final EntryAction LIGHTNING = new EntryAction("lightning", 142);
	public static final EntryAction EXPLODE = new EntryAction("explode", 143);
	// END MAIN (255)

	// START SPAM(256)
	// SKIPPED 256
	public static final EntryAction HURT = new EntryAction("hurt", 257);
	public static final EntryAction INV = new EntryAction("inv", 258, 259);
	// SKIPPED 260
	public static final EntryAction KILL = new EntryAction("kill", 261);
	public static final EntryAction LAND = new EntryAction("land", 262);
	public static final EntryAction ELYTRA = new EntryAction("elytra", 263, 264);
	public static final EntryAction ACTIVITY = new EntryAction("activity", 265);
	public static final EntryAction TOTEM = new EntryAction("totem", 266);
	// END SPAM(511)

	// START IGNOREABANDONED(512)
	public static final EntryAction IGNOREABANDONED = new EntryAction("ignoreabandoned", 512);
	// END IGNOREABANDONED(767)

	// START LONGTERM (768)
	public static final EntryAction IP = new EntryAction("ip", 768);
	public static final EntryAction USERNAME = new EntryAction("username", 769);
	// END LONGTERM (1023)

	// START INVENTORY (1024)
	public static final EntryAction INVENTORY = new EntryAction("inventory", 1024);
	public static final EntryAction LAUNCH = new EntryAction("launch", 1025);
	public static final EntryAction GRAB = new EntryAction("grab", 1026);
	public static final EntryAction DROP = new EntryAction("drop", 1027);
	public static final EntryAction PICKUP = new EntryAction("pickup", 1028);
	public static final EntryAction AUCTIONLIST = new EntryAction("auctionlist", 1029);
	public static final EntryAction AUCTIONBUY = new EntryAction("auctionbuy", 1030);
//	public static final EntryAction AUCTIONBID = new EntryAction("auctionbid", 1031);
	public static final EntryAction BREAKITEM = new EntryAction("breakitem", 1032);

	public static final EntryAction ITEMFRAME = new EntryAction("itemframe", 1152, 1153);
	// END INVENTORY(1279)

	// START COMMANDS (1280)
	public static final EntryAction COMMAND = new EntryAction("command", 1280);
	// END COMMANDS(1289)

	// START POSITION (1290)
	public static final EntryAction POS = new EntryAction("pos", 1290);
	public static final EntryAction TP = new EntryAction("tp", 1291, 1292);
	// END POSITION(1299)

	// START XRAY (1300)
	public static final EntryAction VEIN = new EntryAction("vein", 1300);
	// END XRAY(1309)

	public final boolean hasDual;
	public final int id;
	public final int idPos;
	public final String name;
	private boolean enabled;
	private boolean lowestpriority;

	private String overridePText;
	private String overrideNText;

	protected EntryAction(String name, int id, int idPos) {
		this.hasDual = true;
		this.id = id;
		this.idPos = idPos;
		this.name = name;

		checkId(name, id);
		checkId(name, idPos);

		enabled = true;
		values.put(name, this);
	}

	protected EntryAction(String name, int id) {
		this.hasDual = false;
		this.id = id;
		this.idPos = id;
		this.name = name;

		checkId(name, id);

		enabled = true;
		values.put(name, this);
	}

	private void checkId(String name, int id) throws IllegalArgumentException {
		if (!usedids.add(id)) {
			throw new IllegalArgumentException("Duplicate entry id: " + id + " from action: " + name);
		}
	}

	protected EntryAction(String key, int nid, int pid, String ntext, String ptext) {
		this(key, nid, pid);
		this.overrideNText = ntext;
		this.overridePText = ptext;
	}

	protected EntryAction(String key, int id, String text) {
		this(key, id);
		this.overrideNText = text;
	}

	public String getText(IAuxProtect plugin, boolean state) {
		if (hasDual) {
			if (state) {
				if (overridePText != null) {
					return overridePText;
				}
			} else {
				if (overrideNText != null) {
					return overrideNText;
				}
			}
			return plugin.translate(getLang(state));
		}
		if (overrideNText != null) {
			return overrideNText;
		}
		return plugin.translate(getLang(state));
	}

	private String getLang(boolean state) {
		if (hasDual) {
			return "actions." + toString().toLowerCase() + "." + (state ? "p" : "n");
		}
		return "actions." + toString().toLowerCase();
	}

	public boolean isBungee() {
		if (id == MSG.id || id == COMMAND.id || id == IP.id || id == USERNAME.id || id == SESSION.id) {
			return true;
		}
		return false;
	}

	public boolean isSpigot() {
		if (id == MSG.id) {
			return false;
		}
		return true;
	}

	public Table getTable() {
		if (id < 256) {
			return Table.AUXPROTECT_MAIN;
		}
		if (id < 512) {
			return Table.AUXPROTECT_SPAM;
		}
		if (id < 768) {
			return Table.AUXPROTECT_ABANDONED;
		}
		if (id < 1024) {
			return Table.AUXPROTECT_LONGTERM;
		}
		if (id < 1280) {
			return Table.AUXPROTECT_INVENTORY;
		}
		if (id < 1290) {
			return Table.AUXPROTECT_COMMANDS;
		}
		if (id < 1300) {
			return Table.AUXPROTECT_POSITION;
		}
		if (id < 1310) {
			return Table.AUXPROTECT_XRAY;
		}
		if (id > 10000) {
			return Table.AUXPROTECT_API;
		}
		return null;
	}

	public int getId(boolean state) {
		if (state) {
			return idPos;
		}
		return id;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean state) {
		if (this.equals(USERNAME)) { // Don't allow this to be disabled.
			enabled = true;
			return;
		}
		this.enabled = state;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public boolean equals(Object other_) {
		if (!(other_ instanceof EntryAction)) {
			return false;
		}
		EntryAction other = (EntryAction) other_;
		return this.id == other.id && this.idPos == other.idPos;
	}

	public boolean isLowestpriority() {
		return lowestpriority;
	}

	public void setLowestpriority(boolean lowestpriority) {
		this.lowestpriority = lowestpriority;
	}

}
