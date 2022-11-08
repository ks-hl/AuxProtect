package dev.heliosares.auxprotect.database;

import com.google.common.io.Files;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.database.ConnectionPool.BusyException;
import dev.heliosares.auxprotect.exceptions.AlreadyExistsException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.towny.TownyManager;
import dev.heliosares.auxprotect.utils.InvSerialization;
import org.bukkit.Bukkit;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;

public class SQLManager {
    public static final int MAX_LOOKUP_SIZE = 500000;
    private static SQLManager instance;
    private static String tablePrefix = "";
    private final String targetString;
    private final IAuxProtect plugin;
    private final HashMap<String, Integer> worlds = new HashMap<>();
    private final File sqliteFile;
    private final LookupManager lookupmanager;
    private final InvDiffManager invdiffmanager;
    private final TownyManager townymanager;
    private final SQLUserManager usermanager;
    int rowcount;
    MigrationManager migrationmanager;
    private ConnectionPool conn;
    private boolean isConnected;
    private int nextWid;
    private int nextActionId = 10000;

    public SQLManager(IAuxProtect plugin, String target, String prefix, File sqliteFile) {
        instance = this;
        this.plugin = plugin;
        this.usermanager = new SQLUserManager(plugin, this);
        this.lookupmanager = new LookupManager(this, plugin);
        this.targetString = target;
        this.invdiffmanager = plugin.getPlatform() == PlatformType.SPIGOT ? new InvDiffManager(this, plugin) : null;
        if (prefix == null || prefix.length() == 0) {
            tablePrefix = "";
        } else {
            prefix = prefix.replaceAll(" ", "_");
            if (!prefix.endsWith("_")) {
                prefix += "_";
            }
            tablePrefix = prefix;
        }
        TownyManager tm = null;
        if (plugin.getPlatform() == PlatformType.SPIGOT) {
            try {
                tm = new TownyManager((dev.heliosares.auxprotect.spigot.AuxProtectSpigot) plugin, this);
            } catch (Throwable ignored) {
            }
        }
        this.townymanager = tm;
        this.sqliteFile = sqliteFile;
    }

    public static SQLManager getInstance() {
        return instance;
    }

    public static String getTablePrefix() {
        return tablePrefix;
    }

    public SQLUserManager getUserManager() {
        return usermanager;
    }

    public LookupManager getLookupManager() {
        return lookupmanager;
    }

    public InvDiffManager getInvDiffManager() {
        return invdiffmanager;
    }

    public TownyManager getTownyManager() {
        return townymanager;
    }

