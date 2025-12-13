package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.kshl.kshlib.sql.SQLSet;
import jakarta.annotation.Nullable;
import lombok.Getter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MigrationManager {
    public static final int TARGET_DB_VERSION = 19;
    private final SQLManager sql;
    private final Connection connection;
    private final IAuxProtect plugin;
    private final Map<Integer, MigrationAction> migrationActions;
    private final SQLSet.Int migrationTaskSet;
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
        this.migrationTaskSet = new SQLSet.Int(sql, Table.AUXPROTECT_MIGRATION_TASKS.toString(), false);
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
            sql.execute(connection, sql.getDropIndexStatement("idx_" + Table.AUXPROTECT_UIDS + "_hash", Table.AUXPROTECT_UIDS.toString(), true));
            sql.execute(connection, "ALTER TABLE " + Table.AUXPROTECT_UIDS + " RENAME TO " + Table.AUXPROTECT_UIDS + "_aptemp");
        }, () -> {
            // UID migrations
            sql.execute(connection, sql.getInsertOrIgnore() + " INTO " + Table.AUXPROTECT_UIDS + " (id,value) SELECT uid,uuid FROM " + Table.AUXPROTECT_UIDS + "_aptemp");
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
        // 18
        //

        migrationActions.put(18, new MigrationAction(true, () -> {

            // Fix duplicate snowflakes
            for (Table table : Table.values()) {
                if (!table.hasAPEntries() && table != Table.AUXPROTECT_INVDIFF) continue;
                if (!table.exists(plugin)) continue;
                if (!sql.tableExists(connection, table.toString())) continue;

                plugin.info("Checking/fixing duplicate snowflakes in " + table);

                plugin.info("Creating temp index");
                sql.execute(connection, "CREATE INDEX IF NOT EXISTS idx_" + table + "_time ON " + table + " (time)");

                plugin.info("Fixing duplicate snowflakes");

                int limit = 1000;
                while (sql.query(connection, """
                          SELECT 1 FROM %s GROUP BY time HAVING COUNT(*) > 1
                        """.formatted(table), ResultSet::next) && --limit > 0) {

                    String sqlText;
                    if (sql.isMySQL()) {
                        sqlText = """
                                UPDATE %s t
                                JOIN (
                                  SELECT time AS dup_time, (time DIV 100000) AS base
                                  FROM %s
                                  GROUP BY time
                                  HAVING COUNT(*) > 1
                                ) d ON d.dup_time = t.time
                                SET t.time = d.base*100000 + FLOOR(RAND()*100000);
                                """.formatted(table, table);
                    } else {
                        sqlText = """
                                UPDATE %s
                                SET time = ( (time/100000)*100000 + (ABS(RANDOM()) %% 100000) )
                                WHERE time IN (
                                  SELECT time FROM %s GROUP BY time HAVING COUNT(*) > 1
                                );
                                """.formatted(table, table);
                    }
                    sql.execute(connection, sqlText);
                }
                if (limit == 0) {
                    throw new IllegalStateException("Duplicate snowflakes detected in " + table + ". Failed to fix them after 1000 attempts.");
                }


                plugin.info("Removing old/temp time index on " + table);
                sql.execute(connection, sql.getDropIndexStatement("idx_" + table + "_time", table.toString(), true));
                sql.execute(connection, sql.getDropIndexStatement("idx_" + table + "_action_time", table.toString(), true));
            }
        }, () -> {
            for (Table table : Table.values()) {
                if (!table.hasBlob()) continue;
                if (sql.columnExists(connection, table.toString(), "ablob")) continue;
                sql.execute(connection, "ALTER TABLE " + table + " ADD COLUMN ablob BLOB");
            }

            sql.execute(connection, "ALTER TABLE " + Table.AUXPROTECT_LONGTERM + " ADD COLUMN target_id INT");

            plugin.info("Migrating longterm to UIDs. Creating temp index.");
            sql.execute(connection, "CREATE INDEX idx_auxprotect_longterm_target ON " + Table.AUXPROTECT_LONGTERM + " (target)");
            plugin.info("Migrating uids");
            while (true) {
                var toUpdate = sql.query(connection, "SELECT DISTINCT target FROM " + Table.AUXPROTECT_LONGTERM + " WHERE target IS NOT NULL LIMIT 10000", rs -> {
                    Set<String> newIDs = new HashSet<>();
                    while (rs.next()) {
                        newIDs.add(rs.getString("target"));
                    }
                    return newIDs;
                });
                if (toUpdate.isEmpty()) break;
                var toUpdateMap = sql.getUidManager().getOrInsertAll(connection, toUpdate);
                sql.executeBatch(connection, "UPDATE " + Table.AUXPROTECT_LONGTERM + " SET target_id=?,target=null WHERE target=?", toUpdateMap.entrySet(), e -> List.of(e.getValue(), e.getKey()));
            }
            if (sql.columnExists(connection, Table.AUXPROTECT_LONGTERM.toString(), "target_hash")) {
                sql.execute(connection, "UPDATE " + Table.AUXPROTECT_LONGTERM + " SET target_hash=null");
            }

            plugin.info("Removing temp index");
            sql.execute(connection, sql.getDropIndexStatement("idx_auxprotect_longterm_target", Table.AUXPROTECT_LONGTERM.toString(), false));
        }));


        migrationActions.put(19, new MigrationAction(true, () -> {
            plugin.info("Fixing duplicate UID values");
            sql.query(connection, "SELECT id,value,count(1) AS ct FROM " + Table.AUXPROTECT_UIDS + " GROUP BY lower(value) HAVING ct>1", rs -> {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String value = rs.getString("value");
                    int count = rs.getInt("ct");
                    sql.execute(connection, "UPDATE " + Table.AUXPROTECT_UIDS + " SET value=? WHERE id=?", value + "_" + count, id);
                }
            });
        }, () -> {
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
        migrationTaskSet.init(connection);
        sql.execute(connection, "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_VERSION + " (time BIGINT, version INTEGER)");

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

        if (plugin.getAPConfig().isMigrateDataNormalization()) {
            migrateDataNormalization();
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

    private void migrateDataNormalization() throws SQLException {
        if (migrationTaskSet.contains(connection, OptionalMigrationTask.DATA_NORMALIZATION)) {
            plugin.info("Data already normalized, skipping. You can unset \"MigrateDataNormalization\" in config.yml to remove this message.");
            return;
        }
        plugin.info("Normalizing data...");

        // Migrate IPs to UID
        {
            plugin.info("Normalizing a:session IPs. Creating temp index");

            sql.execute(connection, "CREATE INDEX idx_auxprotect_main_data ON " + Table.AUXPROTECT_MAIN + " (action_id,data)");

            plugin.info("Migrating IPs");
            while (true) {
                var toUpdate = sql.query(connection, "SELECT DISTINCT data FROM " + Table.AUXPROTECT_MAIN + " WHERE action_id=5 AND data IS NOT NULL LIMIT 10000", rs -> {
                    Set<String> newIDs = new HashSet<>();
                    while (rs.next()) {
                        String data = rs.getString("data");
                        if (data.startsWith("IP: ")) data = data.substring(4);
                        newIDs.add(data);
                    }
                    return newIDs;
                });
                if (toUpdate.isEmpty()) break;
                var toUpdateMap = sql.getUidManager().getOrInsertAll(connection, toUpdate);
                sql.executeBatch(connection, "UPDATE " + Table.AUXPROTECT_MAIN + " SET target_id=?,data=null WHERE action_id=5 AND (data=? OR data=?)", toUpdateMap.entrySet(), e -> List.of(e.getValue(), e.getKey(), "IP: " + e.getKey()));
            }

            plugin.info("Removing temp index");
            sql.execute(connection, sql.getDropIndexStatement("idx_auxprotect_main_data", Table.AUXPROTECT_MAIN.toString(), false));
        }

        // a:hurt and a:kill
        {
            plugin.info("Normalizing a:hurt and a:kill data. Creating temp index, this may take a while.");

            sql.execute(connection, "CREATE INDEX idx_auxprotect_spam_data ON " + Table.AUXPROTECT_SPAM + " (action_id,data)");

            plugin.info("Migrating IPs");
            while (true) {
                var toUpdate = sql.query(connection, "SELECT DISTINCT data FROM " + Table.AUXPROTECT_SPAM + " WHERE action_id=5 AND data IS NOT NULL LIMIT 10000", rs -> {
                    Set<String> newIDs = new HashSet<>();
                    while (rs.next()) {
                        String data = rs.getString("data");
                        if (data.startsWith("IP: ")) data = data.substring(4);
                        newIDs.add(data);
                    }
                    return newIDs;
                });
                if (toUpdate.isEmpty()) break;
                var toUpdateMap = sql.getUidManager().getOrInsertAll(connection, toUpdate);
                sql.executeBatch(connection, "UPDATE " + Table.AUXPROTECT_SPAM + " SET target_id=?,data=null WHERE action_id=5 AND (data=? OR data=?)", toUpdateMap.entrySet(), e -> List.of(e.getValue(), e.getKey(), "IP: " + e.getKey()));
            }

            plugin.info("Removing temp index");
            sql.execute(connection, sql.getDropIndexStatement("idx_auxprotect_spam_data", Table.AUXPROTECT_SPAM.toString(), false));
        }

        plugin.info("Migration complete");
        migrationTaskSet.add(connection, OptionalMigrationTask.DATA_NORMALIZATION);
        plugin.info("Completion state stored. You can unset \"MigrateDataNormalization\" in config.yml.");
    }


    @FunctionalInterface
    interface MigrateRunnable {
        void run() throws SQLException;
    }

    private record MigrationAction(boolean necessary, @Nullable MigrateRunnable preTableAction,
                                   @Nullable MigrateRunnable postTableAction) {
    }

    private static class OptionalMigrationTask {
        static final int DATA_NORMALIZATION = 1;
    }
}
