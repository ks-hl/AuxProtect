package dev.heliosares.auxprotect;

import dev.heliosares.auxprotect.database.DbEntry;

public class AuxProtectApi {
	private static AuxProtect instance;

	static void setInstance(AuxProtect auxprotect) {
		instance = auxprotect;
	}

	public static AuxProtect getAuxProtect() {
		return instance;
	}

	public static void log(DbEntry entry) {
		((AuxProtect) AuxProtect.getInstance()).dbRunnable.add(entry);
	}
}
