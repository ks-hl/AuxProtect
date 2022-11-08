package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.utils.InvSerialization;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.IOException;
import java.sql.*;
import java.util.*;

public class MigrationManager {
    public static final int DBVERSION = 7;
    private final SQLManager sql;
    private final Connection connection;
    private final IAuxProtect plugin;
    private boolean isMigrating;
    private int preMigrateDebug;
    private int version;
    private int rowcountformerge;
    private int originalVersion;

    MigrationManager(SQLManager sql, Connection connection, IAuxProtect plugin) {
        this.sql = sql;
        this.plugin = plugin;
        this.connection = connection;
    }

    public int getOriginalVersion() {
        return originalVersion;
    }

    public int getVersion() {
        return version;
    }

    boolean isMigrating() {
        return isMigrating;
    }

    void preTables() throws SQLException, IOException {
        sql.execute(connection,
                "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_VERSION + " (time BIGINT,version INTEGER);");

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
            sql.migrationmanager.setVersion(connection, MigrationManager.DBVERSION, sql);
        }

        preMigrateDebug = -1;
        if (sql.getVersion() < DBVERSION) {
            plugin.info("Outdated DB Version: " + sql.getVersion() + ". Migrating to version: " + DBVERSION
                    + "...");
            plugin.info("This may take a while. Please do not interrupt.");
//			if (plugin.getDebug() < 3) {
//				plugin.info("Enabling debug mode during migration");
//				preMigrateDebug = plugin.getDebug();
//				plugin.setDebug(3);
//			}
            isMigrating = true;
            if (!sql.isMySQL()) {
                plugin.info("Pre-migration database backup created: " + sql.backup());
            }
        }

        if (sql.getVersion() < 5) {
            try {
                sql.execute(connection, "ALTER TABLE " + SQLManager.getTablePrefix() + "auxprotect RENAME TO "
                        + Table.AUXPROTECT_MAIN);
            } catch (SQLException ignored) {
                plugin.warning(
                        "Failed to rename auxprotect table for migration. This may cause errors. Migration continuing.");
            }
        }

        if (sql.getVersion() < 2 && plugin.getPlatform() == PlatformType.SPIGOT) {
            plugin.info("Migrating database to v2");
            sql.execute(connection, "ALTER TABLE worlds RENAME TO auxprotect_worlds;");

            sql.migrationmanager.setVersion(connection, 2, sql);
        }

        if (sql.getVersion() < 3) {
            rowcountformerge = migrateToV3Part1();
        }
    }

    void postTables() throws SQLException {
        if (sql.getVersion() < 3) {
            migrateToV3Part2();
        }

        if (sql.getVersion() < 4) {
            migrateToV4();
        }

        if (sql.getVersion() < 5) {
            sql.migrationmanager.setVersion(connection, 5, sql);
        }

        if (sql.getVersion() < 6) {
            migrateToV6();
        }

        if (sql.getVersion() < 7) {
            migrateToV7();
        }

        /*
         * This should never be reached and is only here as a fail safe
         */
        if (sql.getVersion() < DBVERSION) {
            sql.migrationmanager.setVersion(connection, DBVERSION, sql);
        }

        plugin.debug("Purging temporary tables");
        for (Table table : Table.values()) {
            sql.execute(connection, "DROP TABLE IF EXISTS " + table.toString() + "temp;");
            sql.execute(connection, "DROP TABLE IF EXISTS " + table + "_temp;");
        }

        if (preMigrateDebug >= 0) {
            try {
                plugin.getAPConfig().setDebug(preMigrateDebug);
            } catch (IOException ignored) {
            }
            plugin.info("Debug mode restored to " + preMigrateDebug);
        }
        isMigrating = false;
    }

    int migrateToV3Part1() throws SQLException {
        Table[] migrateTablesV3 = new Table[]{Table.AUXPROTECT_MAIN, Table.AUXPROTECT_SPAM, Table.AUXPROTECT_LONGTERM,
                Table.AUXPROTECT_ABANDONED, Table.AUXPROTECT_INVENTORY};
        if (plugin.getPlatform() == PlatformType.BUNGEE) {
            migrateTablesV3 = new Table[]{Table.AUXPROTECT_MAIN, Table.AUXPROTECT_LONGTERM};
        }
        int rowcountformerge = 0;
        plugin.info("Migrating database to v3. DO NOT INTERRUPT");
        for (Table table : migrateTablesV3) {
            try {
                sql.execute(connection,
                        "ALTER TABLE " + table.toString() + " RENAME TO " + table + "_temp;");
            } catch (Exception ignored) {
                plugin.warning("Error renaming table, continuing anyway. This may cause errors.");
            }
            rowcountformerge += sql.count(table + "_temp");
            plugin.info(".");
        }
        plugin.info("Tables renamed");
        return rowcountformerge;
    }

