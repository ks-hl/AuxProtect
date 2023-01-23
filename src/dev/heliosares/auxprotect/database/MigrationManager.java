package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.utils.InvSerialization;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import javax.annotation.Nullable;
import java.sql.*;
import java.util.*;

public class MigrationManager {
    public static final int TARGET_DB_VERSION = 10;
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

    MigrationManager(SQLManager sql, Connection connection, IAuxProtect plugin) {
        this.sql = sql;
        this.plugin = plugin;
        this.connection = connection;
        HashMap<Integer, MigrationAction> migrationActions = new HashMap<>();

        //
        // 2
        //

        migrationActions.put(2, new MigrationAction(plugin.getPlatform() == PlatformType.SPIGOT, () -> {
            plugin.info("Migrating database to v2");
            tryExecute("ALTER TABLE worlds RENAME TO auxprotect_worlds;");
        }, null));

        //
        // 3
        //

        final Table[][] migrateTablesV3 = {new Table[]{Table.AUXPROTECT_MAIN, Table.AUXPROTECT_SPAM, Table.AUXPROTECT_LONGTERM, Table.AUXPROTECT_INVENTORY}};
        migrationActions.put(3, new MigrationAction(true, () -> {
            if (plugin.getPlatform() == PlatformType.BUNGEE) {
                migrateTablesV3[0] = new Table[]{Table.AUXPROTECT_MAIN, Table.AUXPROTECT_LONGTERM};
            }
            plugin.info("Migrating database to v3. DO NOT INTERRUPT");
            for (Table table : migrateTablesV3[0]) {
                tryExecute("ALTER TABLE " + table.toString() + " RENAME TO " + table + "_temp;");
                plugin.info(".");
            }
            plugin.info("Tables renamed");
        }, () -> {
            for (Table table : migrateTablesV3[0]) {
                total += sql.count(table + "_temp");
            }
            if (plugin.getPlatform() == PlatformType.BUNGEE) {
                migrateTablesV3[0] = new Table[]{Table.AUXPROTECT_MAIN, Table.AUXPROTECT_LONGTERM};
            }
            plugin.info("Merging data into new tables...");

            for (Table table : migrateTablesV3[0]) {
                ArrayList<Object[]> output = new ArrayList<>();
                ArrayList<Object[]> commands = new ArrayList<>();
                final boolean hasLocation = plugin.getPlatform() == PlatformType.SPIGOT && table.hasLocation();
                final boolean hasData = table.hasData();
                final boolean hasStringTarget = table.hasStringTarget();
                plugin.info("Merging table: " + table);
                String stmt = "SELECT * FROM " + table + "_temp;";
                plugin.debug(stmt, 3);
                try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
                    pstmt.setFetchSize(500);
                    try (ResultSet results = pstmt.executeQuery()) {
                        while (results.next()) {
                            ArrayList<Object> entry = new ArrayList<>();
                            entry.add(results.getLong("time"));
                            entry.add(sql.getUserManager().getUIDFromUUID(results.getString("user"), true));
                            int action_id = results.getInt("action_id");
                            if (action_id != 260) {
                                entry.add(action_id);
                            }
                            if (hasLocation) {
                                entry.add(results.getInt("world_id"));
                                entry.add(results.getInt("x"));
                                entry.add(results.getInt("y"));
                                entry.add(results.getInt("z"));
                            }
                            String target = results.getString("target");
                            if (hasStringTarget || action_id == 260) {
                                entry.add(target);
                            } else {
                                entry.add(sql.getUserManager().getUIDFromUUID(target, true));
                            }
                            if (hasData) {
                                entry.add(results.getString("data"));
                            }

                            if (action_id == 260) {
                                commands.add(entry.toArray(new Object[0]));
                            } else {
                                output.add(entry.toArray(new Object[0]));
                            }
                            if (output.size() >= 5000) {
                                putRaw(table, output);
                                output.clear();
                            }
                            if (commands.size() >= 5000) {
                                putRaw(Table.AUXPROTECT_COMMANDS, commands);
                                commands.clear();
                            }
                            complete++;
                        }
                    }
                }
                if (output.size() > 0) {
                    putRaw(table, output);
                }
                if (commands.size() > 0) {
                    putRaw(Table.AUXPROTECT_COMMANDS, commands);
                }
            }
        }));

        //
        // 4
        //

