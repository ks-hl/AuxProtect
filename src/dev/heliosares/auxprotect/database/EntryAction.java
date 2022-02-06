package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.database.SQLiteManager.TABLE;

public enum EntryAction {
	// TODO Migrate Kick to singles

	// START MISC
	VEIN(-1),
	// END MISC

	// START DEFAULT (0)
	LEASH(2, 3), SESSION(4, 5), KICK(6), SHOP(8, 9), BUCKET(10, 11), MOUNT(12, 13),
	// default doubles
	ALERT(128), RESPAWN(129), XRAYCHECK(130), CENSOR(132), MSG(133), CONSUME(134), /* 135 - SKIPPED */ RECOVER(136),
	MONEY(137),
	// END DEFAULT (255)

	// START SPAM(256)
	POS(256), HURT(257), INV(258, 259), COMMAND(260), KILL(261), LAND(262),
	// END SPAM(511)

	// START IGNOREABANDONED(512)
	IGNOREABANDONED(512),
	// END IGNOREABANDONED(767)

	// START LONGTERM (768)
	IP(768), USERNAME(769),
	// END LONGTERM (1023)

	// START INVENTORY (1024)
	INVENTORY(1024), LAUNCH(1025), GRAB(1026), DROP(1027), PICKUP(1028), AHLIST(1029), AHBUY(1030),
	// inventory doubles
	ITEMFRAME(1152, 1153)
	// END INVENTORY(1280)
	;

	public final boolean hasDual;
	public final int id;
	public final int idPos;
	private boolean enabled;

	EntryAction(int id, int idPos) {
		this.hasDual = true;
		this.id = id;
		this.idPos = idPos;

		enabled = true;
	}

	EntryAction(int id) {
		this.hasDual = false;
		this.id = id;
		this.idPos = id;

		enabled = true;
	}

	public String getLang(boolean state) {
		if (hasDual) {
			return "actions." + toString().toLowerCase() + "." + (state ? "p" : "n");
		}
		return "actions." + toString().toLowerCase();
	}

	public static EntryAction valueOfString(String str) {
		for (EntryAction action : values()) {
			if (action.toString().equalsIgnoreCase(str)) {
				return action;
			}
		}
		return null;
	}

	public boolean isBungee() {
		switch (this) {
		case MSG:
			return true;
		case COMMAND:
			return true;
		default:
			break;
		}
		return false;
	}

	public boolean isSpigot() {
		switch (this) {
		case MSG:
			return false;
		default:
			break;
		}
		return true;
	}

	public TABLE getTable(boolean bungee) {
		if (bungee) {
			if (this == USERNAME || this == IP) {
				return TABLE.AUXPROTECT_LONGTERM;
			}
			return TABLE.AUXPROTECT;
		}
		if (id < 256) {
			return TABLE.AUXPROTECT;
		}
		if (id < 512) {
			return TABLE.AUXPROTECT_SPAM;
		}
		if (id < 768) {
			return TABLE.AUXPROTECT_ABANDONED;
		}
		if (id < 1024) {
			return TABLE.AUXPROTECT_LONGTERM;
		}
		if (id < 1280) {
			return TABLE.AUXPROTECT_INVENTORY;
		}
		return null;
	}

	public int getId(boolean state) {
		if (state) {
			return idPos;
		}
		return id;
	}

	public static EntryAction fromId(int id) {
		if (id == 0)
			return null;
		for (EntryAction action : values()) {
			if (action.id == id || action.idPos == id) {
				return action;
			}
		}
		return null;
	}

	public boolean isEnabled() {
		if (this == VEIN) {
			return false;
		}
		return enabled;
	}

	public void setEnabled(boolean state) {
		this.enabled = state;
	}
}
