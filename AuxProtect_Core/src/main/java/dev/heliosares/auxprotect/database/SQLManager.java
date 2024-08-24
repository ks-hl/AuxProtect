package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.exceptions.AlreadyExistsException;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.utils.TimeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

public class SQLManager extends ConnectionPool {
    public static final int MAX_LOOKUP_SIZE = 500000;
    private static SQLManager instance;
    private final IAuxProtect plugin;
    private final HashMap<String, Integer> worlds = new HashMap<>();
    private final File sqliteFile;
    private final LookupManager lookupManager;
    private final BlobManager invBlobManager;
    private final BlobManager transactionBlobManager;
    private final SQLUserManager usermanager;
    private final String tablePrefix;
    int rowcount;
    private MigrationManager migrationmanager;
    private boolean isConnected;
    private boolean isConnectedAndInitDone;
    private int nextWid;
    private int nextActionId = 1;

    public SQLManager(IAuxProtect plugin, String target, String prefix, File sqliteFile, boolean mysql, String user, String pass) throws ClassNotFoundException {
        super(plugin, target, mysql, user, pass);
        instance = this;
        this.plugin = plugin;
        this.usermanager = new SQLUserManager(plugin, this);
        this.lookupManager = new LookupManager(this, plugin);
        this.invBlobManager = plugin.getPlatform() == PlatformType.SPIGOT ? new BlobManager(Table.AUXPROTECT_INVBLOB, this, plugin) : null;
        this.transactionBlobManager = plugin.getPlatform() == PlatformType.SPIGOT ? new BlobManager(Table.AUXPROTECT_TRANSACTIONS_BLOB, this, plugin) : null;
        if (prefix == null || prefix.isEmpty()) {
            tablePrefix = "";
        } else {
            prefix = prefix.replaceAll(" ", "_");
            if (!prefix.endsWith("_")) {
                prefix += "_";
            }
            tablePrefix = prefix;
        }
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
        return lookupManager;
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

    public void connect() throws SQLException, BusyException {
        plugin.info("Connecting to database...");

        try {
            super.init(this::init);
        } catch (SQLException e) {
            if (migrationmanager != null && migrationmanager.isMigrating()) {
                plugin.warning(
                        "Error while migrating database. This database will likely not work with the current version. You will need to restore a backup (plugins/AuxProtect/database/backups) and try again. Please contact the plugin developer if you are unable to complete migration.");
            }
            throw e;
        }

        isConnected = true;
        plugin.info("Connected!");

        // Auto Purge
        out:
        if (plugin.getAPConfig().getAutoPurgePeriodicity() > 0) {
            long timeSincePurge = System.currentTimeMillis() - getLast(LastKeys.AUTO_PURGE);
            if (timeSincePurge < plugin.getAPConfig().getAutoPurgePeriodicity()) {
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

        otherConnectTasks();

        plugin.info("Init done.");
        isConnectedAndInitDone = true;
    }

    protected void otherConnectTasks() {
    }

    public void close() {
        isConnected = false;
        super.close();
    }

    @Nullable
    public String backup() {
        if (isMySQL()) return null;

        File backup = new File(sqliteFile.getParentFile(), "backups/backup-v" + migrationmanager.getVersion() + "-" + System.currentTimeMillis() + ".db");

        if (!backup.getParentFile().exists()) {
            boolean ignored = backup.getParentFile().mkdirs();
        }

//        if (plugin.getAPConfig().doDisableVacuum()) {
//            plugin.info("Vacuum is disabled, creating physical copy instead");
        try {
            Files.copy(sqliteFile.toPath(), backup.toPath());
        } catch (IOException e) {
            plugin.warning("Failed to create backup.");
        }
//        } else {
//            execute("VACUUM INTO ?", connection, backup.getAbsolutePath());
//        }
        return backup.getAbsolutePath();
    }

    protected void createTables(Connection connection) throws SQLException, BusyException {

        if (invBlobManager != null) {
            invBlobManager.createTable(connection);
        }

        if (transactionBlobManager != null) {
            transactionBlobManager.createTable(connection);
        }

        for (Table table : Table.values()) {
            if (table.hasAPEntries() && table.exists(plugin)) {
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
                + " (name varchar(255), nid SMALLINT, pid SMALLINT, ntext varchar(255), ptext varchar(255), owner varchar(255), created BIGINT);";
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
                    nextActionId = Math.max(nextActionId, Math.max(nid, pid) + 1);
                    new EntryAction(key, nid, pid, ntext, ptext, Table.AUXPROTECT_API_ACTIONS);
                }
            }
        }
    }

    protected void postTables(Connection connection) throws SQLException {
        if (invBlobManager != null) {
            invBlobManager.init(connection);
        }

        if (transactionBlobManager != null) {
            transactionBlobManager.init(connection);
        }

        if (getLast(LastKeys.LEGACY_POSITIONS, connection) == 0)
            setLast(LastKeys.LEGACY_POSITIONS, System.currentTimeMillis(), connection);
    }

    private void init(Connection connection) throws SQLException, BusyException {
        connection.setAutoCommit(false);
        try {
            this.migrationmanager = new MigrationManager(this, connection, plugin);
            migrationmanager.preTables();

            createTables(connection);

            usermanager.init(connection);

            migrationmanager.postTables();

            postTables(connection);

            connection.commit();
            plugin.debug("table init done.");
        } catch (Throwable t) {
            plugin.warning("An error occurred during initialization. Rolling back changes.");
            connection.rollback();
            throw t;
        } finally {
            if (!connection.getAutoCommit()) connection.setAutoCommit(true);
        }
    }

    public int purgeUIDs() throws SQLException, BusyException {
        int count = executeReturn(connection -> {
            connection.setAutoCommit(false);
            try {
                // Step 1: Create a Temporary Table
                execute("CREATE TEMP" + (isMySQL() ? "ORARY" : "") + " TABLE temp_uids (uid INT PRIMARY KEY)", connection);

                // Step 2: Insert Data into the Temporary Table
                for (Table table : Table.values()) {
                    if (table.hasAPEntries() && table.exists(plugin)) {
                        execute("INSERT" + (isMySQL() ? "" : " OR") + " IGNORE INTO temp_uids (uid) SELECT uid FROM " + table, connection);
                        if (!table.hasStringTarget()) {
                            execute("INSERT" + (isMySQL() ? "" : " OR") + " IGNORE INTO temp_uids (uid) SELECT target_id FROM " + table, connection);
                        }
                    }
                }

                // Step 3: Delete the UIDs
                int count_ = executeReturnRows(connection, "DELETE FROM auxprotect_uids WHERE uid IN (SELECT auxprotect_uids.uid FROM auxprotect_uids LEFT JOIN temp_uids AS temp ON auxprotect_uids.uid = temp.uid WHERE temp.uid IS NULL)");

                // Step 4: Drop the Temporary Table
                execute("DROP " + (isMySQL() ? "TEMPORARY " : "") + "TABLE IF EXISTS temp_uids", connection);

                connection.commit();

                return count_;
            } catch (Throwable t) {
                connection.rollback();
                throw t;
            } finally {
                if (!connection.getAutoCommit()) connection.setAutoCommit(true);
            }
        }, 30000L, Integer.class);

        // Log and Clear Cache
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
        try {
            execute("VACUUM", connection);
        } catch (SQLException e) {
            if (e.getErrorCode() == 13) {
                plugin.info("Your machine has insufficient space in the temporary partition to condense the database. " +
                        "If you keep running into this issue despite room on the disk, add the following line to the end of the config, with no indentation:\n" +
                        "disablevacuum: true");
            } else {
                throw e;
            }
        }
        setLast(LastKeys.VACUUM, System.currentTimeMillis(), connection);
    }

    protected boolean putPosEntry(PreparedStatement preparedStatement, DbEntry dbEntry, AtomicInteger i) throws SQLException {
        return false;
    }

    protected void putXrayEntry(PreparedStatement preparedStatement, DbEntry dbEntry, AtomicInteger i) throws SQLException {
    }

    protected boolean putSingleItemEntry(PreparedStatement preparedStatement, DbEntry dbEntry, AtomicInteger i) throws SQLException, BusyException {
        return false;
    }

    protected void putTransaction(PreparedStatement preparedStatement, DbEntry dbEntry, AtomicInteger i) throws SQLException, BusyException {
    }


    protected void put(Connection connection, Table table, @Nullable List<DbEntry> entries) throws SQLException, BusyException {
        long start = System.nanoTime();
        if (entries == null || entries.isEmpty()) {
            entries = new ArrayList<>();

            DbEntry entry;
            while (entries.size() < 128 && (entry = table.queue.poll()) != null) {
                entries.add(entry);
            }
        }

        if (entries.isEmpty()) {
            return;
        }

        StringBuilder stmt = new StringBuilder("INSERT INTO " + table + " ");
        int numColumns = table.getNumColumns(plugin.getPlatform());
        String inc = Table.getValuesTemplate(numColumns);
        final boolean hasLocation = plugin.getPlatform().getLevel() == PlatformType.Level.SERVER && table.hasLocation();
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

            AtomicInteger i = new AtomicInteger(1);
            for (DbEntry dbEntry : entries) {
                final int prior = i.get();
                statement.setLong(i.getAndIncrement(), dbEntry.getTime());
                statement.setInt(i.getAndIncrement(), dbEntry.getUid());
                int action = dbEntry.getState() ? dbEntry.getAction().idPos : dbEntry.getAction().id;

                if (hasAction) {
                    statement.setInt(i.getAndIncrement(), action);
                }
                if (hasLocation) {
                    statement.setInt(i.getAndIncrement(), getWID(dbEntry.getWorld()));
                    statement.setInt(i.getAndIncrement(), dbEntry.getX());
                    statement.setInt(i.getAndIncrement(), Math.max(Math.min(dbEntry.getY(), Short.MAX_VALUE), Short.MIN_VALUE));
                    statement.setInt(i.getAndIncrement(), dbEntry.getZ());

                    if (!putPosEntry(statement, dbEntry, i) && table == Table.AUXPROTECT_POSITION) {
                        statement.setByte(i.getAndIncrement(), (byte) 0);
                    }
                }
                if (hasLook) {
                    statement.setInt(i.getAndIncrement(), dbEntry.getPitch());
                    statement.setInt(i.getAndIncrement(), dbEntry.getYaw());
                }
                if (table.hasStringTarget()) {
                    String target = sanitize(dbEntry.getTargetUUID());
                    statement.setString(i.getAndIncrement(), target);
                    if (table == Table.AUXPROTECT_LONGTERM) {
                        statement.setInt(i.getAndIncrement(), target.toLowerCase().hashCode());
                    }
                } else {
                    statement.setInt(i.getAndIncrement(), dbEntry.getTargetId());
                }

                putXrayEntry(statement, dbEntry, i);

                if (hasData) {
                    statement.setString(i.getAndIncrement(), sanitize(dbEntry.getData()));
                }
                if (table.hasBlob()) {
                    if (dbEntry.hasBlob() && dbEntry.getBlob() != null) {
                        setBlob(connection, statement, i.getAndIncrement(), dbEntry.getBlob());
                    } else statement.setNull(i.getAndIncrement(), Types.NULL);
                } else if (table.hasBlobID()) {
                    if (dbEntry.hasBlob() && dbEntry.getBlob() != null) {
                        long blobid = getBlobManager(table).getBlobId(connection, dbEntry.getBlob());
                        statement.setLong(i.getAndIncrement(), blobid);
                    } else statement.setNull(i.getAndIncrement(), Types.NULL);
                    if (table.hasItemMeta()) {
                        if (!putSingleItemEntry(statement, dbEntry, i)) {
                            statement.setNull(i.getAndIncrement(), Types.NULL);
                            statement.setNull(i.getAndIncrement(), Types.NULL);
                        }
                    }
                }

                putTransaction(statement, dbEntry, i);

                if (i.get() - prior != numColumns) {
                    plugin.warning("Incorrect number of columns provided inserting action "
                            + dbEntry.getAction().toString() + " into " + table);
                    plugin.warning(i.get() - prior + " =/= " + numColumns);
                    plugin.warning("Statement: " + stmt);
                    throw new IllegalArgumentException();
                }
            }

            statement.executeUpdate();
        }

        int count = entries.size();
        rowcount += count;
        double elapsed = (System.nanoTime() - start) / 1000000.0;
        plugin.debug(table + ": Logged " + count + " entrie(s) in " + (Math.round(elapsed * 10.0) / 10.0) + "ms. ("
                + (Math.round(elapsed / count * 10.0) / 10.0) + "ms each)", 3);
    }

    public int purge(Table table, long time) throws SQLException, BusyException {
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

    public synchronized int getWID(String world) {
        if (worlds.containsKey(world)) {
            return worlds.get(world);
        }

        if (!plugin.doesWorldExist(world)) {
            return -1;
        }

        try {
            execute("INSERT INTO " + Table.AUXPROTECT_WORLDS + " (name, wid) VALUES (?,?)", 300000L, world, nextWid);
            worlds.put(world, nextWid);
            rowcount++;
            return nextWid++;
        } catch (SQLException | BusyException e) {
            plugin.print(e);
        }
        return -1;
    }

    public String getWorld(int wid) {
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
            return lookupManager.lookup(Table.AUXPROTECT_XRAY, stmt, null);
        } catch (LookupException e) {
            plugin.print(e);
        }
        return null;
    }

    /**
     * @see dev.heliosares.auxprotect.api.AuxProtectAPI#createAction(String, String, String, String)
     */
    @SuppressWarnings("unused")
    public synchronized EntryAction createAction(@Nonnull String plugin, @Nonnull String key, @Nonnull String ntext, @Nullable String ptext) throws AlreadyExistsException, SQLException, BusyException {
        if (plugin.isEmpty()) throw new IllegalArgumentException("plugin cannot be empty.");
        if (key.isEmpty()) throw new IllegalArgumentException("key cannot be empty.");
        if (ntext.isEmpty()) throw new IllegalArgumentException("ntext cannot be empty.");

        EntryAction preexisting = EntryAction.getAction(key);
        if (preexisting != null) throw new AlreadyExistsException(preexisting);

        int pid, nid;
        EntryAction action;

        if (ptext == null) {
            nid = nextActionId++;
            pid = -1;
            action = new EntryAction(key, nid, ntext, Table.AUXPROTECT_API);
        } else {
            nid = nextActionId++;
            pid = nextActionId++;
            action = new EntryAction(key, nid, pid, ntext, ptext, Table.AUXPROTECT_API);
        }


        execute("INSERT INTO " + Table.AUXPROTECT_API_ACTIONS + " (name, nid, pid, ntext, ptext, owner, created) VALUES (?, ?, ?, ?, ?, ?, ?)", 30000L, key, nid, pid, ntext, ptext, plugin, System.currentTimeMillis());
        return action;
    }

    private BlobManager getBlobManager(Table table) {
        return switch (table) {
            case AUXPROTECT_INVENTORY -> invBlobManager;
            case AUXPROTECT_TRANSACTIONS -> transactionBlobManager;
            default ->
                    throw new IllegalArgumentException("Table " + table + " does not have an associated blob manager.");
        };
    }

    public void count() {
        int total = 0;
        plugin.info("Counting rows..");
        for (Table table : Table.values()) {
            if (!table.exists(plugin) || !table.hasAPEntries()) {
                continue;
            }
            try {
                total += count(table);
            } catch (BusyException e) {
                plugin.warning("Database busy, unable to count rows of " + table);
            } catch (SQLException e) {
                plugin.warning("An error occurred counting rows of " + table);
                plugin.print(e);
            }
        }
        plugin.info("Count complete. There are " + total + " rows.");
        rowcount = total;
    }

    public int count(Table table) throws SQLException, BusyException {
        return executeReturn(connection -> count(connection, table.toString(), null), 5000L, Integer.class);
    }

    public byte[] getBlob(DbEntry entry) throws SQLException, BusyException {
        if (entry.getAction().getTable().hasBlob())
            return executeReturn(connection -> {
                try (PreparedStatement pstmt = connection.prepareStatement("SELECT ablob FROM " + entry.getAction().getTable() + " WHERE time=" + entry.getTime() + " LIMIT 1")) {
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return getBlob(rs, 1);
                        }
                    }
                }
                return null;
            }, 30000L, byte[].class);

