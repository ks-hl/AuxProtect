package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.utils.InvSerialization;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

public class MigrationManager {
    public static final int DBVERSION = 9;
    private final SQLManager sql;
    private final Connection connection;
    private final IAuxProtect plugin;
    private boolean isMigrating;
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

    private static class MigrationAction {
        private final boolean backup;

        public MigrationAction(boolean backup, Consumer<Connection> preTableAction, Consumer<Connection> postTableAction) {

            this.backup = backup;
        }
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
            sql.migrationmanager.setVersion(MigrationManager.DBVERSION);
        }

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
            tryExecute("ALTER TABLE " + SQLManager.getTablePrefix() + "auxprotect RENAME TO "
                    + Table.AUXPROTECT_MAIN);
        }

        if (sql.getVersion() < 2 && plugin.getPlatform() == PlatformType.SPIGOT) {
            plugin.info("Migrating database to v2");
            tryExecute("ALTER TABLE worlds RENAME TO auxprotect_worlds;");

            sql.migrationmanager.setVersion(2);
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
            sql.migrationmanager.setVersion(5);
        }

        if (sql.getVersion() < 6) {
            migrateToV6();
        }

        if (sql.getVersion() < 7) {
            migrateToV7();
        }

        if (sql.getVersion() < 8) {
            migrateToV8();
        }

        if (sql.getVersion() < 9) {
            migrateToV9();
        }

        /*
         * This should never be reached and is only here as a failsafe
         */
        if (sql.getVersion() < DBVERSION) {
            plugin.warning("No handling for upgrade: " + this.getVersion() + "->" + DBVERSION);
            sql.migrationmanager.setVersion(DBVERSION);
        }

        plugin.debug("Purging temporary tables");
        for (Table table : Table.values()) {
            sql.execute(connection, "DROP TABLE IF EXISTS " + table.toString() + "temp;");
            sql.execute(connection, "DROP TABLE IF EXISTS " + table + "_temp;");
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

    int migrateToV3Part1() throws SQLException {
        Table[] migrateTablesV3 = new Table[]{Table.AUXPROTECT_MAIN, Table.AUXPROTECT_SPAM, Table.AUXPROTECT_LONGTERM,
                Table.AUXPROTECT_ABANDONED, Table.AUXPROTECT_INVENTORY};
        if (plugin.getPlatform() == PlatformType.BUNGEE) {
            migrateTablesV3 = new Table[]{Table.AUXPROTECT_MAIN, Table.AUXPROTECT_LONGTERM};
        }
        int rowcountformerge = 0;
        plugin.info("Migrating database to v3. DO NOT INTERRUPT");
        for (Table table : migrateTablesV3) {
            tryExecute("ALTER TABLE " + table.toString() + " RENAME TO " + table + "_temp;");
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

        sql.migrationmanager.setVersion(3);
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
        sql.migrationmanager.setVersion(4);
    }

    @SuppressWarnings("deprecation")
    void migrateToV6() throws SQLException {
        if (plugin.getPlatform() == PlatformType.SPIGOT) {
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVENTORY + " ADD COLUMN hasblob BOOL");

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
                        if (limit < 50)
                            limit++; // Slowly ramps up the speed to allow multiple attempts if large blobs are an
                        // issue
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
        sql.migrationmanager.setVersion(6);
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
        putBlobsV6(blobs);

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

    private void putBlobsV6(HashMap<Long, byte[]> blobsToLog) throws SQLException {
        plugin.debug("Logging " + blobsToLog.size() + " blobs");
        HashMap<Long, byte[]> subBlobs = new HashMap<>();
        int size = 0;
        for (Map.Entry<Long, byte[]> entry : blobsToLog.entrySet()) {
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
    }

    private void putBlobsV6_insert(HashMap<Long, byte[]> blobsToLog) throws SQLException {
        String stmt = "INSERT INTO " + Table.AUXPROTECT_INVBLOB + " (time, `blob`) VALUES ";
        for (int i = 0; i < blobsToLog.size(); i++) {
            stmt += "\n(?, ?),";
        }

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


    void migrateToV7() throws SQLException {
        if (plugin.getPlatform() == PlatformType.SPIGOT) {

            final int totalrows = sql.count(Table.AUXPROTECT_INVDIFFBLOB);
            long lastupdate = 0;
            int count = 0;

            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVDIFFBLOB + " ADD COLUMN hash INT");

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
        }
        sql.migrationmanager.setVersion(7);
    }

    void migrateToV8() throws SQLException {
        if (plugin.getPlatform() == PlatformType.SPIGOT) {

            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVBLOB + " RENAME COLUMN time TO blobid");
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVBLOB + " RENAME COLUMN `blob` TO ablob"); //This was a poor naming choice as it conflicts with the datatype
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVBLOB + " ADD COLUMN hash INT");
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVENTORY + " ADD COLUMN blobid BIGINT");
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVENTORY + " ADD COLUMN damage INT");
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_INVENTORY + " ADD COLUMN qty INT");
            sql.execute(connection, "UPDATE " + Table.AUXPROTECT_INVENTORY + " SET blobid=time WHERE hasblob=1");
