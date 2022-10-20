package dev.heliosares.auxprotect.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;

import dev.heliosares.auxprotect.core.IAuxProtect;

public class ConnectionPool {
	private final String connString;
	private final String user;
	private final String pwd;
	private final boolean mysql;
	private final IAuxProtect plugin;
	private boolean closed;

	static final int INITIAL_CAPACITY = 5;
	LinkedList<Connection> pool = new LinkedList<Connection>();

	public String getConnString() {
		return connString;
	}

	public String getPwd() {
		return pwd;
	}

	public String getUser() {
		return user;
	}

	private synchronized Connection newConn() throws SQLException {
		if (mysql) {
			return DriverManager.getConnection(connString, user, pwd);
		}
		return DriverManager.getConnection(connString);
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

		for (int i = 0; i < INITIAL_CAPACITY; i++) {
			pool.add(newConn());
		}

	}

	public synchronized Connection getConnection() throws SQLException, IllegalStateException {
		if (hold.size() > 0) {
			throw new IllegalStateException("busy");
		}
		if (closed) {
			throw new IllegalStateException("closed");
		}
		if (!plugin.isBungee() && !plugin.isShuttingDown()) {
			if (Bukkit.isPrimaryThread()) {
				plugin.warning("Synchronous call to database. This may cause lag.");
				if (plugin.getDebug() > 0) {
					Thread.dumpStack();
				}
			}
		}
		if (pool.isEmpty()) {
			pool.add(newConn());
		}
		return pool.pop();
	}

	public synchronized void returnConnection(Connection connection) {
		if (closed) {
			try {
				connection.close();
			} catch (Exception ignored) {
			}
			return;
		}
		pool.push(connection);
	}

	public synchronized void close() {
		closed = true;
		while (!pool.isEmpty()) {
			try {
				pool.pop().close();
			} catch (SQLException e) {
			}
		}
	}

	public boolean isMySQL() {
		return mysql;
	}

	private List<String> hold = new ArrayList<>();

	public void placeHold(String reason) {
		synchronized (hold) {
			hold.add(reason);
		}
	}

	public void releaseHold(String reason) {
		synchronized (hold) {
			hold.remove(reason);
		}
	}
}
