package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import jakarta.annotation.Nullable;
import lombok.Getter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MigrationManager {
    public static final int TARGET_DB_VERSION = 17;
    private final SQLManager sql;
    private final Connection connection;
    private final IAuxProtect plugin;
    private final Map<Integer, MigrationAction> migrationActions;
    private boolean isMigrating;
    @Getter
    private int version;
    @Getter
    private int originalVersion;
    @Getter
    private int complete;
    @Getter
    private int total;
    private int migratingToVersion;

    MigrationManager(SQLManager sql, Connection connection, IAuxProtect plugin) {
        this.sql = sql;
        this.plugin = plugin;
        this.connection = connection;
        HashMap<Integer, MigrationAction> migrationActions = new HashMap<>();

        //
        // 16
        //

        migrationActions.put(16, new MigrationAction(true, () -> {
        }, () -> {
            for (Table table : Table.values()) {
                if (!table.hasAPEntries() && table != Table.AUXPROTECT_INVDIFF) continue;
                if (!table.exists(plugin)) continue;
                sql.execute(connection, "UPDATE " + table + " set time=time*? WHERE time<?", Snowflake.COUNTER_FACTOR, 1735689600000L * Snowflake.COUNTER_FACTOR);
            }
        }));

        //
        // 17
        //

        migrationActions.put(17, new MigrationAction(true, () -> {
            sql.execute(connection, "DROP TABLE IF EXISTS " + Table.AUXPROTECT_UIDS + "_temp");
            sql.execute(connection, "DROP INDEX IF EXISTS idx_" + Table.AUXPROTECT_UIDS + "_hash");
            sql.execute(connection, "ALTER TABLE " + Table.AUXPROTECT_UIDS + " RENAME TO " + Table.AUXPROTECT_UIDS + "_aptemp");
        }, () -> {
            // UID migrations
            sql.execute(connection, "INSERT OR IGNORE INTO " + Table.AUXPROTECT_UIDS + " (id,value) SELECT uid,uuid FROM " + Table.AUXPROTECT_UIDS + "_aptemp");
            sql.execute(connection, "DROP TABLE " + Table.AUXPROTECT_UIDS + "_aptemp");

            // Chat migrations
            if (plugin.getSqlManager().isMySQL()) {
                sql.execute(connection, "ALTER TABLE " + Table.AUXPROTECT_CHAT + " CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            }
            if (plugin.getSqlManager().isMySQL()) {
                sql.execute(connection, "ALTER TABLE " + Table.AUXPROTECT_CHAT + " ADD COLUMN data LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            } else {
                sql.execute(connection, "ALTER TABLE " + Table.AUXPROTECT_CHAT + " ADD COLUMN data LONGTEXT");
            }
            sql.execute(connection, "ALTER TABLE " + Table.AUXPROTECT_CHAT + " ADD COLUMN target_id INTEGER");
            sql.execute(connection, "UPDATE " + Table.AUXPROTECT_CHAT + " SET data=target");
            sql.execute(connection, "UPDATE " + Table.AUXPROTECT_CHAT + " SET target=NULL");
        }));

        //
        // Finalizing
        //

        this.migrationActions = Collections.unmodifiableMap(migrationActions);

        int max = migrationActions.keySet().stream().max(Integer::compare).orElse(0);
        if (max != TARGET_DB_VERSION) {
            throw new IllegalArgumentException("Improperly defined migration actions. DBVERSION=" + TARGET_DB_VERSION + " with max action=" + max);
        }
    }

    public String getProgressString() {
        if (!isMigrating()) return null;
        if (migratingToVersion <= 0) return null;
        int progressPercentage = (int) Math.floor((double) getComplete() / getTotal() * 100);
        return String.format("Migration to v%d %d%% complete. (%d/%d). DO NOT INTERRUPT", migratingToVersion, progressPercentage, getComplete(), getTotal());
    }

    private void setVersion(int version) throws SQLException {
        sql.execute(connection, "INSERT INTO " + Table.AUXPROTECT_VERSION + " (time,version) VALUES (?,?)", System.currentTimeMillis(), this.version = version);
        plugin.info("Done migrating to version " + version);
    }

    boolean isMigrating() {
        return isMigrating;
    }

    void preTables() throws SQLException {

        sql.execute(connection, "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_VERSION + " (time BIGINT,version INTEGER)");

        sql.query(connection, "SELECT * FROM " + Table.AUXPROTECT_VERSION + " ORDER BY time DESC LIMIT 1", rs -> {
            if (rs.next()) version = rs.getInt("version");
        });
        sql.query(connection, "SELECT * FROM " + Table.AUXPROTECT_VERSION + " ORDER BY time ASC LIMIT 1", rs -> {
            if (rs.next()) originalVersion = rs.getInt("version");
        });

        int currentVersion = sql.getVersion();
        if (currentVersion < 1) {
            setVersion(currentVersion = TARGET_DB_VERSION);
            originalVersion = TARGET_DB_VERSION;
        }

        if (currentVersion < 6) {
            plugin.warning("This database version is no longer supported. Please download AuxProtect 1.2.7 and run it first to upgrade your database, wait for migration to complete, then run this version again. https://www.spigotmc.org/resources/auxprotect.99147/download?version=509785");
            throw new SQLException();
        }

        if (currentVersion < 15) {
            plugin.warning("This database version is no longer supported. Please download AuxProtect 1.3.3 and run it first to upgrade your database, wait for migration to complete, then run this version again. https://www.spigotmc.org/resources/auxprotect.99147/download?version=575276");
            throw new SQLException();
        }

        if (sql.getVersion() < TARGET_DB_VERSION) {
            plugin.info("Outdated DB Version: " + sql.getVersion() + ". Migrating to version: " + TARGET_DB_VERSION + "...");
            plugin.info("This may take a while. Please do not interrupt.");
            isMigrating = true;
            for (int i = sql.getVersion() + 1; i <= TARGET_DB_VERSION; i++) {
                MigrationAction action = migrationActions.get(i);
                if (action == null) {
                    plugin.warning("This database version is no longer supported. Please download an older AuxProtect version and run it first to upgrade your database, wait for migration to complete, then run this version again. https://www.spigotmc.org/resources/auxprotect.99147");
                    throw new SQLException();
                }
            }
        }

        for (int i = sql.getVersion() + 1; i <= TARGET_DB_VERSION; i++) {
            MigrationAction action = migrationActions.get(i);
            if (!action.necessary) continue;
            if (action.preTableAction != null) {
                plugin.info("Migrating to v" + i + ", performing pre-table migration... DO NOT INTERRUPT");
                migratingToVersion = i;
                complete = total = 0;
                action.preTableAction.run();
                plugin.info("Migrating to v" + i + " pre-table migration complete.");
                migratingToVersion = -1;
            }
        }
    }

    void postTables() throws SQLException {
        for (int i = sql.getVersion() + 1; i <= TARGET_DB_VERSION; i++) {
            MigrationAction action = migrationActions.get(i);
            if (action.necessary && action.postTableAction != null) {
                migratingToVersion = i;
                plugin.info("Migrating to v" + i + ", performing post-table migration... DO NOT INTERRUPT");
                complete = total = 0;
                action.postTableAction.run();
                plugin.info("Migrating to v" + i + " post-table migration complete.");
                migratingToVersion = -1;
            }
            setVersion(i);
        }

        /*
         * This should never be reached and is only here as a failsafe
         */
        if (sql.getVersion() < TARGET_DB_VERSION) {
            plugin.warning("No handling for upgrade: " + this.getVersion() + "->" + TARGET_DB_VERSION);
            setVersion(TARGET_DB_VERSION);
        }

        plugin.debug("Purging temporary tables");
        for (Table table : Table.values()) {
            sql.execute(connection, "DROP TABLE IF EXISTS " + table.toString() + "temp");
            sql.execute(connection, "DROP TABLE IF EXISTS " + table + "_temp");
        }

        isMigrating = false;
    }


    @FunctionalInterface
    interface MigrateRunnable {
        void run() throws SQLException;
    }

    private record MigrationAction(boolean necessary, @Nullable MigrateRunnable preTableAction,
                                   @Nullable MigrateRunnable postTableAction) {
    }
}