        migrationActions.put(4, new MigrationAction(true, null, () -> {
            plugin.info("Migrating database to v4. DO NOT INTERRUPT");
            if (plugin.getPlatform() == PlatformType.SPIGOT) {
                ArrayList<Object[]> output = new ArrayList<>();
                String stmt = "SELECT * FROM " + Table.AUXPROTECT_SPAM + " WHERE action_id = 256;";
                plugin.debug(stmt, 3);
                try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
                    pstmt.setFetchSize(500);
                    try (ResultSet results = pstmt.executeQuery()) {
                        while (results.next()) {
                            ArrayList<Object> entry = new ArrayList<>();
                            entry.add(results.getLong("time"));
                            entry.add(results.getInt("uid"));
                            entry.add(EntryAction.POS.id);
                            entry.add(results.getInt("world_id"));
                            entry.add(results.getInt("x"));
                            entry.add(results.getInt("y"));
                            entry.add(results.getInt("z"));
                            String data = results.getString("data");

                            try {
                                String[] parts = data.split("[^\\d-]+");
                                entry.add(Integer.parseInt(parts[2]));
                                entry.add(Integer.parseInt(parts[1]));
                            } catch (Exception e) {
                                plugin.print(e);
                            }

                            entry.add(results.getInt("target_id"));
                            output.add(entry.toArray(new Object[0]));

                            if (output.size() >= 5000) {
                                putRaw(Table.AUXPROTECT_POSITION, output);
                                output.clear();
                            }
                        }
                    }
                }
                if (output.size() > 0) {
                    putRaw(Table.AUXPROTECT_POSITION, output);
                }
                plugin.info("Deleting old entries.");
                sql.execute("DELETE FROM " + Table.AUXPROTECT_SPAM + " WHERE action_id = 256;", connection);
            }
        }));

        //
        // 5
        //

        migrationActions.put(5, new MigrationAction(true, () -> tryExecute("ALTER TABLE " + sql.getTablePrefix() + "auxprotect RENAME TO "
                + Table.AUXPROTECT_MAIN), null));

        //
        // 6
        //

        migrationActions.put(6, new MigrationAction(plugin.getPlatform() == PlatformType.SPIGOT, null, () -> {
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVENTORY + " ADD COLUMN hasblob BOOL");

            if (!plugin.getAPConfig().doSkipV6Migration()) {
                plugin.info("Skipping v6 migration, will migrate in place");

                total = sql.count(Table.AUXPROTECT_INVENTORY);

                String stmt = "SELECT time, action_id, data FROM " + Table.AUXPROTECT_INVENTORY
                        + " WHERE (action_id=1024 OR data LIKE '%,ITEM,%') AND (hasblob!=TRUE OR hasblob IS NULL) LIMIT ";

                plugin.debug(stmt, 3);

                boolean any = true;
                int limit = 1;
                while (any) {
                    any = false;
                    HashMap<Long, byte[]> blobs = new HashMap<>();
                    try (PreparedStatement pstmt = connection.prepareStatement(stmt + limit)) {
                        if (limit < 50)
                            limit++; // Slowly ramps up the speed to allow multiple attempts if large blobs are an
                        // issue
                        pstmt.setFetchSize(500);
                        try (ResultSet results = pstmt.executeQuery()) {
                            while (results.next()) {
                                any = true;
                                complete++;
                                long time = results.getLong("time");
                                String data = results.getString("data");
                                int action_id = results.getInt("action_id");
                                boolean hasblob = false;
                                if (data.contains(",ITEM,")) {
                                    data = data.substring(data.indexOf(",ITEM,")
                                            + ",ITEM,".length());
                                    hasblob = true;
                                }
                                byte[] blob = null;
                                try {
                                    if (action_id == EntryAction.INVENTORY.id) {
                                        try {
                                            blob = InvSerialization.playerToByteArray(InvSerialization.toPlayer(data));
                                        } catch (Exception e) {
                                            plugin.warning("THIS IS PROBABLY FINE. Failed to migrate inventory log at "
                                                    + time
                                                    + "e. This can be ignored, but this entry will no longer be available.");
                                        }
                                    } else {
                                        if (!hasblob) {
                                            continue;
                                        }
                                        blob = Base64Coder.decodeLines(data);
                                    }
                                } catch (IllegalArgumentException e) {
                                    plugin.info("Error while decoding: " + data);
                                    continue;
                                }
                                if (blob == null || blob.length == 0) {
                                    continue;
                                }

                                blobs.put(time, blob);
                            }
                        }
                    }
                    plugin.debug("Logging " + blobs.size() + " blobs");
                    HashMap<Long, byte[]> subBlobs = new HashMap<>();
                    int size = 0;
                    for (Map.Entry<Long, byte[]> entry : blobs.entrySet()) {
                        if (entry.getValue() == null || entry.getValue().length == 0) {
                            continue;
                        }
                        if (size + entry.getValue().length > 16777215 || subBlobs.size() >= 1000) {
                            if (subBlobs.size() == 0) {
                                plugin.warning("Blob too big. Skipping. " + entry.getKey() + "e");
                                continue;
                            }
                            plugin.debug("Logging " + subBlobs.size() + " blobs, " + SQLManager.getBlobSize(size));
                            putBlobsV6_insert(subBlobs);
                            subBlobs.clear();
                            size = 0;
                        }
                        size += entry.getValue().length;
                        subBlobs.put(entry.getKey(), entry.getValue());
                    }
                    if (!subBlobs.isEmpty()) {
                        plugin.debug("Logging " + subBlobs.size() + " blobs, " + SQLManager.getBlobSize(size));
                        putBlobsV6_insert(subBlobs);
                    }

                    StringBuilder where = new StringBuilder();
                    if (blobs.size() > 0) {
                        where.append(" WHERE time IN (");
                        for (Long time : blobs.keySet()) {
                            where.append(time).append(",");
                        }
                        where = new StringBuilder(where.substring(0, where.length() - 1) + ")");
                    }
                    sql.execute("UPDATE " + Table.AUXPROTECT_INVENTORY + " SET hasblob=1" + where, connection);
                }
                plugin.info("Done migrating blobs, purging unneeded data");
                sql.execute("UPDATE " + Table.AUXPROTECT_INVENTORY + " SET data = '' where hasblob=true;", connection
                );
            }
        }));

        //
        // 7
        //

        migrationActions.put(7, new MigrationAction(plugin.getPlatform() == PlatformType.SPIGOT, null, () -> {

            total = sql.count(Table.AUXPROTECT_INVDIFFBLOB);

            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVDIFFBLOB + " ADD COLUMN hash INT");

            String stmt = "SELECT blobid, ablob FROM " + Table.AUXPROTECT_INVDIFFBLOB
                    + " WHERE (hash IS NULL) LIMIT ";

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

        migrationActions.put(10, new MigrationAction(plugin.getPlatform() == PlatformType.SPIGOT,
                () -> {
                    try {
                        sql.execute("ALTER TABLE " + Table.AUXPROTECT_LASTS + " RENAME COLUMN `key` TO name", connection);
                    } catch (SQLException ignored) {
                        // This may error if migrating from a version where the `lasts` table has not yet been created, as this is executed pre-tables
                    }
                }, () -> {
        })); //This was a poor naming choice as it conflicts with the phrase `KEY`

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
        sql.execute("INSERT INTO " + Table.AUXPROTECT_VERSION + " (time,version) VALUES ("
                + System.currentTimeMillis() + "," + (this.version = version) + ")", connection);
        plugin.info("Done migrating to version " + version);
    }

    boolean isMigrating() {
        return isMigrating;
    }

    void preTables() throws SQLException {
        sql.execute("CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_VERSION + " (time BIGINT,version INTEGER);", connection);

        String stmt = "SELECT * FROM " + Table.AUXPROTECT_VERSION;
        plugin.debug(stmt, 3);
        try (Statement statement = connection.createStatement()) {
            try (ResultSet results = statement.executeQuery(stmt)) {
                long newestVersionTime = 0;
                long oldestVersionTime = Long.MAX_VALUE;
                while (results.next()) {
                    long versionTime_ = results.getLong("time");
                    int version_ = results.getInt("version");
                    if (versionTime_ > newestVersionTime) {
                        version = version_;
                        newestVersionTime = versionTime_;
                    }
                    if (versionTime_ < oldestVersionTime) {
                        originalVersion = version_;
                        oldestVersionTime = versionTime_;
                    }
                    plugin.debug("Version at " + versionTime_ + " was v" + version_ + ".", 1);
                }
            }
        }

        if (sql.getVersion() < 1) {
            setVersion(TARGET_DB_VERSION);
        }

        if (sql.getVersion() < TARGET_DB_VERSION) {
            plugin.info("Outdated DB Version: " + sql.getVersion() + ". Migrating to version: " + TARGET_DB_VERSION
                    + "...");
            plugin.info("This may take a while. Please do not interrupt.");
            isMigrating = true;
            if (!sql.isMySQL()) {
                plugin.info("Pre-migration database backup created: " + sql.backup());
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
            sql.execute("DROP TABLE IF EXISTS " + table.toString() + "temp;", connection);
            sql.execute("DROP TABLE IF EXISTS " + table + "_temp;", connection);
        }

        if (isMigrating && !sql.isMySQL()) {
            try {
                sql.vacuum();
            } catch (SQLException e) {
                plugin.warning("Error while condensing database, you can ignore this");
                plugin.print(e);
            }
        }
        isMigrating = false;
    }

    void putRaw(Table table, ArrayList<Object[]> datas)
            throws SQLException, ClassCastException, IndexOutOfBoundsException {
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
        void run() throws SQLException;
    }

    private record MigrationAction(boolean necessary, @Nullable MigrateRunnable preTableAction,
                                   @Nullable MigrateRunnable postTableAction) {
    }
}