        return getBlobManager(entry.getAction().getTable()).getBlob(entry);
    }

    public void getMultipleBlobs(DbEntry... entries) throws SQLException, BusyException {
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
        if (usermanager != null) {
            usermanager.cleanup();
        }
        if (invBlobManager != null) {
            invBlobManager.cleanup();
        }
        if (transactionBlobManager != null) {
            transactionBlobManager.cleanup();
        }
    }

    public void tick() {
        if (!isConnected() || !isConnectedAndInitDone) return;
        try {
            execute(this::tickPuts, 0L);
        } catch (BusyException ignored) {
        } catch (SQLException e) {
            plugin.print(e);
        }
        cleanup();
    }

    protected void tickPuts(Connection connection) {
        Arrays.asList(Table.values()).forEach(t -> {
            try {
                put(connection, t, null);
            } catch (BusyException ignored) {
                // Only thrown by UID lookups. Shouldn't happen because this thread already has the lock
            } catch (SQLException e) {
                plugin.print(e);
            }
        });
    }

    public void setLast(LastKeys key, long value) throws SQLException, BusyException {
        key.value = value;
        execute(connection -> setLast(key, value, connection), 30000L);
    }

    public void setLast(LastKeys key, long value, Connection connection) throws SQLException {
        key.value = value;
        execute("UPDATE " + Table.AUXPROTECT_LASTS + " SET value=? WHERE name=?", connection, value, key.id);
    }

    public long getLast(LastKeys key) throws SQLException, BusyException {
        if (key.value != null) return key.value;
        return executeReturn(connection -> getLast(key, connection), 30000L, Long.class);
    }

    public long getLast(LastKeys key, Connection connection) throws SQLException {
        if (key.value != null) return key.value;
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

    protected IAuxProtect getPlugin() {
        return plugin;
    }

    public enum LastKeys {
        AUTO_PURGE(1), VACUUM(2), TELEMETRY(3), LEGACY_POSITIONS(4);

        public final short id;
        private Long value;

        LastKeys(int id) {
            this.id = (short) id;
        }
    }

    public DbEntry convertToTransactionEntryForMigration(DbEntry entry, EntryAction action, short quantity, double cost, double balance, int target_id2) throws SQLException, BusyException {
        return null;
    }
}
