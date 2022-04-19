package dev.heliosares.auxprotect;

import dev.heliosares.auxprotect.database.DbEntry;

public class AuxProtectAPI {

	/**
	 * Add an entry to the queue to be logged.
	 * 
	 * @param entry The entry to be logged.
	 */
	public static void add(DbEntry entry) {
		((AuxProtect) AuxProtect.getInstance()).dbRunnable.add(entry);
	}
}
