package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.LookupException;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MigrationManager {
    public static final int TARGET_DB_VERSION = 15;
    private final SQLManager sql;
    private final Connection connection;
    private final IAuxProtect plugin;
    private final Map<Integer, MigrationAction> migrationActions;
    private boolean isMigrating;
    private int version;
    private int originalVersion;
    private int complete;
    private int total;
    private int migratingToVersion;

    @SuppressWarnings("deprecation")
    MigrationManager(SQLManager sql, Connection connection, IAuxProtect plugin) {
        this.sql = sql;
        this.plugin = plugin;
        this.connection = connection;
        HashMap<Integer, MigrationAction> migrationActions = new HashMap<>();

        //
        // 7
        //

        migrationActions.put(7, new MigrationAction(plugin.getPlatform() == PlatformType.SPIGOT, null, () -> {

            total = sql.count(connection, Table.AUXPROTECT_INVDIFFBLOB.toString(), null);

            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVDIFFBLOB + " ADD COLUMN hash INT");

            String stmt = "SELECT blobid, ablob FROM " + Table.AUXPROTECT_INVDIFFBLOB + " WHERE (hash IS NULL) LIMIT ";

            plugin.debug(stmt, 3);

            boolean any = true;
            Set<Long> ignore = new HashSet<>();
            int limit = 50;
            while (any) {
                any = false;
                HashMap<Long, Integer> hashes = new HashMap<>();
                try (PreparedStatement pstmt = connection.prepareStatement(stmt + (limit + ignore.size()))) {
                    pstmt.setFetchSize(500);
                    try (ResultSet results = pstmt.executeQuery()) {
                        while (results.next()) {
                            long blobid = results.getLong("blobid");
                            if (ignore.contains(blobid)) continue;
                            any = true;
                            complete++;
                            hashes.put(blobid, Arrays.hashCode(sql.getBlob(results, "ablob")));
                        }
                    }
                }
                hashes.forEach((k, v) -> {
                    try {
                        sql.execute("UPDATE " + Table.AUXPROTECT_INVDIFFBLOB + " SET hash=? WHERE blobid=?", connection, v, k);
                    } catch (SQLException e) {
                        plugin.warning("Error while committing hash for blobid: " + k + ", this is probably fine.");
                        ignore.add(k);
                    }
                });
            }
        }));

        //
        // 8
        //

        migrationActions.put(8, new MigrationAction(plugin.getPlatform() == PlatformType.SPIGOT, () -> {
        }, () -> {
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVBLOB + " RENAME COLUMN time TO blobid");
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVBLOB + " RENAME COLUMN `blob` TO ablob"); //This was a poor naming choice as it conflicts with the datatype
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVBLOB + " ADD COLUMN hash INT");
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVENTORY + " ADD COLUMN blobid BIGINT");
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVENTORY + " ADD COLUMN damage INT");
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVENTORY + " ADD COLUMN qty INT");
            sql.execute("UPDATE " + Table.AUXPROTECT_INVENTORY + " SET blobid=time WHERE hasblob=1", connection);
            sql.execute("UPDATE " + Table.AUXPROTECT_INVENTORY + " SET hasblob=null", connection);
        }));

        //
        // 9
        //

        migrationActions.put(9, new MigrationAction(plugin.getPlatform() == PlatformType.SPIGOT, () -> {
        }, () -> tryExecute("ALTER TABLE " + Table.AUXPROTECT_POSITION + " ADD COLUMN ablob BLOB")));

        //
        // 10
        //

        migrationActions.put(10, new MigrationAction(plugin.getPlatform() == PlatformType.SPIGOT, () -> {
            try {
                sql.execute("ALTER TABLE " + Table.AUXPROTECT_LASTS + " RENAME COLUMN `key` TO name", connection);
            } catch (SQLException ignored) {
                // This may error if migrating from a version where the `lasts` table has not yet been created, as this is executed pre-tables
            }
        }, () -> {
        })); //This was a poor naming choice as it conflicts with the phrase `KEY`


        //
        // 11
        //

        migrationActions.put(11, new MigrationAction(plugin.getPlatform() == PlatformType.SPIGOT, () -> {
        }, () -> {
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_POSITION + " ADD COLUMN increment TINYINT");
            tryExecute("UPDATE " + Table.AUXPROTECT_POSITION + " set increment=0 where increment is null");
        }));


        //
        // 12
        //

        migrationActions.put(12, new MigrationAction(plugin.getPlatform() == PlatformType.SPIGOT, () -> {
        }, () -> {
            String buckets = Table.AUXPROTECT_MAIN + " WHERE action_id IN (10,11)";
            sql.execute("INSERT INTO " + Table.AUXPROTECT_INVENTORY + " (time, uid, action_id, world_id, x, y, z, target_id, data) SELECT time, uid, action_id, world_id, x, y, z, target_id, data FROM " + buckets, connection);
            sql.execute("UPDATE " + Table.AUXPROTECT_INVENTORY + " SET action_id=1158 WHERE action_id=10", connection);
            sql.execute("UPDATE " + Table.AUXPROTECT_INVENTORY + " SET action_id=1159 WHERE action_id=11", connection);
            sql.execute("DELETE FROM " + buckets, connection);
        }));


        //
        // 13
        //

        migrationActions.put(13, new MigrationAction(true, () -> {
        }, () -> {
            tryExecute("DELETE FROM " + Table.AUXPROTECT_UIDS + " WHERE ROWID NOT IN (SELECT MIN(ROWID) FROM " + Table.AUXPROTECT_UIDS + " GROUP BY uuid)");
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_UIDS + " RENAME TO " + Table.AUXPROTECT_UIDS + "_temp");
            sql.getUserManager().init(connection);
            tryExecute("INSERT INTO " + Table.AUXPROTECT_UIDS + " (uid,uuid) SELECT uid,uuid FROM " + Table.AUXPROTECT_UIDS + "_temp");

            Set<String> uidValues = new HashSet<>();
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT uuid FROM " + Table.AUXPROTECT_UIDS + " WHERE hash IS NULL")) {
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    while (rs.next()) {
                        uidValues.add(rs.getString(1));
                    }
                }
            }
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_LONGTERM + " ADD COLUMN target_hash INT");
            Set<String> longTermValues = new HashSet<>();
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT target FROM " + Table.AUXPROTECT_LONGTERM + " WHERE target_hash IS NULL")) {
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    while (rs.next()) {
                        longTermValues.add(rs.getString(1));
                    }
                }
            }

            total = uidValues.size() + longTermValues.size();
            complete = 0;
            for (String value : uidValues) {
                sql.execute("UPDATE " + Table.AUXPROTECT_UIDS + " SET hash=? WHERE uuid=?", connection, value.hashCode(), value);
                complete++;
            }

            for (String value : longTermValues) {
                sql.execute("UPDATE " + Table.AUXPROTECT_LONGTERM + " SET target_hash=? WHERE target=?", connection, value.toLowerCase().hashCode(), value);
                complete++;
            }
        }));


        //
        // 14
        //

        migrationActions.put(14, new MigrationAction(sql.isMySQL(), () -> {
        }, () -> {
            for (Table table : Table.values()) {
                if (!table.hasStringTarget() && !table.hasData() || !table.exists(plugin)) continue;
                sql.execute("ALTER TABLE " + table + " CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;", connection);
            }
        }));


        //
        // 15
        //

        migrationActions.put(15, new MigrationAction(plugin.getPlatform() == PlatformType.SPIGOT, () -> {
        }, () -> {
            final String where = "action_id IN (" + EntryAction.SHOP_OLD.id + "," + EntryAction.SHOP_OLD.idPos + ")";
            Table tableOld = EntryAction.SHOP_OLD.getTable();
            Table tableNew = EntryAction.SHOP_SGP.getTable();
            this.total = sql.count(connection, tableOld.toString(), where);
            plugin.info("Migrating " + total + " entries...");
            this.complete = 0;
            ArrayList<DbEntry> input = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            do {
                input.clear();
                try {
                    sql.getLookupManager().lookup(connection, input, tableOld, //
                            "SELECT * FROM " + tableOld + //
                                    " WHERE " + where + //
                                    " ORDER BY time ASC LIMIT 10000", new ArrayList<>());
                } catch (LookupException e) {
                    throw new SQLException(e);
                }

                ArrayList<DbEntry> output = new ArrayList<>();
                for (DbEntry entry : input) {
                    String[] parts = entry.getData().split(", ");
                    double cost = 0, balance = 0;
                    short quantity;
                    EntryAction action;
                    int target_id2 = 0;
                    try {
                        action = switch (parts[0]) {
                            case "SGP" -> EntryAction.SHOP_SGP;
                            case "CS" -> EntryAction.SHOP_CS;
                            case "DS" -> EntryAction.SHOP_DS;
                            case "ESG" -> EntryAction.SHOP_ESG;
                            default -> throw new IllegalArgumentException();
                        };
                        if (parts.length >= 3) {
                            quantity = Short.parseShort(parts[2].split(" ")[1]);

                            String valueStr = parts[1];
                            double value;
                            if (!valueStr.contains(" each")) {
                                valueStr = valueStr.replaceAll(" each", "");
                                value = Double.parseDouble(valueStr.replaceAll("[$,]", ""));
                            } else {
                                double each = Double.parseDouble(valueStr.split(" ")[0].replaceAll("[$,]", ""));
                                value = each * quantity;
                            }
                            if (value > 0) {
                                if (entry.getState()) value *= -1;
                                cost = value;
                            }
                            int balanceIndex = action == EntryAction.SHOP_CS ? 4 : 3;
                            if (parts.length > balanceIndex) {
                                balance = Double.parseDouble(parts[balanceIndex].split(" ")[1].replaceAll("[$,]", ""));
                            }
                            if (action == EntryAction.SHOP_CS) {
                                String target2 = parts[3].split(" ")[1];
                                target_id2 = sql.getUserManager().getUIDFromUsername(target2, true);
                                if (target_id2 < 0)
                                    target_id2 = sql.getUserManager().getUIDFromUUID(target2, false, true);
                            }
                        } else {
                            throw new IllegalArgumentException();
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.warning("Failed to parse entry during migration (" + e.getMessage() + "), it will be lost: " + entry);
                        if (plugin.getAPConfig().getDebug() > 0) {
                            plugin.print(e);
                        }
                        continue;
                    }
                    if (!seen.add(entry.getTime())) {
                        throw new IllegalArgumentException("Failed to delete entry " + entry);
                    }

                    output.add(new TransactionEntry(entry.getTime(), entry.getUid(), action, entry.getState(), entry.getWorld(), entry.getX(), entry.getY(), entry.getZ(), 0, 0, entry.getTargetId(), "", quantity, cost, balance, target_id2, sql));
                    this.complete++;
                }
                sql.put(connection, tableNew, output);

                sql.execute("DELETE FROM " + tableOld + " WHERE time IN (" + input.stream().map(entry -> String.valueOf(entry.getTime())).reduce((a, b) -> a + "," + b).orElse(null) + ")", connection);
            } while (!input.isEmpty());
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

    public int getComplete() {
        return complete;
    }

    public int getTotal() {
        return total;
    }

    public String getProgressString() {
        if (!isMigrating()) return null;
        if (migratingToVersion <= 0) return null;
        int progressPercentage = (int) Math.floor((double) getComplete() / getTotal() * 100);
        return String.format("Migration to v%d %d%% complete. (%d/%d). DO NOT INTERRUPT", migratingToVersion, progressPercentage, getComplete(), getTotal());
    }

    public int getOriginalVersion() {
        return originalVersion;
    }

    public int getVersion() {
        return version;
    }

    private void setVersion(int version) throws SQLException {
        sql.execute("INSERT INTO " + Table.AUXPROTECT_VERSION + " (time,version) VALUES (" + System.currentTimeMillis() + "," + (this.version = version) + ")", connection);
        plugin.info("Done migrating to version " + version);
    }

    boolean isMigrating() {
        return isMigrating;
    }

    void preTables() throws SQLException, BusyException {

        sql.execute("CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_VERSION + " (time BIGINT,version INTEGER);", connection);

        String stmt = "SELECT * FROM " + Table.AUXPROTECT_VERSION + " ORDER BY time DESC LIMIT 1";
        plugin.debug(stmt, 3);
        try (Statement statement = connection.createStatement()) {
            try (ResultSet results = statement.executeQuery(stmt)) {
                if (results.next()) version = results.getInt("version");
            }
        }

        if (sql.getVersion() < 1) {
            setVersion(TARGET_DB_VERSION);
        }

        if (version < 6) {
            plugin.warning("This database version is no longer supported. Please download AuxProtect 1.2.7 and run it first to upgrade your database, wait for migration to complete, then run this version again. https://www.spigotmc.org/resources/auxprotect.99147/download?version=509785");
            throw new SQLException();
        }

        if (sql.getVersion() < TARGET_DB_VERSION) {
            plugin.info("Outdated DB Version: " + sql.getVersion() + ". Migrating to version: " + TARGET_DB_VERSION + "...");
            plugin.info("This may take a while. Please do not interrupt.");
            isMigrating = true;
            boolean needBackup = false;
            for (int i = sql.getVersion() + 1; i <= TARGET_DB_VERSION; i++) {
                MigrationAction action = migrationActions.get(i);
                if (action.necessary) {
                    needBackup = true;
                    break;
                }
            }
            if (!sql.isMySQL() && needBackup) {
                String path = sql.backup();
                if (path != null) plugin.info("Pre-migration database backup created: " + path);
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

    void postTables() throws SQLException, BusyException {
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
            sql.execute("DROP TABLE IF EXISTS " + table.toString() + "temp;", connection);
            sql.execute("DROP TABLE IF EXISTS " + table + "_temp;", connection);
        }

        if (isMigrating && !sql.isMySQL()) {
            try {
                sql.vacuum(connection);
            } catch (SQLException e) {
                plugin.warning("Error while condensing database, you can ignore this");
                plugin.print(e);
            }
        }
        isMigrating = false;
    }

    void putRaw(Table table, ArrayList<Object[]> datas) throws SQLException, ClassCastException, IndexOutOfBoundsException {
        StringBuilder stmt = new StringBuilder("INSERT INTO " + table.toString() + " ");
        final boolean hasLocation = plugin.getPlatform() == PlatformType.SPIGOT && table.hasLocation();
        final boolean hasData = table.hasData();
        final boolean hasAction = table.hasActionId();
        final boolean hasLook = table.hasLook();
        stmt.append(table.getValuesHeader(plugin.getPlatform()));
        String inc = table.getValuesTemplate(plugin.getPlatform());
        stmt.append(" VALUES");
        for (int i = 0; i < datas.size(); i++) {
            stmt.append("\n").append(inc);
            if (i + 1 == datas.size()) {
                stmt.append(";");
            } else {
                stmt.append(",");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(stmt.toString())) {

            int i = 1;
            for (Object[] data : datas) {
                int y = 0;
                try {
                    // statement.setString(i++, table);
                    statement.setLong(i++, (long) data[y++]);
                    statement.setInt(i++, (int) data[y++]);

                    if (hasAction) {
                        statement.setInt(i++, (int) data[y++]);
                    }
                    if (hasLocation) {
                        statement.setInt(i++, (int) data[y++]);
                        statement.setInt(i++, (int) data[y++]);
                        statement.setInt(i++, (int) data[y++]);
                        statement.setInt(i++, (int) data[y++]);
                    }
                    if (hasLook) {
                        statement.setInt(i++, (int) data[y++]);
                        statement.setInt(i++, (int) data[y++]);
                    }
                    if (table.hasStringTarget()) {
                        statement.setString(i++, (String) data[y++]);
                    } else {
                        statement.setInt(i++, (int) data[y++]);
                    }
                    if (hasData) {
                        statement.setString(i++, (String) data[y++]);
                    }
                } catch (Exception e) {
                    StringBuilder error = new StringBuilder();
                    for (Object o : data) {
                        error.append(o).append(", ");
                    }
                    plugin.warning(error + "\nError at index " + y);
                    throw e;
                }
            }

            statement.executeUpdate();
        }
        sql.rowcount += datas.size();
    }

    private void putBlobsV6_insert(HashMap<Long, byte[]> blobsToLog) throws SQLException {
        StringBuilder stmt = new StringBuilder("INSERT INTO " + Table.AUXPROTECT_INVBLOB + " (time, `blob`) VALUES ");
        stmt.append("\n(?, ?),".repeat(blobsToLog.size()));

        try (PreparedStatement statement = connection.prepareStatement(stmt.substring(0, stmt.length() - 1))) {
            int i = 1;
            for (Map.Entry<Long, byte[]> entry : blobsToLog.entrySet()) {
                plugin.debug("blob: " + entry.getKey(), 5);
                statement.setLong(i++, entry.getKey());
                sql.setBlob(connection, statement, i++, entry.getValue());
            }

            statement.executeUpdate();
        }
    }

    private void tryExecute(String stmt) {
        try {
            sql.execute(stmt, connection);
        } catch (SQLException e) {
            plugin.warning("Error, if you are reattempting migration this is expected.");
            plugin.print(e);
        }
    }


    @FunctionalInterface
    interface MigrateRunnable {
        void run() throws SQLException, BusyException;
    }

    private record MigrationAction(boolean necessary, @Nullable MigrateRunnable preTableAction,
                                   @Nullable MigrateRunnable postTableAction) {
    }
}