    void migrateToV3Part2() throws SQLException {
        Table[] migrateTablesV3 = new Table[]{Table.AUXPROTECT_MAIN, Table.AUXPROTECT_SPAM, Table.AUXPROTECT_LONGTERM,
                Table.AUXPROTECT_ABANDONED, Table.AUXPROTECT_INVENTORY};
        if (plugin.getPlatform() == PlatformType.BUNGEE) {
            migrateTablesV3 = new Table[]{Table.AUXPROTECT_MAIN, Table.AUXPROTECT_LONGTERM};
        }
        plugin.info("Merging data into new tables...");
        int progress = 0;
        int count = 0;

        for (Table table : migrateTablesV3) {
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
                        count++;
                        int progressPercentage = (int) Math.floor((double) count / rowcountformerge * 100);
                        if (progressPercentage / 5 > progress) {
                            progress = progressPercentage / 5;
                            plugin.info("Migration " + progress * 5 + "% complete. (" + count + "/" + rowcountformerge
                                    + "). DO NOT INTERRUPT");
                        }
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

        sql.migrationmanager.setVersion(connection, 3, sql);
    }

    void migrateToV4() throws SQLException {
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
            sql.execute(connection, "DELETE FROM " + Table.AUXPROTECT_SPAM + " WHERE action_id = 256;");
        }
        sql.migrationmanager.setVersion(connection, 4, sql);
    }

    @SuppressWarnings("deprecation")
    void migrateToV6() throws SQLException {
        if (plugin.getPlatform() == PlatformType.SPIGOT) {
            try {
                sql.execute(connection,
                        "ALTER TABLE " + Table.AUXPROTECT_INVENTORY + " ADD COLUMN hasblob BOOL");
            } catch (SQLException e) {
                plugin.warning(
                        "Error while modifying inventory table. This is probably due to a prior failed migration. You can ignore this if there are no further errors.");
            }

            if (!plugin.getAPConfig().doSkipV6Migration()) {
                plugin.info("Skipping v6 migration, will migrate in place");

                final int totalrows = sql.count(Table.AUXPROTECT_INVENTORY);
                long lastupdate = 0;
                int count = 0;

                String stmt = "SELECT time, action_id, data FROM " + Table.AUXPROTECT_INVENTORY
                        + " WHERE (action_id=1024 OR data LIKE '%" + InvSerialization.ITEM_SEPARATOR
                        + "%') AND (hasblob!=TRUE OR hasblob IS NULL) LIMIT ";

                plugin.debug(stmt, 3);

                plugin.info("Migration beginnning. (0/" + totalrows + "). ***DO NOT INTERRUPT***");
                boolean any = true;
                while (any) {
                    any = false;
                    HashMap<Long, byte[]> blobs = new HashMap<>();
                    int limit = 1;
                    try (PreparedStatement pstmt = connection.prepareStatement(stmt + limit)) {
                        if (limit < 50) {
                            limit++; // Slowly ramps up the speed to allow multiple attempts if large blobs are an
                            // issue
                        }
                        pstmt.setFetchSize(500);
                        try (ResultSet results = pstmt.executeQuery()) {
                            while (results.next()) {
                                any = true;
                                int progress = (int) Math.round((double) count / (double) totalrows * 100.0);
                                if (System.currentTimeMillis() - lastupdate > 5000) {
                                    lastupdate = System.currentTimeMillis();
                                    plugin.info("Migration " + progress + "% complete. (" + count + "/" + totalrows
                                            + "). DO NOT INTERRUPT");
                                }
                                count++;
                                long time = results.getLong("time");
                                String data = results.getString("data");
                                int action_id = results.getInt("action_id");
                                boolean hasblob = false;
                                if (data.contains(InvSerialization.ITEM_SEPARATOR)) {
                                    data = data.substring(data.indexOf(InvSerialization.ITEM_SEPARATOR)
                                            + InvSerialization.ITEM_SEPARATOR.length());
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
                    migrateV6Commit(blobs);
                }
                plugin.info("Done migrating blobs, purging unneeded data");
                sql.execute(connection,
                        "UPDATE " + Table.AUXPROTECT_INVENTORY + " SET data = '' where hasblob=true;");
            }
        }
        sql.migrationmanager.setVersion(connection, 6, sql);
    }

    void migrateToV7() throws SQLException {
        if (plugin.getPlatform() == PlatformType.SPIGOT) {

            final int totalrows = sql.count(Table.AUXPROTECT_INVDIFFBLOB);
            long lastupdate = 0;
            int count = 0;

            try {
                sql.execute(connection, "ALTER TABLE " + Table.AUXPROTECT_INVDIFFBLOB + " ADD COLUMN hash INT");
            } catch (SQLException e) {
                plugin.warning("Failed to alter table, if you are reattempting migration this is expected.");
            }

            String stmt = "SELECT blobid, ablob FROM " + Table.AUXPROTECT_INVDIFFBLOB
                    + " WHERE (hash IS NULL) LIMIT ";

            plugin.debug(stmt, 3);

            plugin.info("Migration beginnning. (0/" + totalrows + "). ***DO NOT INTERRUPT***");
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
                            int progress = (int) Math.round((double) count / (double) totalrows * 100.0);
                            if (System.currentTimeMillis() - lastupdate > 5000) {
                                lastupdate = System.currentTimeMillis();
                                plugin.info("Migration " + progress + "% complete. (" + count + "/" + totalrows
                                        + "). DO NOT INTERRUPT");
                            }
                            count++;
                            byte[] blob = null;
                            try {
                                blob = sql.getBlob(results, "ablob");
                            } catch (IOException e) {
                                plugin.warning("Failed to get blob for blobid: " + blobid + ". This is probably fine.");
                                ignore.add(blobid);
                            }
                            hashes.put(blobid, Arrays.hashCode(blob));
                        }
                    }
                }
                hashes.forEach((k, v) -> {
                    try {
                        sql.executeWrite(connection, "UPDATE " + Table.AUXPROTECT_INVDIFFBLOB + " SET hash=? WHERE blobid=?", v, k);
                    } catch (SQLException e) {
                        plugin.warning("Error while committing hash for blobid: " + k + ", this is probably fine.");
                        ignore.add(k);
                    }
                });
            }
            plugin.info("Done migrating blobs, purging unneeded data");
        }
        sql.migrationmanager.setVersion(connection, 7, sql);
    }