    public int getCount() {
        return rowcount;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public int getVersion() {
        return migrationmanager.getVersion();
    }

    public int getOriginalVersion() {
        return migrationmanager.getOriginalVersion();
    }

    public boolean isMySQL() {
        return conn.isMySQL();
    }

    public void connect(String user, String pass)
            throws SQLException, IOException, ClassNotFoundException {
        plugin.info("Connecting to database...");

        conn = new ConnectionPool(plugin, targetString, user, pass);
        try {
            init();
        } catch (Exception e) {
            if (migrationmanager.isMigrating()) {
                plugin.warning(
                        "Error while migrating database. This database will likely not work with the current version. You will need to restore a backup (plugins/AuxProtect/database/backups) and try again. Please contact the plugin developer if you are unable to complete migration.");
            }
            throw e;
        }

        isConnected = true;
        plugin.info("Connected!");

        // Auto Purge
        if (plugin.getAPConfig().doAutoPurge()) {
            boolean anypurge = false;
            int count = 0;
            for (Table table : Table.values()) {
                if (table.canPurge() && table.exists(plugin)) {
                    if (table.getAutoPurgeInterval() >= Table.MIN_PURGE_INTERVAL) {
                        anypurge = true;
                        plugin.info(Language.L.COMMAND__PURGE__PURGING.translate(table.toString()));
                        try {
                            count += purge(table, table.getAutoPurgeInterval());
                        } catch (Exception e) {
                            plugin.warning(Language.L.COMMAND__PURGE__ERROR.translate());
                            plugin.print(e);
                            continue;
                        }
                    }
                }
            }
            if (anypurge) {
                try {
                    plugin.info(Language.L.COMMAND__PURGE__UIDS.translate());
                    plugin.getSqlManager().purgeUIDs();

                    // TODO put this back with a last_vacuum variable
//					if (!plugin.getSqlManager().isMySQL()) {
//						plugin.info(Language.L.COMMAND__PURGE__VACUUM.translate());
//						plugin.getSqlManager().vacuum();
//					}
                } catch (SQLException e) {
                    plugin.warning(Language.L.COMMAND__PURGE__ERROR.translate());
                    plugin.print(e);
                    return;
                }
                plugin.info(Language.L.COMMAND__PURGE__COMPLETE_COUNT.translate(count));
            }
        }

        count();

        plugin.info("There are currently " + rowcount + " rows");
        if (townymanager != null) {
            townymanager.init();
        }
    }

    public void close() {
        isConnected = false;
        if (conn != null) {
            conn.close();
        }
    }

    public String backup() throws IOException, BusyException {
        if (isMySQL()) {
            return null;
        }

        Connection connection = conn.getWriteConnection(30000);
        try {
            File backup = new File(sqliteFile.getParentFile(),
                    "backups/backup-v" + migrationmanager.getVersion() + "-" + System.currentTimeMillis() + ".db");
            if (!backup.getParentFile().exists()) {
                backup.getParentFile().mkdirs();
            }
            Files.copy(sqliteFile, backup);
            return backup.getAbsolutePath();
        } finally {
            conn.returnConnection(connection);
        }
    }

    private void init() throws SQLException, IOException {

        Connection connection = conn.getWriteConnection(60000);
        try {
            synchronized (connection) {
                this.migrationmanager = new MigrationManager(this, connection, plugin);
                migrationmanager.preTables();

                for (Table table : Table.values()) {
                    if (table.hasAPEntries()) {
                        execute(connection, table.getSQLCreateString(plugin));
                    }
                }
                String stmt;
                if (plugin.getPlatform() == PlatformType.SPIGOT) {
                    stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_INVBLOB;
                    stmt += " (time BIGINT, `blob` MEDIUMBLOB);";
                    execute(connection, stmt);

                    stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_INVDIFF;
                    stmt += " (time BIGINT, uid INT, slot INT, qty INT, blobid BIGINT, damage INT);";
                    execute(connection, stmt);

                    stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_INVDIFFBLOB;
                    stmt += " (blobid BIGINT, ablob MEDIUMBLOB, hash INT);";
                    execute(connection, stmt);

                    stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_WORLDS;
                    stmt += " (name varchar(255), wid SMALLINT);";
                    execute(connection, stmt);

                    stmt = "SELECT * FROM " + Table.AUXPROTECT_WORLDS + ";";
                    plugin.debug(stmt, 3);
                    try (Statement statement = connection.createStatement()) {
                        try (ResultSet results = statement.executeQuery(stmt)) {
                            while (results.next()) {
                                String world = results.getString("name");
                                int wid = results.getInt("wid");
                                worlds.put(world, wid);
                                if (wid >= nextWid) {
                                    nextWid = wid + 1;
                                }
                            }
                        }
                    }

                }

                stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_API_ACTIONS
                        + " (name varchar(255), nid SMALLINT, pid SMALLINT, ntext varchar(255), ptext varchar(255));";
                execute(connection, stmt);

                stmt = "SELECT * FROM " + Table.AUXPROTECT_API_ACTIONS + ";";
                plugin.debug(stmt, 3);
                try (Statement statement = connection.createStatement()) {
                    try (ResultSet results = statement.executeQuery(stmt)) {
                        while (results.next()) {
                            String key = results.getString("name");
                            int nid = results.getInt("nid");
                            int pid = results.getInt("pid");
                            String ntext = results.getString("ntext");
                            String ptext = results.getString("ptext");
                            if (nid >= nextActionId) {
                                nextActionId = nid + 1;
                            }
                            if (pid >= nextActionId) {
                                nextActionId = pid + 1;
                            }
                            new EntryAction(key, nid, pid, ntext, ptext);
                        }
                    }
                }

                usermanager.init(connection);

                migrationmanager.postTables();
                if (invdiffmanager != null) {
                    invdiffmanager.init(connection);
                }

                plugin.debug("init done.");
            }
        } finally {
            returnConnection(connection);
        }
    }

    private Set<Integer> getAllDistinctUIDs(Table table) throws SQLException {
        boolean hasTargetId = !table.hasStringTarget() && table != Table.AUXPROTECT_UIDS;
        String[] columns = new String[hasTargetId ? 2 : 1];
        columns[0] = "uid";
        if (hasTargetId) {
            columns[1] = "target_id";
        }
        Set<Integer> inUseUids = new HashSet<>();
        for (String column : columns) {
            Connection connection = conn.getConnection(true);
            String stmt = "SELECT DISTINCT " + column + " FROM " + table;
            plugin.debug(stmt, 3);
            try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
                pstmt.setFetchSize(500);
                try (ResultSet results = pstmt.executeQuery()) {
                    while (results.next()) {
                        int uid = results.getInt(column);
                        inUseUids.add(uid);
                    }
                }
            }
            returnConnection(connection);
        }
        return inUseUids;
    }

