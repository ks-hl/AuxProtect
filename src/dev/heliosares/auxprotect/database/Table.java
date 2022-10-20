package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;

public enum Table {
	AUXPROTECT_MAIN, AUXPROTECT_SPAM, AUXPROTECT_LONGTERM, AUXPROTECT_ABANDONED, AUXPROTECT_XRAY, AUXPROTECT_INVENTORY,
	AUXPROTECT_COMMANDS, AUXPROTECT_POSITION,

	AUXPROTECT_API, AUXPROTECT_UIDS, AUXPROTECT_WORLDS, AUXPROTECT_API_ACTIONS, AUXPROTECT_VERSION, AUXPROTECT_INVBLOB,
	AUXPROTECT_INVDIFF, AUXPROTECT_INVDIFFBLOB;

	@Override
	public String toString() {
		return SQLManager.getTablePrefix() + super.toString().toLowerCase();
	}

	public boolean exists(IAuxProtect plugin) {
		if (plugin.isBungee() && !this.isOnBungee()) {
			return false;
		}
		if (!plugin.getAPConfig().isPrivate() && this == AUXPROTECT_ABANDONED) {
			return false;
		}
		return true;
	}

	public boolean hasAPEntries() {
		switch (this) {
		case AUXPROTECT_MAIN:
		case AUXPROTECT_SPAM:
		case AUXPROTECT_LONGTERM:
		case AUXPROTECT_ABANDONED:
		case AUXPROTECT_XRAY:
		case AUXPROTECT_INVENTORY:
		case AUXPROTECT_COMMANDS:
		case AUXPROTECT_POSITION:
		case AUXPROTECT_API:
			return true;
		default:
			return false;
		}
	}

	public boolean hasData() {
		switch (this) {
		case AUXPROTECT_MAIN:
		case AUXPROTECT_INVENTORY:
		case AUXPROTECT_SPAM:
		case AUXPROTECT_API:
		case AUXPROTECT_XRAY:
			return true;
		default:
			return false;
		}
	}

	public boolean isOnBungee() {
		switch (this) {
		case AUXPROTECT_MAIN:
		case AUXPROTECT_COMMANDS:
		case AUXPROTECT_LONGTERM:
		case AUXPROTECT_API:
		case AUXPROTECT_UIDS:
		case AUXPROTECT_API_ACTIONS:
		case AUXPROTECT_VERSION:
			return true;
		default:
			return false;
		}
	}

	public boolean hasLocation() {
		switch (this) {
		case AUXPROTECT_MAIN:
		case AUXPROTECT_ABANDONED:
		case AUXPROTECT_XRAY:
		case AUXPROTECT_INVENTORY:
		case AUXPROTECT_SPAM:
		case AUXPROTECT_COMMANDS:
		case AUXPROTECT_POSITION:
		case AUXPROTECT_API:
			return true;
		default:
			return false;
		}
	}

	public boolean hasLook() {
		switch (this) {
		case AUXPROTECT_POSITION:
			return true;
		default:
			return false;
		}
	}

	public boolean hasActionId() {
		switch (this) {
		case AUXPROTECT_COMMANDS:
		case AUXPROTECT_XRAY:
			return false;
		default:
			return true;
		}
	}

	public boolean hasStringTarget() {
		switch (this) {
		case AUXPROTECT_COMMANDS:
		case AUXPROTECT_LONGTERM:
			return true;
		default:
			return false;
		}
	}

	public String getValuesHeader(boolean bungee) {
		if (this == Table.AUXPROTECT_LONGTERM) {
			return "(time, uid, action_id, target)";
		} else if (this == Table.AUXPROTECT_COMMANDS) {
			if (bungee) {
				return "(time, uid, target)";
			}
			return "(time, uid, world_id, x, y, z, target)";
		} else if (bungee) {
			return "(time, uid, action_id, target_id, data)";
		} else if (this == Table.AUXPROTECT_MAIN || this == Table.AUXPROTECT_SPAM || this == Table.AUXPROTECT_API) {
			return "(time, uid, action_id, world_id, x, y, z, target_id, data)";
		} else if (this == Table.AUXPROTECT_INVENTORY) {
			return "(time, uid, action_id, world_id, x, y, z, target_id, data, hasblob)";
		} else if (this == Table.AUXPROTECT_ABANDONED) {
			return "(time, uid, action_id, world_id, x, y, z, target_id)";
		} else if (this == Table.AUXPROTECT_POSITION) {
			return "(time, uid, action_id, world_id, x, y, z, pitch, yaw, target_id)";
		} else if (this == Table.AUXPROTECT_XRAY) {
			return "(time, uid, world_id, x, y, z, target_id, rating, data)";
		}
		return null;
	}

	public static String getValuesTemplate(int numColumns) {
		if (numColumns <= 0) {
			return null;
		}
		String output = "(";
		for (int i = 0; i < numColumns; i++) {
			if (i > 0) {
				output += ", ";
			}
			output += "?";
		}
		output += ")";
		return output;
	}

	public int getNumColumns(boolean bungee) {
		if (this == Table.AUXPROTECT_LONGTERM) {
			return 4;
		}
		if (this == Table.AUXPROTECT_COMMANDS) {
			if (bungee) {
				return 3;
			}
			return 7;
		}
		if (bungee) {
			return 5;
		}

		switch (this) {
		case AUXPROTECT_ABANDONED:
			return 8;
		case AUXPROTECT_MAIN:
		case AUXPROTECT_SPAM:
		case AUXPROTECT_API:
		case AUXPROTECT_XRAY:
			return 9;
		case AUXPROTECT_POSITION:
		case AUXPROTECT_INVENTORY:
			return 10;
		default:
			return -1;
		}
	}

	public String getValuesTemplate(boolean bungee) {
		return getValuesTemplate(getNumColumns(bungee));
	}

	public String getSQLCreateString(IAuxProtect plugin) {
		if (!this.hasAPEntries()) {
			return null;
		}
		String stmt = "CREATE TABLE IF NOT EXISTS " + toString() + " (\n";
		stmt += "    time BIGINT";
		stmt += ",\n    uid integer";
		if (hasActionId()) {
			stmt += ",\n    action_id SMALLINT";
		}
		if (!plugin.isBungee() && hasLocation()) {
			stmt += ",\n    world_id SMALLINT";
			stmt += ",\n    x INTEGER";
			stmt += ",\n    y SMALLINT";
			stmt += ",\n    z INTEGER";
		}
		if (hasLook()) {
			stmt += ",\n    pitch SMALLINT";
			stmt += ",\n    yaw SMALLINT";
		}
		if (hasStringTarget()) {
			stmt += ",\n    target ";
			if (this == AUXPROTECT_COMMANDS) {
				stmt += "LONGTEXT";
			} else {
				stmt += "varchar(255)";
			}
		} else {
			stmt += ",\n    target_id integer";
		}
		if (this == AUXPROTECT_XRAY) {
			stmt += ",\n    rating SMALLINT";
		}
		if (hasData()) {
			stmt += ",\n    data LONGTEXT";
		}
		if (hasBlob()) {
			stmt += ",\n    hasblob BOOL";
		}
		stmt += "\n);";

		return stmt;
	}

	public boolean hasBlob() {
		if (this == AUXPROTECT_INVENTORY) {
			return true;
		}
		return false;
	}

}