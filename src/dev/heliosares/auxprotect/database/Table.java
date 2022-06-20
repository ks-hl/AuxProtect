package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;

public enum Table {
	AUXPROTECT_MAIN, AUXPROTECT_SPAM, AUXPROTECT_LONGTERM, AUXPROTECT_ABANDONED, AUXPROTECT_INVENTORY,
	AUXPROTECT_COMMANDS, AUXPROTECT_POSITION,

	AUXPROTECT_API, AUXPROTECT_UIDS, AUXPROTECT_WORLDS, AUXPROTECT_API_ACTIONS, AUXPROTECT_VERSION;

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
		} else if (this == Table.AUXPROTECT_MAIN || this == Table.AUXPROTECT_SPAM || this == Table.AUXPROTECT_INVENTORY
				|| this == Table.AUXPROTECT_API) {
			return "(time, uid, action_id, world_id, x, y, z, target_id, data)";
		} else if (this == Table.AUXPROTECT_ABANDONED) {
			return "(time, uid, action_id, world_id, x, y, z, target_id)";
		} else if (this == Table.AUXPROTECT_POSITION) {
			return "(time, uid, action_id, world_id, x, y, z, pitch, yaw, target_id)";
		}
		return null;
	}

	public String getValuesTemplate(boolean bungee) {
		if (this == Table.AUXPROTECT_LONGTERM) {
			return "(?, ?, ?, ?)";
		} else if (this == Table.AUXPROTECT_COMMANDS) {
			if (bungee) {
				return "(?, ?, ?)";
			}
			return "(?, ?, ?, ?, ?, ?, ?)";
		} else if (bungee) {
			return "(?, ?, ?, ?, ?)";
		} else if (this == Table.AUXPROTECT_MAIN || this == Table.AUXPROTECT_SPAM || this == Table.AUXPROTECT_INVENTORY
				|| this == Table.AUXPROTECT_API) {
			return "(?, ?, ?, ?, ?, ?, ?, ?, ?)";
		} else if (this == Table.AUXPROTECT_ABANDONED) {
			return "(?, ?, ?, ?, ?, ?, ?, ?)";
		} else if (this == Table.AUXPROTECT_POSITION) {
			return "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		}
		return null;
	}

}