    public int purgeUIDs() throws SQLException {
        Set<Integer> inUseUids = new HashSet<>();
        for (Table table : Table.values()) {
            if (!table.hasAPEntries() || !table.exists(plugin)) {
                continue;
            }

            inUseUids.addAll(getAllDistinctUIDs(table));
        }
        Set<Integer> savedUids = getAllDistinctUIDs(Table.AUXPROTECT_UIDS);
        plugin.debug(savedUids.size() + " total UIDs");
        plugin.debug(inUseUids.size() + " currently in use UIDs");
        savedUids.removeAll(inUseUids);
        plugin.debug("Purging " + savedUids.size() + " UIDs");
        int i = 0;
        int count = 0;
        final String hdr = "DELETE FROM " + Table.AUXPROTECT_UIDS + " WHERE uid IN ";
        String stmt = "";
        for (int uid : savedUids) {
            if (!stmt.isEmpty()) {
                stmt += ",";
            }
            plugin.debug("Purging UID " + uid, 5);
            stmt += uid;
            if (++i >= 1000) {
                count += executeWriteReturnRows(hdr + "(" + stmt + ")");
                stmt = "";
                i = 0;
            }
        }
        if (!stmt.isEmpty())
            count += executeWriteReturnRows(hdr + "(" + stmt + ")");
        return count;
    }

    public void vacuum() throws SQLException {
        executeWrite("VACUUM;");
    }

    public void execute(Connection connection, String stmt) throws SQLException {
        plugin.debug(stmt, 5);

        try (Statement statement = connection.createStatement()) {
            statement.execute(stmt);
        }
    }

    public void execute(String stmt, boolean wait) throws SQLException {
        Connection connection = conn.getConnection(wait);
        try {
            execute(connection, stmt);
        } finally {
            returnConnection(connection);
        }
    }