//            sql.execute(connection, "UPDATE " + Table.AUXPROTECT_INVBLOB + " SET hash=0 WHERE blobid IN (SELECT blobid FROM " +
//                    Table.AUXPROTECT_INVENTORY + " WHERE action_id=" + EntryAction.INVENTORY.id + ")");
//            computeV8Hashes(sql, plugin);
            //TODO is it worth dropping this column?
            sql.execute(connection, "UPDATE " + Table.AUXPROTECT_INVENTORY + " SET hasblob=null");
        }
        sql.migrationmanager.setVersion(8);
    }

    public static void computeV8Hashes(SQLManager sql, IAuxProtect plugin) throws SQLException {
        final int totalrows;
        Connection connection = sql.getConnection(true);
        try {
            try (PreparedStatement stmt = connection.prepareStatement(sql.getCountStmt(Table.AUXPROTECT_INVBLOB.toString()) + " WHERE (hash IS NULL)")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalrows = rs.getInt(1);
                    } else {
                        plugin.info("No rows to hash.");
                        return;
                    }
                }
            }
        } finally {
            sql.returnConnection(connection);
        }
        long lastupdate = 0;
        int count = 0;

        String stmt = "SELECT blobid, ablob FROM " + Table.AUXPROTECT_INVBLOB
                + " WHERE (hash IS NULL) LIMIT ";

        plugin.debug(stmt, 3);

        plugin.info("Hashing beginnning. " + totalrows + " remaining");
        boolean any = true;
        Set<Long> ignore = new HashSet<>();
        int limit = 50;
        while (any) {
            any = false;
            HashMap<Long, Integer> hashes = new HashMap<>();
            connection = sql.getConnection(true);
            try (PreparedStatement pstmt = connection.prepareStatement(stmt + (limit + ignore.size()))) {
                pstmt.setFetchSize(500);
                try (ResultSet results = pstmt.executeQuery()) {
                    while (results.next()) {
                        long blobid = results.getLong("blobid");
                        if (ignore.contains(blobid)) continue;
                        any = true;
                        int progress = (int) Math.round((double) count / (double) totalrows * 100.0);
                        if (System.currentTimeMillis() - lastupdate > 30000) {
                            lastupdate = System.currentTimeMillis();
                            plugin.info("Hashing inventory data. " + progress + "% complete. (" + count + "/" + totalrows
                                    + ") This can be ignored and/or interrupted.");
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
            } finally {
                sql.returnConnection(connection);
            }
            hashes.forEach((k, v) -> {
                try {
                    sql.executeWrite("UPDATE " + Table.AUXPROTECT_INVBLOB + " SET hash=? WHERE blobid=?", v, k);
                } catch (SQLException e) {
                    plugin.warning("Error while committing hash for blobid: " + k + ", this is probably fine.");
                    ignore.add(k);
                }
            });
        }
    }


    void migrateToV9() throws SQLException {
        if (plugin.getPlatform() == PlatformType.SPIGOT) {
            tryExecute("ALTER TABLE " + Table.AUXPROTECT_POSITION + " ADD COLUMN ablob BLOB");
        }
        sql.migrationmanager.setVersion(9);
    }

    private void setVersion(int version) throws SQLException {
        sql.execute(connection, "INSERT INTO " + Table.AUXPROTECT_VERSION + " (time,version) VALUES ("
                + System.currentTimeMillis() + "," + (this.version = version) + ")");
        plugin.info("Done migrating to version " + version);
    }

    private boolean tryExecute(String stmt) {
        try {
            sql.execute(connection, stmt);
        } catch (SQLException e) {
            plugin.warning("Error, if you are reattempting migration this is expected.");
            plugin.print(e);
            return false;
        }
        return true;
    }
}
