package dev.heliosares.auxprotect.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.Bukkit;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;

public class ConnectionPool {
	private final LinkedList<Connection> pool = new LinkedList<Connection>();
	public static final int CAPACITY = 10;

	private final String connString;
	private final String user;
	private final String pwd;
	private final boolean mysql;
	private final IAuxProtect plugin;
	private boolean closed;
	private final Connection writeconn;
	private Thread whoHasWriteConnection;
	private ReentrantLock lock = new ReentrantLock();

	private static int alive = 0;
	private static int born = 0;

	public static int getNumAlive() {
		return alive;
	}

	public static int getNumBorn() {
		return born;
	}

	public int getPoolSize() {
		return pool.size();
	}

	public String getConnString() {
		return connString;
	}

	private synchronized Connection newConn() throws SQLException {
		alive++;
		born++;
		Connection connection;
		if (mysql) {
			connection = DriverManager.getConnection(connString, user, pwd);
		} else {
			connection = DriverManager.getConnection(connString);
		}
		return connection;
	}

	public ConnectionPool(IAuxProtect plugin, String connString, String user, String pwd)
			throws SQLException, ClassNotFoundException {
		this.connString = connString;
		this.plugin = plugin;
		this.user = user;
		this.pwd = pwd;
		this.mysql = user != null && pwd != null;

		boolean driver = false;
		if (!driver)
			try {
				Class.forName("org.sqlite.JDBC");
				driver = true;
			} catch (ClassNotFoundException e1) {
			}
		if (!driver)
			try {
				Class.forName("com.mysql.cj.jdbc.Driver");
				driver = true;
			} catch (ClassNotFoundException e1) {
			}
		if (!driver)
			try {
				Class.forName("com.mysql.jdbc.Driver");
				driver = true;
			} catch (ClassNotFoundException e1) {
			}
		if (!driver) {
			throw new ClassNotFoundException("SQL Driver not found");
		}

		writeconn = newConn();

		synchronized (pool) {
			for (int i = 0; i < CAPACITY; i++) {
				pool.add(newConn());
			}
		}

	}

	private void checkAsync() throws IllegalStateException {
		if (plugin.getPlatform() == PlatformType.SPIGOT) {
			if (Bukkit.isPrimaryThread()) {
				plugin.warning("Synchronous call to database.");
				if (plugin.getAPConfig().getDebug() > 0) {
					throw new IllegalStateException();
				}
			}
		}
	}

	public static class BusyException extends Exception {
		private static final long serialVersionUID = 4797822287876350186L;
		public final Thread holder;

		BusyException(Thread holder) {
			this.holder = holder;
		}
	};

	/**
	 * Use this ONLY for modifying the database.
	 * 
	 * You MUST synchronize this variable while you use it.
	 * 
	 * You MUST call {@link ConnectionPool#returnConnection(Connection)} when you
	 * are done with this Connection
	 * 
	 * @return a WRITE-ONLY connection to the database
	 * @param wait how many milliseconds to wait for a lock
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
		return writeconn;
	}

	public Connection getConnection() throws SQLException, IllegalStateException {
		if (closed) {
			throw new IllegalStateException("closed");
		}
		checkAsync();
		synchronized (pool) {
			if (pool.isEmpty()) {
				pool.add(newConn());
			}
			Connection out = pool.pop();
			checkOutTimes.put(out.hashCode(), System.currentTimeMillis());
			return out;
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
				writeTimes[writeTimeIndex] = new long[] { writeCheckOut, System.currentTimeMillis() };
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
				readTimes[readTimeIndex] = new long[] { checkedOutAt, System.currentTimeMillis() };
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
		} catch (SQLException e) {
		}
		synchronized (pool) {
			while (!pool.isEmpty()) {
				try {
					alive--;
					pool.pop().close();
				} catch (SQLException e) {
				}
			}
		}
	}

	public boolean isMySQL() {
		return mysql;
	}

	private static final HashMap<Integer, Long> checkOutTimes = new HashMap<>();
	private static final long[][] readTimes = new long[500][];
	private static int readTimeIndex;
	private static long writeCheckOut;
	private static final long[][] writeTimes = new long[200][];
	private static int writeTimeIndex;

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
		for (int i = 0; i < array.length; i++) {
			if (array[i] == null) {
				continue;
			}
			long start = array[i][0];
			long stop = array[i][1];
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
		return new long[] { last - first, sum, count };
	}
}
