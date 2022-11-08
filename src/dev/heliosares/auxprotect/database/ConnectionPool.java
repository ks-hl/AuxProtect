package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import org.bukkit.Bukkit;
import org.sqlite.SQLiteConfig;

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
    private static int roaming = 0;
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
    private Thread whoHasWriteConnection;
    private final ReentrantLock lock = new ReentrantLock();

    public ConnectionPool(IAuxProtect plugin, String connString, String user, String pwd)
            throws SQLException, ClassNotFoundException {
        this.connString = connString;
        this.plugin = plugin;
        this.user = user;
        this.pwd = pwd;
        this.mysql = user != null && pwd != null;

        checkDriver();

        writeconn = newConn(false);

        synchronized (pool) {
            for (int i = 0; i < CAPACITY; i++) {
                pool.add(newConn(true));
            }
        }

    }

    public static int getRoaming() {
        return roaming;
    }

    public static int getNumAlive() {
        return alive;
    }

    public static int getNumBorn() {
        return born;
    }

    ;

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
        Connection connection;
        if (mysql) { //TODO MySQL readonly
            connection = DriverManager.getConnection(connString, user, pwd);
        } else if (readonly) {
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            connection = DriverManager.getConnection(connString, config.toProperties());
        } else {
            connection = DriverManager.getConnection(connString);
        }
        alive++;
        born++;
        return connection;
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

    /**
     * Use this ONLY for modifying the database.
     * <p>
     * You MUST call {@link ConnectionPool#returnConnection(Connection)} when you
     * are done with this Connection
     *
     * @param wait how many milliseconds to wait for a lock
     * @return a writable connection to the database
     * @throws BusyException if wait is exceeded and the database is busy
     */
    public Connection getWriteConnection(long wait) throws BusyException {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        checkAsync();
        boolean havelock = false;
        if (wait > 0) {
            try {
                havelock = lock.tryLock(wait, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
        } else {
            havelock = lock.tryLock();
        }
        if (!havelock) {
            throw new BusyException(whoHasWriteConnection);
        }
        if (lock.getHoldCount() == 1) {
            writeCheckOut = System.currentTimeMillis();
            whoHasWriteConnection = Thread.currentThread();
        }
        roaming++;
        return writeconn;
    }

    public Connection getConnection(boolean wait) throws SQLException, IllegalStateException, BusyException {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        checkAsync();
        if (wait) {
            lock.lock();
        } else {
            try {
                if (!lock.tryLock(5000, TimeUnit.MILLISECONDS)) {
                    throw new BusyException(whoHasWriteConnection);
                }
            } catch (InterruptedException e) {
                throw new BusyException(whoHasWriteConnection);
            }
        }
        try {
            synchronized (pool) {
                if (pool.isEmpty()) {
                    pool.add(newConn(true));
                }
                Connection out = pool.pop();
                checkOutTimes.put(out.hashCode(), System.currentTimeMillis());
                roaming++;
                return out;
            }
        } finally {
            lock.unlock();
        }
    }

    public void returnConnection(Connection connection) {
        if (connection.equals(writeconn)) {
            if (!lock.isHeldByCurrentThread()) {
                throw new IllegalArgumentException();
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
            roaming--;
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
            roaming--;
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

    public static class BusyException extends SQLException {
        public final Thread holder;

        BusyException(Thread holder) {
            this.holder = holder;
        }
    }
}
