package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import org.bukkit.Bukkit;
import org.sqlite.SQLiteConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ConnectionPool {
    public static final int CAPACITY = 10;
    private static final HashMap<Integer, Long> checkOutTimes = new HashMap<>();
    private static final long[][] readTimes = new long[500][];
    private static final long[][] writeTimes = new long[200][];
    private static int alive = 0;
    private static int born = 0;
    private static int readTimeIndex;
    private static long writeCheckOut;
    private static int writeTimeIndex;
    private final LinkedList<Connection> pool = new LinkedList<>();
    private final String connString;
    private final String user;
    private final String pwd;
    private final boolean mysql;
    private final IAuxProtect plugin;
    private final Connection writeconn;
    private boolean closed;
    @Nullable
    private StackTraceElement[] whoHasWriteConnection;
    private final ReentrantLock lock = new ReentrantLock();

    public ConnectionPool(IAuxProtect plugin, String connString, String user, String pwd)
            throws SQLException, ClassNotFoundException {
        this.connString = connString;
        this.plugin = plugin;
        this.user = user;
        this.pwd = pwd;
        this.mysql = user != null && pwd != null;

        boolean driver = false;
        try {
            Class.forName("org.sqlite.JDBC");
            driver = true;
        } catch (ClassNotFoundException ignored) {
        }
        if (!driver)
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                driver = true;
            } catch (ClassNotFoundException ignored) {
            }
        if (!driver)
            try {
                Class.forName("com.mysql.jdbc.Driver");
                driver = true;
            } catch (ClassNotFoundException ignored) {
            }
        if (!driver) {
            throw new ClassNotFoundException("SQL Driver not found");
        }

        writeconn = newConn(false);

        synchronized (pool) {
            for (int i = 0; i < CAPACITY; i++) {
                pool.add(newConn(true));
            }
        }

    }

    public static int getNumAlive() {
        return alive;
    }

    ;

    public static int getNumBorn() {
        return born;
    }

    public static long[] calculateWriteTimes() {
        return calculateTimes(writeTimes);
    }

    public static long[] calculateReadTimes() {
        return calculateTimes(readTimes);
    }

    private static long[] calculateTimes(long[][] array) {
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        long sum = 0;
        int count = 0;
        for (long[] longs : array) {
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

    public int getPoolSize() {
        return pool.size();
    }

    private synchronized Connection newConn(boolean readonly) throws SQLException {
        alive++;
        born++;
        Connection connection;
        if (mysql) {
            connection = DriverManager.getConnection(connString, user, pwd);
        } else {
            if (readonly) {
                SQLiteConfig config = new SQLiteConfig();
                config.setReadOnly(true);
                connection = DriverManager.getConnection(connString, config.toProperties());
            } else connection = DriverManager.getConnection(connString);
        }
        return connection;
    }

    private void checkAsync() throws IllegalStateException {
        if (plugin.getPlatform() == PlatformType.SPIGOT) {
            if (Bukkit.isPrimaryThread()) {
                plugin.warning("Synchronous call to database.");
                if (plugin.getAPConfig().getDebug() > 0) {
                    Thread.dumpStack();
                }
            }
        }
    }

    /**
     * Use this ONLY for modifying the database.
     * <p>
     * You MUST synchronize this variable while you use it.
     * <p>
     * You MUST call {@link ConnectionPool#returnConnection(Connection)} when you
     * are done with this Connection
     *
     * @param wait how many milliseconds to wait for a lock
     * @return a WRITE-ONLY connection to the database
     * @throws BusyException if wait is exceeded and the database is busy
     */
    public @Nonnull Connection getWriteConnection(long wait) throws BusyException {
        if (closed || writeconn == null) {
            throw new IllegalStateException("closed");
        }
        checkAsync();
        boolean havelock = false;
        try {
            havelock = lock.tryLock(wait, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }
        if (!havelock) {
            throw new BusyException(whoHasWriteConnection);
        }
        if (lock.getHoldCount() == 1) {
            writeCheckOut = System.currentTimeMillis();
            whoHasWriteConnection = Thread.currentThread().getStackTrace().clone();
        }
        return writeconn;
    }

    public @Nullable StackTraceElement[] getWhoHasWriteConnection() {
        return whoHasWriteConnection;
    }

    public long getWriteCheckOutTime() {
        return writeCheckOut;
    }

    public Connection getConnection() throws SQLException, IllegalStateException {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        checkAsync();
        synchronized (pool) {
            if (pool.isEmpty()) {
                pool.add(newConn(true));
            }
            Connection out = pool.pop();
            checkOutTimes.put(out.hashCode(), System.currentTimeMillis());
            return out;
        }
    }

    public synchronized void returnConnection(Connection connection) {
        if (connection == null) return;
        if (connection.equals(writeconn)) {
            if (!lock.isHeldByCurrentThread()) {
                throw new IllegalArgumentException("Returned a write connection not currently held by the thread.");
            }
            if (lock.getHoldCount() == 1) {
                writeTimeIndex++;
                if (writeTimeIndex >= writeTimes.length) {
                    writeTimeIndex = 0;
                }
                writeTimes[writeTimeIndex] = new long[]{writeCheckOut, System.currentTimeMillis()};
                writeCheckOut = 0;
                whoHasWriteConnection = null;
            }
            lock.unlock();
            return;
        }
        synchronized (pool) {
            Long checkedOutAt = checkOutTimes.remove(connection.hashCode());
            if (checkedOutAt != null) {
                readTimeIndex++;
                if (readTimeIndex >= readTimes.length) {
                    readTimeIndex = 0;
                }
                readTimes[readTimeIndex] = new long[]{checkedOutAt, System.currentTimeMillis()};
            }
            if (closed || pool.size() >= CAPACITY) {
                try {
                    connection.close();
                    alive--;
                } catch (Exception ignored) {
                }
                return;
            }
            pool.push(connection);
        }
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writeconn.close();
        } catch (SQLException ignored) {
        }
        synchronized (pool) {
            while (!pool.isEmpty()) {
                try {
                    alive--;
                    pool.pop().close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public boolean isMySQL() {
        return mysql;
    }

    public static class BusyException extends Exception {
        public final StackTraceElement[] stack;

        BusyException(StackTraceElement[] stack) {
            this.stack = stack;
        }
    }
}
