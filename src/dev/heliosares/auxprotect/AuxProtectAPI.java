package dev.heliosares.auxprotect;

import dev.heliosares.auxprotect.bungee.AuxProtectBungee;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class AuxProtectAPI {

	/**
	 * Add an entry to the queue to be logged.
	 * 
	 * @param entry The entry to be logged.
	 */
	public static void add(DbEntry entry) {
		AuxProtectSpigot ap = ((AuxProtectSpigot) AuxProtectSpigot.getInstance());
		if (ap != null) {
			ap.add(entry);
			return;
		}
		AuxProtectBungee apb = ((AuxProtectBungee) AuxProtectBungee.getInstance());
		if (apb != null) {
			apb.add(entry);
			return;
		} // TODO core
	}
}