    private void prepare(Connection connection, PreparedStatement pstmt, Object... args) throws SQLException {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            Object o = args[i];
            if (o == null) {
                pstmt.setNull(i + 1, Types.NULL);
                continue;
            } else if (o instanceof String c) {
                pstmt.setString(i + 1, c);
            } else if (o instanceof Integer c) {
                pstmt.setInt(i + 1, c);
            } else if (o instanceof Long c) {
                pstmt.setLong(i + 1, c);
            } else if (o instanceof Boolean c) {
                pstmt.setBoolean(i + 1, c);
            } else if (o instanceof byte[] c) {
                setBlob(connection, pstmt, i + 1, c);
            } else {
                throw new IllegalArgumentException(o.toString());
            }
        }
    }

    /**
     * {@link PreparedStatement#execute()}
     *
     * @param stmt
     * @param args
     * @return
     * @throws SQLException
     * @throws BusyException
     */
    public boolean executeWrite(String stmt, Object... args) throws SQLException, BusyException {
        plugin.debug(stmt, 5);
        Connection connection = conn.getWriteConnection(30000);
        try {
            return executeWrite(connection, stmt, args);
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * {@link PreparedStatement#execute()}
     *
     * @param stmt
     * @param args
     * @return
     * @throws SQLException
     * @throws BusyException
     */
    public boolean executeWrite(Connection connection, String stmt, Object... args) throws SQLException {
        plugin.debug(stmt, 5);
        try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
            prepare(connection, pstmt, args);
            return pstmt.execute();
        }
    }

    /**
     * {@link PreparedStatement#executeUpdate()}
     *
     * @param stmt
     * @param args
     * @return
     * @throws SQLException
     * @throws BusyException
     */
    public int executeWriteReturnRows(String stmt, Object... args) throws SQLException, BusyException {
        plugin.debug(stmt, 5);
        Connection connection = conn.getWriteConnection(30000);
        try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
            prepare(connection, pstmt, args);
            return pstmt.executeUpdate();
        } finally {
            returnConnection(connection);
        }
    }

    public int executeWriteReturnGenerated(String stmt, Object... args) throws SQLException {
        plugin.debug(stmt, 5);
        Connection connection = conn.getWriteConnection(30000);
        try (PreparedStatement pstmt = connection.prepareStatement(stmt, Statement.RETURN_GENERATED_KEYS)) {
            prepare(connection, pstmt, args);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } finally {
            returnConnection(connection);
        }
        return -1;
    }

    public List<List<String>> executeGet(String stmt, String... args) throws SQLException {
        plugin.debug(stmt, 5);
        final List<List<String>> rowList = new LinkedList<List<String>>();

        Connection connection = getConnection(false);
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(stmt)) {
                final ResultSetMetaData meta = rs.getMetaData();
                final int columnCount = meta.getColumnCount();
                {
                    final List<String> columnList = new ArrayList<String>();
                    rowList.add(columnList);
                    for (int i = 0; i < columnCount; i++) {
                        columnList.add(meta.getColumnName(i + 1));
                    }
                }
                while (rs.next()) {
                    final List<String> columnList = new ArrayList<String>();
                    rowList.add(columnList);

                    for (int column = 1; column <= columnCount; ++column) {
                        final Object value = rs.getObject(column);
                        columnList.add(String.valueOf(value));
                    }
                }
            }
        } finally {
            returnConnection(connection);
        }
        return rowList;
    }

    /**
     * Do not use this Connection to modify the database, use
     * {@link SQLManager#executeWrite}
     * <p>
     * You MUST call {@link SQLManager#returnConnection(Connection)} when you are
     * done with this Connection
     *
     * @return returns a READ-ONLY connection to the database
     * @throws SQLException
     * @throws BusyException if the database is busy for longer than 3 seconds
     */
    public Connection getConnection(boolean wait) throws SQLException, BusyException {
        return conn.getConnection(wait);
    }

    /**
     * Called to return a connection to the pool
     *
     * @param connection a Connection obtained from {@link #getConnection(boolean)}
     */
    public void returnConnection(Connection connection) {
        conn.returnConnection(connection);
    }

    public int getConnectionPoolSize() {
        return conn.getPoolSize();
    }

    protected boolean put(Connection connection, Table table) throws SQLException {
        long start = System.nanoTime();
        int count = 0;
        List<DbEntry> entries = new ArrayList<>();

        DbEntry entry;
        while ((entry = table.queue.poll()) != null) {
            entries.add(entry);
        }
        count = entries.size();
        if (count == 0) {
            return false;
        }
        String stmt = "INSERT INTO " + table + " ";
        int numColumns = table.getNumColumns(plugin.getPlatform());
        String inc = Table.getValuesTemplate(numColumns);
        final boolean hasLocation = plugin.getPlatform() == PlatformType.SPIGOT && table.hasLocation();
        final boolean hasData = table.hasData();
        final boolean hasAction = table.hasActionId();
        final boolean hasLook = table.hasLook();
        stmt += table.getValuesHeader(plugin.getPlatform());
        stmt += " VALUES";
        for (int i = 0; i < entries.size(); i++) {
            stmt += "\n" + inc;
            if (i + 1 == entries.size()) {
                stmt += ";";
            } else {
                stmt += ",";
            }
        }
        HashMap<Long, byte[]> blobsToLog = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(stmt)) {

            int i = 1;
            for (DbEntry dbEntry : entries) {
                int prior = i;
                // statement.setString(i++, table);
                statement.setLong(i++, dbEntry.getTime());
                statement.setInt(i++, dbEntry.getUid());
                int action = dbEntry.getState() ? dbEntry.getAction().idPos : dbEntry.getAction().id;

                if (hasAction) {
                    statement.setInt(i++, action);
                }
                if (hasLocation) {
                    statement.setInt(i++, getWID(dbEntry.world));
                    statement.setInt(i++, dbEntry.x);
                    int y = dbEntry.y;
                    if (y > 32767) {
                        y = 32767;
                    }
                    if (y < -32768) {
                        y = -32768;
                    }
                    statement.setInt(i++, y);
                    statement.setInt(i++, dbEntry.z);
                }
                if (hasLook) {
                    statement.setInt(i++, dbEntry.pitch);
                    statement.setInt(i++, dbEntry.yaw);
                }
                if (table.hasStringTarget()) {
                    statement.setString(i++, dbEntry.getTargetUUID());
                } else {
                    statement.setInt(i++, dbEntry.getTargetId());
                }
                if (dbEntry instanceof XrayEntry) {
                    statement.setShort(i++, ((XrayEntry) dbEntry).getRating());
                }
                if (hasData) {
                    statement.setString(i++, dbEntry.getData());
                }
                if (table.hasBlob()) {
                    statement.setBoolean(i++, dbEntry.hasBlob());
                    if (dbEntry.hasBlob()) {
                        try {
                            blobsToLog.put(dbEntry.getTime(), dbEntry.getBlob());
                        } catch (BusyException e) {
                            plugin.warning("Failed to acquire lock to log blob");
                            plugin.print(e);
                        }
                    }
                }
                if (i - prior != numColumns) {
                    plugin.warning("Incorrect number of columns provided inserting action "
                            + dbEntry.getAction().toString() + " into " + table);
                    plugin.warning(i - prior + " =/= " + numColumns);
                    plugin.warning("Statement: " + stmt);
                    throw new IllegalArgumentException();
                }
            }

            statement.executeUpdate();
        }
        if (table.hasBlob() && blobsToLog.size() > 0) {
            putBlobs(blobsToLog);
        }

        rowcount += entries.size();

        double elapsed = (System.nanoTime() - start) / 1000000.0;
        plugin.debug(table + ": Logged " + count + " entrie(s) in " + (Math.round(elapsed * 10.0) / 10.0) + "ms. ("
                + (Math.round(elapsed / count * 10.0) / 10.0) + "ms each)", 3);
        return true;
    }

    private String getSize(double bytes) {
        int oom = 0;
        while (bytes > 1024) {
            bytes /= 1024;
            oom++;
        }
        String out = "";
        switch (oom) {
            case 0:
                out = "B";
                break;
            case 1:
                out = "KB";
                break;
            case 2:
                out = "MB";
                break;
            case 3:
                out = "GB";
                break;
            case 4:
                out = "TB";
                break;
        }
        return (Math.round(bytes * 100.0) / 100.0) + " " + out;
    }

    void putBlobs(HashMap<Long, byte[]> blobsToLog) throws SQLException {
        plugin.debug("Logging " + blobsToLog.size() + " blobs");
        HashMap<Long, byte[]> subBlobs = new HashMap<>();
        int size = 0;
        Iterator<Entry<Long, byte[]>> it = blobsToLog.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Long, byte[]> entry = it.next();
            if (entry.getValue() == null || entry.getValue().length == 0) {
                continue;
            }
            if (size + entry.getValue().length > 16777215 || subBlobs.size() >= 1000) {
                if (subBlobs.size() == 0) {
                    plugin.warning("Blob too big. Skipping. " + entry.getKey() + "e");
                    continue;
                }
                plugin.debug("Logging " + subBlobs.size() + " blobs, " + getSize(size));
                putBlobs_(subBlobs);
                subBlobs.clear();
                size = 0;
            }
            size += entry.getValue().length;
            subBlobs.put(entry.getKey(), entry.getValue());
        }
        if (!subBlobs.isEmpty()) {
            plugin.debug("Logging " + subBlobs.size() + " blobs, " + getSize(size));
            putBlobs_(subBlobs);
        }
    }

    private void putBlobs_(HashMap<Long, byte[]> blobsToLog) throws SQLException {
        String stmt = "INSERT INTO " + Table.AUXPROTECT_INVBLOB + " (time, `blob`) VALUES ";
        for (int i = 0; i < blobsToLog.size(); i++) {
            stmt += "\n(?, ?),";
        }

        Connection connection;
        try {
            connection = conn.getWriteConnection(30000);
        } catch (BusyException e) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(stmt.substring(0, stmt.length() - 1))) {
            int i = 1;
            for (Entry<Long, byte[]> entry : blobsToLog.entrySet()) {
                plugin.debug("blob: " + entry.getKey(), 5);
                statement.setLong(i++, entry.getKey());
                setBlob(connection, statement, i++, entry.getValue());
            }

            statement.executeUpdate();
        } finally {
            returnConnection(connection);
        }
    }

    private void putBlob(long time, byte[] bytes) throws SQLException {
        String stmt = "INSERT INTO " + Table.AUXPROTECT_INVBLOB + " (time, `blob`) VALUES ";
        stmt += "\n(?, ?),";

        Connection connection;
        try {
            connection = conn.getWriteConnection(30000);
        } catch (BusyException e) {
            return;
        }
        synchronized (connection) {
            try (PreparedStatement statement = connection.prepareStatement(stmt.substring(0, stmt.length() - 1))) {
                int i = 1;
                plugin.debug("blob: " + time, 5);
                statement.setLong(i++, time);
                setBlob(connection, statement, i++, bytes);

                statement.executeUpdate();
            } finally {
                returnConnection(connection);
            }
        }
    }

    public int purge(Table table, long time) throws SQLException {
        if (!isConnected)
            return 0;
        if (time < Table.MIN_PURGE_INTERVAL) {
            return 0;
        }
        int count = 0;
        if (table == null) {
            for (Table table1 : Table.values()) {
                if (!table1.hasAPEntries()) {
                    continue;
                }
                if (!table1.exists(plugin)) {
                    continue;
                }
                if (!table1.canPurge()) {
                    continue;
                }
                count += purge(table1, time);
            }
            return count;
        }

        count += executeWriteReturnRows("DELETE FROM " + table + " WHERE (time < ?);",
                System.currentTimeMillis() - time);
        if (table == Table.AUXPROTECT_INVENTORY) {
            count += executeWriteReturnRows("DELETE FROM " + Table.AUXPROTECT_INVBLOB + " WHERE (time < ?);",
                    System.currentTimeMillis() - time);
            count += executeWriteReturnRows("DELETE FROM " + Table.AUXPROTECT_INVDIFF + " WHERE (time < ?);",
                    System.currentTimeMillis() - time);
            count += executeWriteReturnRows("DELETE FROM " + Table.AUXPROTECT_INVDIFFBLOB + " WHERE "
                    + Table.AUXPROTECT_INVDIFFBLOB + ".blobid NOT IN (SELECT DISTINCT blobid FROM "
                    + Table.AUXPROTECT_INVDIFF + " WHERE blobid" + (isMySQL() ? " IS" : "") + " NOT NULL);");
        }
        return count;
    }

    public void updateXrayEntry(XrayEntry entry) throws SQLException {
        if (!isConnected)
            return;
        String stmt = "UPDATE " + entry.getAction().getTable().toString();
        stmt += "\nSET rating=?, data=?";
        stmt += "\nWHERE time = ? AND uid = ? AND target_id = ?";

        plugin.debug(stmt, 3);

        Connection connection = conn.getWriteConnection(30000);
        try (PreparedStatement statement = connection.prepareStatement(stmt)) {
            int i = 1;
            statement.setShort(i++, entry.getRating());
            statement.setString(i++, entry.getData());
            statement.setLong(i++, entry.getTime());
            statement.setInt(i++, entry.getUid());
            statement.setInt(i++, entry.getTargetId());
            if (statement.executeUpdate() > 1) {
                plugin.warning("Updated multiple entries when updating the following entry:");
                Results.sendEntry(plugin, plugin.getConsoleSender(), entry, 0, true, true);
            }
        } finally {
            returnConnection(connection);
        }
    }

    public int getWID(String world) {
        if (worlds.containsKey(world)) {
            return worlds.get(world);
        }
        if (world == null || Bukkit.getWorld(world) == null) {
            return -1;
        }

        Connection connection;
        try {
            connection = conn.getWriteConnection(30000);
        } catch (BusyException e1) {
            return -1;
        }
        try {
            String stmt = "INSERT INTO " + Table.AUXPROTECT_WORLDS + " (name, wid)";
            stmt += "\nVALUES (?,?)";
            PreparedStatement pstmt = connection.prepareStatement(stmt);
            pstmt.setString(1, world);
            pstmt.setInt(2, nextWid);
            plugin.debug(stmt + "\n" + world + ":" + nextWid, 3);
            pstmt.execute();
            worlds.put(world, nextWid);
            rowcount++;
            return nextWid++;
        } catch (SQLException e) {
            plugin.print(e);
        } finally {
            returnConnection(connection);
        }
        return -1;
    }

    String getWorld(int wid) {
        for (Entry<String, Integer> entry : worlds.entrySet()) {
            if (entry.getValue() == wid) {
                return entry.getKey();
            }
        }
        return null;
    }

    public ArrayList<DbEntry> getAllUnratedXrayRecords(long since) {

        String stmt = "SELECT * FROM " + Table.AUXPROTECT_XRAY + " WHERE rating=-1";
        if (since > 0) {
            stmt += " AND time>" + since;
        }
        try {
            return lookupmanager.lookup(this, Table.AUXPROTECT_XRAY, stmt, null);
        } catch (LookupException e) {
            plugin.print(e);
        }
        return null;
    }

    /**
     * Creates a new action and stores it in the database for future use. Use
     * EntryAction.getAction(String) to determine if an action already exists, or to
     * recall it later. Actions only need to be created once, after which they are
     * automatically loaded on startup. You cannot delete actions. Be careful what
     * you create.
     *
     * @param key   The key which will be used to refer to this action in lookups.
     * @param ntext The text which will be displayed for this action in looks for
     *              negative actions.
     * @param ptext The text which will be displayed for this action in looks for
     *              positive actions, or null for singular actions.
     * @throws AlreadyExistsException if the action you are attempting to create
     *                                already exists or the name is taken.
     * @throws SQLException           if there is a problem connecting to the
     *                                database.
     * @returns The created EntryAction.
     */
    public EntryAction createAction(String key, String ntext, String ptext)
            throws AlreadyExistsException, SQLException {
        if (EntryAction.getAction(key) != null) {
            throw new AlreadyExistsException();
        }
        int pid = -1;
        int nid = -1;
        EntryAction action = null;

        if (ptext == null) {
            nid = nextActionId++;
            action = new EntryAction(key, nid, ntext);
        } else {
            nid = nextActionId++;
            pid = nextActionId++;
            action = new EntryAction(key, nid, pid, ntext, ptext);
        }
        Connection connection = conn.getWriteConnection(30000);
        try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO " + Table.AUXPROTECT_API_ACTIONS
                + " (name, nid, pid, ntext, ptext) VALUES (?, ?, ?, ?, ?)")) {
            int i = 1;
            pstmt.setString(i++, key);
            pstmt.setInt(i++, nid);
            pstmt.setInt(i++, pid);
            pstmt.setString(i++, ntext);
            pstmt.setString(i++, ptext);

            pstmt.executeUpdate();
        } finally {
            returnConnection(connection);
        }
        return action;
    }

    private void count() {
        int total = 0;
        plugin.debug("Counting rows..");
        for (Table table : Table.values()) {
            if (!table.exists(plugin) || !table.hasAPEntries()) {
                continue;
            }
            try {
                total += count(table);
            } catch (SQLException ignored) {
            }
        }
        plugin.debug("Counted all tables. " + total + " rows.");
        rowcount = total;
    }

    public int count(Table table) throws SQLException {
        return count(table.toString());
    }

    protected String getCountStmt(String table) {
        if (isMySQL()) {
            return "SELECT COUNT(*) FROM " + table;
        } else {
            return "SELECT COUNT(1) FROM " + table;
        }
    }

    int count(String table) throws SQLException {
        String stmtStr = getCountStmt(table);
        plugin.debug(stmtStr, 5);

        Connection connection = getConnection(false);
        try (PreparedStatement pstmt = connection.prepareStatement(stmtStr)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } finally {
            returnConnection(connection);
        }

        return -1;
    }

    public void setBlob(Connection connection, PreparedStatement statement, int index, byte[] bytes) throws SQLException {
        if (isMySQL()) {
            Blob ablob = connection.createBlob();
            ablob.setBytes(1, bytes);
            statement.setBlob(index, ablob);
        } else {
            statement.setBytes(index, bytes);
        }
    }

    public byte[] getBlob(ResultSet rs, String key) throws SQLException, IOException {
        if (isMySQL()) {
            try (InputStream in = rs.getBlob(key).getBinaryStream()) {
                return in.readAllBytes();
            }
        } else {
            return rs.getBytes(key);
        }
    }

    @SuppressWarnings("deprecation")
    public byte[] getBlob(DbEntry entry) throws SQLException {
        byte[] blob = null;

        String stmt = "SELECT * FROM " + Table.AUXPROTECT_INVBLOB + " WHERE time=?;";
        plugin.debug(stmt, 3);

        Connection connection;
        try {
            connection = getConnection(false);
        } catch (SQLException e1) {
            plugin.print(e1);
            return null;
        }
        try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
            pstmt.setLong(1, entry.getTime());
            try (ResultSet results = pstmt.executeQuery()) {
                if (results.next()) {
                    // TODO escape?
                    blob = getBlob(results, "blob");
                }
            }
        } catch (IOException e) {
            plugin.print(e);
        } finally {
            returnConnection(connection);
        }

        if (blob == null && plugin.getAPConfig().doSkipV6Migration()) {
            boolean hasblob = false;
            String data = entry.getData();
            if (data.contains(InvSerialization.ITEM_SEPARATOR)) {
                data = data.substring(
                        data.indexOf(InvSerialization.ITEM_SEPARATOR) + InvSerialization.ITEM_SEPARATOR.length());
                hasblob = true;
            }
            try {
                if (entry.getAction().id == EntryAction.INVENTORY.id && data.length() > 20) {
                    plugin.info("Migrating inventory in place to v6: " + entry.getTime());
                    try {
                        blob = InvSerialization.playerToByteArray(InvSerialization.toPlayer(data));
                    } catch (Exception e) {
                        plugin.warning("Failed to migrate inventory " + entry.getTime() + ".");
                    }
                } else if (hasblob) {
                    plugin.info("Migrating item in place to v6: " + entry.getTime());
                    blob = Base64Coder.decodeLines(data);
                } else {
                    plugin.info("Attempted to migrate invalid log");
                    return null;
                }
                this.putBlob(entry.getTime(), blob);
                executeWrite("UPDATE " + Table.AUXPROTECT_INVENTORY + " SET hasblob=1, data = '' where time="
                        + entry.getTime());
            } catch (IllegalArgumentException | SQLException e) {
                plugin.info("Error while decoding: " + data);
            }

        }
        return blob;
    }

    protected void incrementRows() {
        rowcount++;
    }

    public void cleanup() {
        usermanager.cleanup();
        if (townymanager != null) {
            townymanager.cleanup();
        }
        if (invdiffmanager != null) {
            invdiffmanager.cleanup();
        }
    }

    public void tick() {
        Connection connection;
        try {
            connection = conn.getWriteConnection(0);
        } catch (BusyException e) {
            return;
        }
        try {
            Arrays.asList(Table.values()).forEach(t -> {
                try {
                    put(connection, t);
                } catch (SQLException e) {
                    plugin.print(e);
                }
            });
            if (invdiffmanager != null) {
                invdiffmanager.put(connection);
            }
            cleanup();
        } finally {
            returnConnection(connection);
        }
    }
}
