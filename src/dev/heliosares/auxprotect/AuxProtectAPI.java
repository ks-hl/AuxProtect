package dev.heliosares.auxprotect;

import dev.heliosares.auxprotect.bungee.AuxProtectBungee;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.LookupManager;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class AuxProtectAPI {

    private static IAuxProtect instance;

    public static IAuxProtect getInstance() {
        if (instance != null) {
            return instance;
        }
        try {
            if ((instance = AuxProtectSpigot.getInstance()) != null) {
                return instance;
            }
        } catch (Throwable ignored) {
        }
        try {
            if ((instance = AuxProtectBungee.getInstance()) != null) {
                return instance;
            }
        } catch (Throwable ignored) {
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
