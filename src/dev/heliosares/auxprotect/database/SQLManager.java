package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.exceptions.AlreadyExistsException;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.towny.TownyManager;
import dev.heliosares.auxprotect.utils.TimeUtil;
import org.bukkit.Bukkit;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

public class SQLManager extends ConnectionPool {
    public static final int MAX_LOOKUP_SIZE = 500000;
    private static SQLManager instance;
    private final IAuxProtect plugin;
    private final HashMap<String, Integer> worlds = new HashMap<>();
    private final File sqliteFile;
    private final LookupManager lookupmanager;
    private final InvDiffManager invdiffmanager;
    private final BlobManager invblobmanager;
    private final TownyManager townymanager;
    private final SQLUserManager usermanager;
    private final String tablePrefix;
    int rowcount;
    private MigrationManager migrationmanager;
    private boolean isConnected;
    private int nextWid;
    private int nextActionId = 10000;

    public SQLManager(IAuxProtect plugin, String target, String prefix, File sqliteFile, boolean mysql, String user, String pass) throws ClassNotFoundException {
        super(plugin, target, mysql, user, pass);
        instance = this;
        this.plugin = plugin;
        this.usermanager = new SQLUserManager(plugin, this);
        this.lookupmanager = new LookupManager(this, plugin);
        this.invdiffmanager = plugin.getPlatform() == PlatformType.SPIGOT ? new InvDiffManager(this, plugin) : null;
        this.invblobmanager = plugin.getPlatform() == PlatformType.SPIGOT ? new BlobManager(Table.AUXPROTECT_INVBLOB, this, plugin) : null;
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
            } catch (NoClassDefFoundError | IllegalStateException ignored) {
            }
        }
        this.townymanager = tm;
        this.sqliteFile = sqliteFile;
    }

    public static SQLManager getInstance() {
        return instance;
    }

    public String getTablePrefix() {
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isConnected() {
        return isConnected;
    }

    public int getVersion() {
        return migrationmanager.getVersion();
    }

    public int getOriginalVersion() {
        return migrationmanager.getOriginalVersion();
    }

    public void connect() throws SQLException {
        plugin.info("Connecting to database...");

        try {
            super.init(this::init);
        } catch (SQLException e) {
            if (migrationmanager.isMigrating()) {
                plugin.warning(
                        "Error while migrating database. This database will likely not work with the current version. You will need to restore a backup (plugins/AuxProtect/database/backups) and try again. Please contact the plugin developer if you are unable to complete migration.");
            }
            throw e;
        }

        isConnected = true;
        plugin.info("Connected!");

        // Auto Purge
        out:
        if (plugin.getAPConfig().doAutoPurge()) {
            long timeSincePurge = System.currentTimeMillis() - getLast(LastKeys.AUTO_PURGE);
            if (timeSincePurge < 3600000L) {
                plugin.info(Language.L.COMMAND__PURGE__SKIPAUTO.translate(TimeUtil.millisToString(timeSincePurge)));
                break out;
            }
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
                        }
                    }
                }
            }
            if (anypurge) {
                try {
                    plugin.info(Language.L.COMMAND__PURGE__UIDS.translate());
                    count += purgeUIDs();

                    if (!isMySQL()) plugin.getSqlManager().execute(plugin.getSqlManager()::vacuum, 30000L);
                } catch (SQLException e) {
                    plugin.warning(Language.L.COMMAND__PURGE__ERROR.translate());
                    plugin.print(e);
                    break out;
                }
                plugin.info(Language.L.COMMAND__PURGE__COMPLETE_COUNT.translate(count));
                setLast(LastKeys.AUTO_PURGE, System.currentTimeMillis());
            }
        }

        count();

        plugin.info("There are currently " + rowcount + " rows");
        if (townymanager != null) townymanager.init();
    }

    public void close() {
        isConnected = false;
        super.close();
    }

    @Nullable
    public String backup(Connection connection) throws SQLException {
        if (isMySQL()) return null;

        File backup = new File(sqliteFile.getParentFile(), "backups/backup-v" + migrationmanager.getVersion() + "-" + System.currentTimeMillis() + ".db");

        if (!backup.getParentFile().exists()) {
            boolean ignored = backup.getParentFile().mkdirs();
        }
        if (plugin.getAPConfig().doDisableVacuum()) {
            plugin.info("Vacuum is disabled, creating physical copy instead");
            try {
                Files.copy(sqliteFile.toPath(), backup.toPath());
            } catch (IOException e) {
                plugin.warning("Failed to create backup.");
            }
        } else {
            execute("VACUUM INTO ?", connection, backup.getAbsolutePath());
        }
        return backup.getAbsolutePath();
    }

    private void init(Connection connection) throws SQLException {
        this.migrationmanager = new MigrationManager(this, connection, plugin);
        migrationmanager.preTables();

        if (invdiffmanager != null) {
            invdiffmanager.createTable(connection);
        }

        if (invblobmanager != null) {
            invblobmanager.createTable(connection);
        }

        for (Table table : Table.values()) {
            if (table.hasAPEntries()) {
                execute(table.getSQLCreateString(plugin), connection);
            }
        }
        String stmt;
        if (plugin.getPlatform() == PlatformType.SPIGOT) {
            stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_INVDIFF;
            stmt += " (time BIGINT, uid INT, slot INT, qty INT, blobid BIGINT, damage INT);";
            execute(stmt, connection);

            stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_WORLDS;
            stmt += " (name varchar(255), wid SMALLINT);";
            execute(stmt, connection);

            stmt = "SELECT * FROM " + Table.AUXPROTECT_WORLDS + ";";
            debugSQLStatement(stmt);
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

        stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_LASTS;
        stmt += " (name SMALLINT PRIMARY KEY, value BIGINT);";
        execute(stmt, connection);
        for (LastKeys key : LastKeys.values()) {
            try {
                execute("INSERT INTO " + Table.AUXPROTECT_LASTS + " (name, value) VALUES (?,?)", connection, key.id);
            } catch (SQLException ignored) {
                // Ensures each LastKeys has a value, so we can just UPDATE later
            }
        }

        stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_API_ACTIONS
                + " (name varchar(255), nid SMALLINT, pid SMALLINT, ntext varchar(255), ptext varchar(255));";
        execute(stmt, connection);

        stmt = "SELECT * FROM " + Table.AUXPROTECT_API_ACTIONS + ";";
        debugSQLStatement(stmt);
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

        if (invblobmanager != null) {
            invblobmanager.init(connection);
        }

        plugin.debug("init done.");

    }

    //TODO test this
    public int purgeUIDs() throws SQLException {
        BinaryOperator<String> reducer = (a, b) -> a + " AND " + b;
        String stmt = "DELETE FROM auxprotect_uids WHERE " +
                Stream.of(
                        Arrays.stream(Table.values())
                                .filter(Table::hasAPEntries)
                                .map(table -> "(uid NOT IN (SELECT uid FROM " + table + " WHERE uid IS NOT NULL))")
                                .reduce(reducer).orElse(""),
                        Arrays.stream(Table.values())
                                .filter(Table::hasAPEntries)
                                .filter(table -> !table.hasStringTarget())
                                .map(table -> "(uid NOT IN (SELECT target_id FROM " + table + " WHERE target_id IS NOT NULL))")
                                .reduce(reducer).orElse("")
                ).reduce(reducer).orElseThrow();
        int count = executeReturnRows(stmt);
        plugin.debug("Purged " + count + " UIDs");
        this.usermanager.clearCache();
        return count;
    }

    public void vacuum(Connection connection) throws SQLException {
        if (plugin.getAPConfig().doDisableVacuum()) {
            plugin.info("Vacuum is disabled. To force this run `ap sqli vacuum` from the console.");
            return;
        }
        long sinceLastVac = System.currentTimeMillis() - getLast(LastKeys.VACUUM, connection);
        if (sinceLastVac < 24L * 3600000L * 6L) {
            plugin.info(Language.L.COMMAND__PURGE__NOTVACUUM.translate(TimeUtil.millisToString(sinceLastVac)));
            return;
        }
        plugin.info(Language.L.COMMAND__PURGE__VACUUM.translate());
        execute("VACUUM;", connection);
        setLast(LastKeys.VACUUM, System.currentTimeMillis(), connection);
    }


    protected void put(Connection connection, Table table) throws SQLException {
        long start = System.nanoTime();
        int count;
        List<DbEntry> entries = new ArrayList<>();

        DbEntry entry;
        while ((entry = table.queue.poll()) != null) {
            entries.add(entry);
        }
        count = entries.size();
        if (count == 0) {
            return;
        }
        StringBuilder stmt = new StringBuilder("INSERT INTO " + table + " ");
        int numColumns = table.getNumColumns(plugin.getPlatform());
        String inc = Table.getValuesTemplate(numColumns);
        final boolean hasLocation = plugin.getPlatform() == PlatformType.SPIGOT && table.hasLocation();
        final boolean hasData = table.hasData();
        final boolean hasAction = table.hasActionId();
        final boolean hasLook = table.hasLook();
        stmt.append(table.getValuesHeader(plugin.getPlatform()));
        stmt.append(" VALUES");
        for (int i = 0; i < entries.size(); i++) {
            stmt.append("\n").append(inc);
            if (i + 1 == entries.size()) {
                stmt.append(";");
            } else {
                stmt.append(",");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(stmt.toString())) {

            int i = 1;
            for (DbEntry dbEntry : entries) {
                int prior = i;
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
                    if (dbEntry.hasBlob() && dbEntry.getBlob() != null) {
                        setBlob(connection, statement, i++, dbEntry.getBlob());
                    } else statement.setNull(i++, Types.NULL);
                } else if (table.hasBlobID()) {
                    if (dbEntry.hasBlob() && dbEntry.getBlob() != null) {
                        long blobid = invblobmanager.getBlobId(connection, dbEntry.getBlob());
                        statement.setLong(i++, blobid);
                    } else statement.setNull(i++, Types.NULL);
                    if (table.hasItemMeta()) {
                        if (dbEntry instanceof SingleItemEntry sientry && sientry.getItem() != null) {
                            statement.setInt(i++, sientry.getQty());
                            statement.setInt(i++, sientry.getDamage());
                        } else {
                            statement.setNull(i++, Types.NULL);
                            statement.setNull(i++, Types.NULL);
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

        rowcount += entries.size();

        double elapsed = (System.nanoTime() - start) / 1000000.0;
        plugin.debug(table + ": Logged " + count + " entrie(s) in " + (Math.round(elapsed * 10.0) / 10.0) + "ms. ("
                + (Math.round(elapsed / count * 10.0) / 10.0) + "ms each)", 3);
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

        count += executeReturnRows("DELETE FROM " + table + " WHERE (time < ?);",
                System.currentTimeMillis() - time);
        if (table == Table.AUXPROTECT_INVENTORY) {
            count += executeReturnRows("DELETE FROM " + Table.AUXPROTECT_INVDIFF + " WHERE (time < ?);",
                    System.currentTimeMillis() - time);
            count += executeReturnRows("DELETE FROM " + Table.AUXPROTECT_INVDIFFBLOB + " WHERE "
                    + Table.AUXPROTECT_INVDIFFBLOB + ".blobid NOT IN (SELECT DISTINCT blobid FROM "
                    + Table.AUXPROTECT_INVDIFF + " WHERE blobid" + (isMySQL() ? " IS" : "") + " NOT NULL);");
            count += executeReturnRows("DELETE FROM " + Table.AUXPROTECT_INVBLOB + " WHERE "
                    + Table.AUXPROTECT_INVBLOB + ".blobid NOT IN (SELECT DISTINCT blobid FROM "
                    + Table.AUXPROTECT_INVENTORY + " WHERE blobid" + (isMySQL() ? " IS" : "") + " NOT NULL);");
        }
        return count;
    }

    public void updateXrayEntry(XrayEntry entry) throws SQLException {
        if (!isConnected)
            return;
        String stmt = "UPDATE " + entry.getAction().getTable().toString();
        stmt += "\nSET rating=?, data=?";
        stmt += "\nWHERE time = ? AND uid = ? AND target_id = ?";

        if (executeReturnRows(stmt, entry.getRating(), sanitize(entry.getData()), entry.getTime(), entry.getUid(), entry.getTargetId()) > 1) {
            plugin.warning("Updated multiple entries when updating the following entry:");
            Results.sendEntry(plugin, plugin.getConsoleSender(), entry, 0, true, true);
        }
    }

    public synchronized int getWID(String world) {
        if (worlds.containsKey(world)) {
            return worlds.get(world);
        }
        if (world == null || Bukkit.getWorld(world) == null) {
            return -1;
        }

        try {
            execute("INSERT INTO " + Table.AUXPROTECT_WORLDS + " (name, wid) VALUES (?,?)", 30000L, world, nextWid);
            worlds.put(world, nextWid);
            rowcount++;
            return nextWid++;
        } catch (SQLException e) {
            plugin.print(e);
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
     * @return The created EntryAction.
     * @throws AlreadyExistsException if the action you are attempting to create
     *                                already exists or the name is taken.
     * @throws SQLException           if there is a problem connecting to the
     *                                database.
     */
    @SuppressWarnings("unused")
    public EntryAction createAction(String key, String ntext, String ptext)
            throws AlreadyExistsException, SQLException {
        if (EntryAction.getAction(key) != null) {
            throw new AlreadyExistsException();
        }
        int pid = -1;
        int nid = -1;
        EntryAction action;

        if (ptext == null) {
            nid = nextActionId++;
            action = new EntryAction(key, nid, ntext);
        } else {
            nid = nextActionId++;
            pid = nextActionId++;
            action = new EntryAction(key, nid, pid, ntext, ptext);
        }

        execute("INSERT INTO " + Table.AUXPROTECT_API_ACTIONS + " (name, nid, pid, ntext, ptext) VALUES (?, ?, ?, ?, ?)", 30000L, key, nid, pid, ntext, ptext);
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

    public byte[] getBlob(DbEntry entry) throws SQLException {
        if (entry.getAction().getTable().hasBlob())
            return executeReturn(connection -> {
                try (PreparedStatement pstmt = connection.prepareStatement("SELECT ablob FROM " + entry.getAction().getTable() + " WHERE time=" + entry.getTime() + " LIMIT 1")) {
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            byte[] blob = getBlob(rs, 1);
                            System.out.println("Resolved blob to " + Arrays.toString(blob));
                            return blob;
                        }
                    }
                }
                return null;
            }, 30000L, byte[].class);
        if (entry.getAction().getTable() == Table.AUXPROTECT_INVENTORY)
            return invblobmanager.getBlob(entry);
        return null;
    }

    public void getMultipleBlobs(DbEntry... entries) throws SQLException {
        execute(connection -> {
            Table table = null;
            StringBuilder stmt = new StringBuilder("SELECT time,ablob FROM %s WHERE time IN (");
            HashMap<Long, DbEntry> entryHash = new HashMap<>();
            for (DbEntry entry : entries) {
                if (table == null) table = entry.getAction().getTable();
                else if (table != entry.getAction().getTable())
                    throw new IllegalArgumentException("Incompatible actions");
                stmt.append(entry.getTime()).append(",");
                entryHash.put(entry.getTime(), entry);
            }
            if (table == null) return;
            stmt = new StringBuilder(String.format(stmt.substring(0, stmt.length() - 1), table) + ")");
            try (PreparedStatement pstmt = connection.prepareStatement(stmt.toString())) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        entryHash.get(rs.getLong("time")).setBlob(getBlob(rs, "ablob"));
                    }
                }
            }
        }, 30000L);
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
        try {
            execute(connection -> {
                Arrays.asList(Table.values()).forEach(t -> {
                    try {
                        put(connection, t);
                    } catch (BusyException ignored) {
                    } catch (SQLException e) {
                        plugin.print(e);
                    }
                });
                if (invdiffmanager != null) {
                    invdiffmanager.put(connection);
                }
            }, 0L);
        } catch (BusyException ignored) {
        } catch (SQLException e) {
            plugin.print(e);
        }
        cleanup();
    }

    //TODO implement
    public void setLast(LastKeys key, long value) throws SQLException {
        execute(connection -> setLast(key, value, connection), 30000L);
    }

    public void setLast(LastKeys key, long value, Connection connection) throws SQLException {
        execute("UPDATE " + Table.AUXPROTECT_LASTS + " SET value=? WHERE name=?", connection, value, key.id);
    }

    public long getLast(LastKeys key) throws SQLException {
        return executeReturn(connection -> getLast(key, connection), 30000L, Long.class);
    }

    public long getLast(LastKeys key, Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT value FROM " + Table.AUXPROTECT_LASTS + " WHERE name=?")) {
            stmt.setShort(1, key.id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
            return -1;
        }
    }

    @Nullable
    public String getMigrationStatus() {
        if (migrationmanager == null) return null;
        return migrationmanager.getProgressString();
    }


    public enum LastKeys {
        AUTO_PURGE(1), VACUUM(2), TELEMETRY(3);

        public final short id;

        LastKeys(int id) {
            this.id = (short) id;
        }
    }
}
