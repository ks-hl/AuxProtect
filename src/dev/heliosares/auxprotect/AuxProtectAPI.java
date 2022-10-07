package dev.heliosares.auxprotect;

import dev.heliosares.auxprotect.bungee.AuxProtectBungee;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.LookupManager;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class AuxProtectAPI {

	public static IAuxProtect getInstance() {
		IAuxProtect ap = AuxProtectSpigot.getInstance();
		if (ap != null) {
			return ap;
		}
		ap = AuxProtectBungee.getInstance();
		if (ap != null) {
			return ap;
		}
		return null;
	}

	/**
	 * Add an entry to the queue to be logged.
	 * 
	 * @param entry The entry to be logged.
	 */
	public static void add(DbEntry entry) {
		getInstance().add(entry);
	}

	public static SQLManager getSQLManager() {
		return getInstance().getSqlManager();
	}

	public static LookupManager getLookupManager() {
		return getSQLManager().getLookupManager();
	}
}