    void putRaw(Table table, ArrayList<Object[]> datas)
            throws SQLException, ClassCastException, IndexOutOfBoundsException {
        String stmt = "INSERT INTO " + table.toString() + " ";
        final boolean hasLocation = plugin.getPlatform() == PlatformType.SPIGOT && table.hasLocation();
        final boolean hasData = table.hasData();
        final boolean hasAction = table.hasActionId();
        final boolean hasLook = table.hasLook();
        stmt += table.getValuesHeader(plugin.getPlatform());
        String inc = table.getValuesTemplate(plugin.getPlatform());
        stmt += " VALUES";
        for (int i = 0; i < datas.size(); i++) {
            stmt += "\n" + inc;
            if (i + 1 == datas.size()) {
                stmt += ";";
            } else {
                stmt += ",";
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(stmt)) {

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
                    String error = "";
                    for (Object o : data) {
                        error += o + ", ";
                    }
                    plugin.warning(error + "\nError at index " + y);
                    throw e;
                }
            }

            statement.executeUpdate();
        }
        sql.rowcount += datas.size();
    }

    private void migrateV6Commit(HashMap<Long, byte[]> blobs) throws SQLException {
        sql.putBlobs(blobs);

        String where = "";
        if (blobs.size() > 0) {
            where += " WHERE time IN (";
            for (Long time : blobs.keySet()) {
                where += time + ",";
            }
            where = where.substring(0, where.length() - 1) + ")";
        }
        sql.execute(connection, "UPDATE " + Table.AUXPROTECT_INVENTORY + " SET hasblob=1" + where);
    }

    private void setVersion(Connection connection, int version, SQLManager sql) throws SQLException {
        sql.execute(connection, "INSERT INTO " + Table.AUXPROTECT_VERSION + " (time,version) VALUES ("
                + System.currentTimeMillis() + "," + (this.version = version) + ")");
        plugin.info("Done migrating to version " + version);
    }
}
