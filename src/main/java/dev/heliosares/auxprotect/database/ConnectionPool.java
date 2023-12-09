package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.utils.SQLConsumer;
import dev.heliosares.auxprotect.utils.SQLFunction;
import dev.heliosares.auxprotect.utils.SQLFunctionWithException;
import org.bukkit.Bukkit;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ConnectionPool {
    private static final long[][] accessTimes = new long[500][];
    private static int expired = 0;
    private static int writeTimeIndex;
    private final ConnectionSupplier newConnectionSupplier;
    private final boolean mysql;
    private final IAuxProtect plugin;
    private final ReentrantLock lock = new ReentrantLock();
    private Connection connection;
    private long lastChecked = System.currentTimeMillis();
    private boolean closed;
    @Nullable
    private StackTraceElement[] whoHasLock;
    private long whoHasLockID = -1;
    private long lockedSince;
    private long timeConnected;
    private boolean ready;
    private boolean skipAsyncCheck;

    public ConnectionPool(IAuxProtect plugin, String connString, boolean mysql, @Nullable String user, @Nullable String pwd) throws ClassNotFoundException {
        this.plugin = plugin;
        this.mysql = mysql;
        this.newConnectionSupplier = () -> {
            if (mysql) return DriverManager.getConnection(connString, user, pwd);
            else return DriverManager.getConnection(connString);
        };
        checkDriver();
    }

    @FunctionalInterface
    public interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    public static int getExpiredConnections() {
        return expired;
    }

    public static long[] calculateWriteTimes() {
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        long sum = 0;
        int count = 0;
        for (long[] longs : ConnectionPool.accessTimes) {
            if (longs == null) {
                continue;
            }
            long start = longs[0];
            long stop = longs[1];
            if (start == 0) {
                continue;
            }
            if (start < first) {
                first = start;
            }
            if (stop > last) {
                last = stop;
            }
            sum += stop - start;
            count++;
        }
        if (count == 0) {
            return null;
        }
        return new long[]{last - first, sum, count};
    }

    public static String sanitize(String str) {
        if (!AuxProtectAPI.getInstance().getAPConfig().isSanitizeUnicode()) return str;
        return str.replaceAll("[^\\u0020-\\u007F]", "¿");
    }

    public static String getBlobSize(double bytes) {
        int oom = 0;
        while (bytes > 1024) {
            bytes /= 1024;
            oom++;
        }
        String out = switch (oom) {
            case 0 -> "B";
            case 1 -> "KB";
            case 2 -> "MB";
            case 3 -> "GB";
            case 4 -> "TB";
            default -> "";
        };
        return (Math.round(bytes * 100.0) / 100.0) + " " + out;
    }

    private static boolean testConnection(Connection connection) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT 1")) {
            stmt.execute();
        } catch (SQLException ignored) {
            return false;
        }
        return true;
    }

    public void init(SQLConsumer initializationTask) throws SQLException, BusyException {
        connection = newConnectionSupplier.get();
        if (ready) throw new IllegalStateException("Already initialized");
        lock(10000);
        try {
            ready = true;
            initializationTask.accept(connection);
        } finally {
            lock.unlock();
        }
        timeConnected = System.currentTimeMillis();
    }

    public long getLockedSince() {
        return lockedSince;
    }

    public long getTimeConnected() {
        return timeConnected;
    }

    public StackTraceElement[] getWhoHasLock() {
        return whoHasLock;
    }

    private void checkDriver() throws ClassNotFoundException {
        try {
            Class.forName("org.sqlite.JDBC");
            return;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("com.mysql.jdbc.Driver");
            return;
        } catch (ClassNotFoundException ignored) {
        }
        throw new ClassNotFoundException("SQL Driver not found");
    }

    private void checkAsync() throws IllegalStateException {
        if (skipAsyncCheck) return;
        //noinspection ConstantValue
        if (plugin.getPlatform() == PlatformType.SPIGOT && Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Synchronous call to database.");
        }
    }

    @OverridingMethodsMustInvokeSuper
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!isMySQL()) {
            try {
                execute("PRAGMA optimize", connection);
            } catch (SQLException e) {
                plugin.warning("Failed to optimize SQLite database - this doesn't really matter");
                plugin.print(e);
            }
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    /**
     * Causes the normal check to prevent synchronous calls to the database to be skipped.
     * This should only be called when shutting down.
     */
    public void setSkipAsyncCheck(boolean state) {
        skipAsyncCheck = state;
    }

    /**
     * Same as {@link ConnectionPool#executeReturn(SQLFunction, long, Class)} but as a void
     */
    public void execute(SQLConsumer task, long wait) throws SQLException, BusyException {
        executeReturn(connection -> {
            task.accept(connection);
            return null;
        }, wait, Object.class);
    }

    /**
     * Executes a given task under a write-lock, providing the writeable Connection, then returns a value
     *
     * @param task The task to be executed under the write-lock
     * @param wait How long to wait for a lock
     * @param type The class which will be returned by the SQLFunction
     * @return The value returned by the task
     * @throws BusyException If the wait time is exceeded
     * @throws SQLException  For SQLException thrown by the task
     */
    public <T> T executeReturn(SQLFunction<T> task, long wait, Class<T> type) throws SQLException, BusyException {
        try {
            return executeReturnException(task::apply, wait, type);
        } catch (SQLException | BusyException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Same as {@link ConnectionPool#executeReturn(SQLFunction, long, Class)} but with any Exception
     *
     * @throws Exception For Exception thrown by the task
     */
    @SuppressWarnings("unused")
    public <T> T executeReturnException(SQLFunctionWithException<T> task, long wait, Class<T> type) throws Exception {
        if (closed || connection == null) throw new IllegalStateException("closed");
        if (!ready) throw new IllegalStateException("Not yet initialized");
        lock(wait);
        if (lock.getHoldCount() == 1) {
            lockedSince = System.currentTimeMillis();
            whoHasLock = Thread.currentThread().getStackTrace().clone();
            whoHasLockID = Thread.currentThread().getId();
        }
        if (!isConnectionValid()) {
            connection = newConnectionSupplier.get();
        }
        try {
            return task.apply(connection);
        } finally {
            if (lock.getHoldCount() == 1) {
                writeTimeIndex++;
                if (writeTimeIndex >= accessTimes.length) {
                    writeTimeIndex = 0;
                }
                accessTimes[writeTimeIndex] = new long[]{lockedSince, System.currentTimeMillis()};
                whoHasLock = null;
                whoHasLockID = -1;
            }
            lockedSince = 0;
            lock.unlock();
        }
    }

    private void lock(long wait) throws BusyException {
        checkAsync();
        try {
            if (lock.tryLock(wait, TimeUnit.MILLISECONDS)) return;
        } catch (InterruptedException ignored) {
        }
        throw new BusyException(whoHasLock, whoHasLockID);
    }

    public boolean isMySQL() {
        return mysql;
    }

    /**
     * @see PreparedStatement#execute()
     */
    public void execute(String stmt, long wait, Object... args) throws SQLException, BusyException {
        execute(connection -> execute(stmt, connection, args), wait);
    }

    /**
     * @see PreparedStatement#execute()
     */
    public void execute(String stmt, Connection connection, Object... args) throws SQLException {
        debugSQLStatement(stmt, args);
        try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
            prepare(connection, pstmt, args);
            pstmt.execute();
        }
    }

    /**
     * @see PreparedStatement#executeUpdate()
     */
    public int executeReturnRows(String stmt, Object... args) throws SQLException, BusyException {
        debugSQLStatement(stmt, args);
        return executeReturn(connection -> {
            try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
                prepare(connection, pstmt, args);
                return pstmt.executeUpdate();
            }
        }, 30000L, Integer.class);
    }

    public int executeReturnGenerated(String stmt, Object... args) throws SQLException, BusyException {
        debugSQLStatement(stmt, args);
        return executeReturn(connection -> {
            try (PreparedStatement pstmt = connection.prepareStatement(stmt, Statement.RETURN_GENERATED_KEYS)) {
                prepare(connection, pstmt, args);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            return -1;
        }, 30000L, Integer.class);
    }

    public ResultMap executeGetMap(String stmt, Object... args) throws SQLException, BusyException {
        debugSQLStatement(stmt, args);
        return executeReturn(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(stmt)) {
                prepare(connection, statement, args);
                try (ResultSet rs = statement.executeQuery()) {
                    return new ResultMap(this, rs);
                }
            }
        }, 30000L, ResultMap.class);
    }

    public void debugSQLStatement(String stmt, Object... args) {
        final String originalStmt = stmt;
        try {
            for (Object arg : args) {
                stmt = stmt.replaceFirst("\\?", String.valueOf(arg).replace("\\", "\\\\"));
            }
        } catch (Exception e) {
            stmt = originalStmt + ": ";
            boolean first = true;
            StringBuilder stmtBuilder = new StringBuilder(stmt);
            for (Object o : args) {
                if (first) first = false;
                else stmtBuilder.append(", ");
                stmtBuilder.append(o);
            }
            stmt = stmtBuilder.toString();
        }
        plugin.debug(stmt, 5);
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

    public byte[] getBlob(ResultSet rs, String key) throws SQLException {
        if (isMySQL()) {
            try (InputStream in = rs.getBlob(key).getBinaryStream()) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        } else {
            return rs.getBytes(key);
        }
    }

    public byte[] getBlob(ResultSet rs, int index) throws SQLException {
        if (isMySQL()) {
            try (InputStream in = rs.getBlob(index).getBinaryStream()) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        } else {
            return rs.getBytes(index);
        }
    }

    protected int count(Connection connection, String table, @Nullable String where) throws SQLException {
        String stmtStr = getCountStmt(table);
        if (where != null) stmtStr += " WHERE " + where;
        plugin.debug(stmtStr, 5);

        try (PreparedStatement pstmt = connection.prepareStatement(stmtStr)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return -1;
            }
        }
    }

    protected String getCountStmt(String table) {
        if (isMySQL()) {
            return "SELECT COUNT(*) FROM " + table;
        } else {
            return "SELECT COUNT(1) FROM " + table;
        }
    }

    protected void prepare(Connection connection, PreparedStatement pstmt, Object... args) throws SQLException {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            Object o = args[i];
            if (o == null) {
                pstmt.setNull(i + 1, Types.NULL);
            } else if (o instanceof String c) {
                pstmt.setString(i + 1, c);
            } else if (o instanceof Integer c) {
                pstmt.setInt(i + 1, c);
            } else if (o instanceof Long c) {
                pstmt.setLong(i + 1, c);
            } else if (o instanceof Short s) {
                pstmt.setShort(i + 1, s);
            } else if (o instanceof Boolean c) {
                pstmt.setBoolean(i + 1, c);
            } else if (o instanceof byte[] c) {
                setBlob(connection, pstmt, i + 1, c);
            } else {
                throw new IllegalArgumentException(o.toString());
            }
        }
    }

    private boolean isConnectionValid() {
        if (connection == null) return false;
        if (!isMySQL()) return true;
        if (System.currentTimeMillis() - lastChecked < 30000L) return true;
        if (testConnection(connection)) {
            lastChecked = System.currentTimeMillis();
            return true;
        } else {
            expired++;
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            return false;
        }
    }

    public String concat(String s1, String s2) {
        if (isMySQL()) {
            return "CONCAT(" + s1 + "," + s2 + ")";
        } else {
            return s1 + "||" + s2;
        }
    }
}
