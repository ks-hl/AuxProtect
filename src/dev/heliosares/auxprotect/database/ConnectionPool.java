package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import org.bukkit.Bukkit;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class ConnectionPool {
    private static final long[][] accessTimes = new long[500][];
    private static int expired = 0;
    private static int writeTimeIndex;
    private final Supplier<Connection> newConnectionSupplier;
    private final boolean mysql;
    private final IAuxProtect plugin;
    private Connection connection;
    private final ReentrantLock lock = new ReentrantLock();
    private long lastChecked = System.currentTimeMillis();
    private boolean closed;
    @Nullable
    private StackTraceElement[] whoHasLock;
    private long lockedSince;

    public ConnectionPool(IAuxProtect plugin, String connString, boolean mysql, @Nullable String user, @Nullable String pwd, SQLConsumer initializationTask)
            throws SQLException, ClassNotFoundException {
        this.plugin = plugin;
        this.mysql = mysql;
        this.newConnectionSupplier = () -> {
            try {
                if (mysql) return DriverManager.getConnection(connString, user, pwd);
                else return DriverManager.getConnection(connString);
            } catch (SQLException ignored) {
            }
            return null;
        };
        checkDriver();
        connection = newConnectionSupplier.get();

        execute(initializationTask, Long.MAX_VALUE);
    }

    public long getLockedSince() {
        return lockedSince;
    }

    public StackTraceElement[] getWhoHasLock() {
        return whoHasLock;
    }

    public static int getExpired() {
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
        if (plugin.getPlatform() == PlatformType.SPIGOT && Bukkit.isPrimaryThread()) {
            plugin.warning("Synchronous call to database.");
            throw new IllegalStateException();
        }
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }


    @FunctionalInterface
    public interface SQLConsumer {
        void accept(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SQLFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SQLFunctionWithException<T> {
        T apply(Connection connection) throws Exception;
    }

    public static class Holder<T> {
        private T value;
        private boolean set;

        public void set(@Nullable T t) {
            set = true;
            value = t;
        }

        @Nullable
        public T get() {
            return value;
        }

        public Number getNumberOrElse(Number def) {
            if (value == null) return def;
            if (!(value instanceof Number number)) throw new IllegalStateException();
            return number;
        }

        /**
         * This is different from a null check because passing 'null' to {@link Holder#set(T)} will still cause this to return true
         *
         * @return Whether this Holder has had {@link Holder#set(T)} called at least once, regardless of the value
         */
        public boolean isSet() {
            return set;
        }
    }

    /**
     * Same as {@link ConnectionPool#executeReturn(SQLFunction, long, Class)} but as a void
     */
    public void execute(SQLConsumer task, long wait) throws SQLException {
        executeReturn(connection -> {
            task.accept(connection);
            return null;
        }, wait, Object.class);
    }

    /**
     * Executes a given task under a write-lock, providing the writeable Connection, then returns a value
     *
     * @param task The task to be executed under the write-lock
     * @param wait How long to wait for a write
     * @param type The class which will be returned by the SQLFunction
     * @return The value returned by the task
     * @throws BusyException If the wait time is exceeded
     * @throws SQLException  For SQLException thrown by the task
     */
    public <T> T executeReturn(SQLFunction<T> task, long wait, Class<T> type) throws SQLException {
        try {
            return executeReturnException(task::apply, wait, type);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Same as {@link ConnectionPool#executeReturn(SQLFunction, long, Class)} but with any Exception
     */
    public <T> T executeReturnException(SQLFunctionWithException<T> task, long wait, Class<T> type) throws Exception {
        if (closed || connection == null) {
            throw new IllegalStateException("closed");
        }
        checkAsync();
        boolean havelock = false;
        try {
            havelock = lock.tryLock(wait, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }
        if (!havelock) {
            throw new BusyException(whoHasLock);
        }
        if (lock.getHoldCount() == 1) {
            lockedSince = System.currentTimeMillis();
            whoHasLock = Thread.currentThread().getStackTrace().clone();
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
            }
            lockedSince = 0;
            lock.unlock();
        }
    }

    private boolean isConnectionValid() {
        if (connection == null) return false;
        if (!isMySQL()) return true;
        if (System.currentTimeMillis() - lastChecked < 30000L) return true;
        if (testConnection()) {
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

    private boolean testConnection() {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT 1")) {
            stmt.execute();
        } catch (SQLException ignored) {
            return false;
        }
        return true;
    }

    public boolean isMySQL() {
        return mysql;
    }

    public static class BusyException extends SQLException {
        public final StackTraceElement[] stack;

        private BusyException(StackTraceElement[] stack) {
            this.stack = stack;
        }
    }
}
