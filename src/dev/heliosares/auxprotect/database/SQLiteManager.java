package dev.heliosares.auxprotect.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
//import org.bukkit.World;
import org.bukkit.command.CommandSender;

import dev.heliosares.auxprotect.IAuxProtect;

public class SQLiteManager {
	private Connection connection;
	private String dbFile;
	private final IAuxProtect plugin;

	private boolean isConnected;

	private HashMap<String, Integer> worlds = new HashMap<>();
	private int nextWid;

	private int version;

	private int count;

	public int getCount() {
		return count;
	}

	public boolean isConnected() {
		return isConnected;
	}

	public int getVersion() {
		return version;
	}

	public static enum TABLE {
		AUXPROTECT, AUXPROTECT_SPAM, AUXPROTECT_LONGTERM, AUXPROTECT_ABANDONED, AUXPROTECT_INVENTORY, WORLDS;

		@Override
		public String toString() {
			return super.toString().toLowerCase();
		}
	}

	public String holdingConnection;
	public long holdingConnectionSince;

	public SQLiteManager(IAuxProtect plugin, String dbFile) {
		this.plugin = plugin;
		this.dbFile = dbFile;
	}

	public boolean connect() {
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
			System.err.println("SQL DRIVER NOT FOUND");
			return false;
		}

		try {
			connection = DriverManager.getConnection(dbFile);
			init();

			isConnected = true;
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public void close() {
		if (connection != null) {
			synchronized (connection) {
				holdingConnectionSince = System.currentTimeMillis();
				holdingConnection = "close";
				try {
					connection.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
				holdingConnectionSince = 0;
			}
		}
	}

	private void init() throws SQLException {
		synchronized (connection) {
			holdingConnectionSince = System.currentTimeMillis();
			holdingConnection = "init";
			Statement statement = connection.createStatement();

			String stmt = "CREATE TABLE IF NOT EXISTS version (time BIGINT,version INTEGER);";
			plugin.debug(stmt, 3);
			statement.execute(stmt);

			stmt = "SELECT * FROM version;";
			plugin.debug(stmt, 3);
			ResultSet results = statement.executeQuery(stmt);
			long versionTime = 0;
			while (results.next()) {
				long versionTime_ = results.getLong("time");
				int version_ = results.getInt("version");
				if (versionTime_ > versionTime) {
					version = version_;
					versionTime = versionTime_;
				}
				plugin.debug("Version at " + versionTime_ + " was v" + version_ + ".", 5);
			}
			results.close();

			if (version < 1) {
				PreparedStatement pstmt = connection
						.prepareStatement("INSERT INTO version (time,version) VALUES (?,?)");
				pstmt.setLong(1, System.currentTimeMillis());
				pstmt.setInt(2, 1);
				plugin.debug("Updating database to v1", 1);
				pstmt.execute();
				pstmt.close();
			}

			stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT.toString() + " (\n";
			stmt += "    time BIGINT(255),\n";
			stmt += "    user varchar(255),\n";
			stmt += "    action_id SMALLINT,\n";
			if (!plugin.isBungee()) {
				stmt += "    world_id SMALLINT,\n";
				stmt += "    x INTEGER,\n";
				stmt += "    y SMALLINT,\n";
				stmt += "    z INTEGER,\n";
			}
			stmt += "    target varchar(255),\n";
			stmt += "    data LONGTEXT\n";
			stmt += ");";
			plugin.debug(stmt, 3);
			statement.execute(stmt);

			stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT_LONGTERM.toString() + " (\n";
			stmt += "    time BIGINT(255),\n";
			stmt += "    user varchar(255),\n";
			stmt += "    action_id SMALLINT,\n";
			stmt += "    target varchar(255)\n";
			stmt += ");";
			plugin.debug(stmt, 3);
			statement.execute(stmt);

			if (!plugin.isBungee()) {
				stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT_INVENTORY.toString() + " (\n";
				stmt += "    time BIGINT(255),\n";
				stmt += "    user varchar(255),\n";
				stmt += "    action_id SMALLINT,\n";
				stmt += "    world_id SMALLINT,\n";
				stmt += "    x INTEGER,\n";
				stmt += "    y SMALLINT,\n";
				stmt += "    z INTEGER,\n";
				stmt += "    target varchar(255),\n";
				stmt += "    data LONGTEXT\n";
				stmt += ");";
				plugin.debug(stmt, 3);
				statement.execute(stmt);

				stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT_SPAM.toString() + " (\n";
				stmt += "    time BIGINT(255),\n";
				stmt += "    user varchar(255),\n";
				stmt += "    action_id SMALLINT,\n";
				stmt += "    world_id SMALLINT,\n";
				stmt += "    x INTEGER,\n";
				stmt += "    y SMALLINT,\n";
				stmt += "    z INTEGER,\n";
				stmt += "    target varchar(255),\n";
				stmt += "    data LONGTEXT\n";
				stmt += ");";
				plugin.debug(stmt, 3);
				statement.execute(stmt);

				if (plugin.getAPConfig().isPrivate()) {
					stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT_ABANDONED.toString() + " (\n";
					stmt += "    time BIGINT(255),\n";
					stmt += "    user varchar(255),\n";
					stmt += "    action_id SMALLINT,\n";
					stmt += "    world_id SMALLINT,\n";
					stmt += "    x INTEGER,\n";
					stmt += "    y SMALLINT,\n";
					stmt += "    z INTEGER,\n";
					stmt += "    target varchar(255)\n";
					stmt += ");";
					plugin.debug(stmt, 3);
					statement.execute(stmt);
				}

				stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.WORLDS.toString() + " (name varchar(255), wid SMALLINT);";
				plugin.debug(stmt, 3);
				statement.execute(stmt);

				stmt = "SELECT * FROM " + TABLE.WORLDS + ";";
				plugin.debug(stmt, 3);
				results = statement.executeQuery(stmt);
				while (results.next()) {
					String world = results.getString("name");
					int wid = results.getInt("wid");
					worlds.put(world, wid);
					if (wid >= nextWid) {
						nextWid = wid + 1;
					}
				}
				results.close();
			}

			statement.close();
			plugin.debug("init done.", 1);
			holdingConnectionSince = 0;
		}
	}

	public void execute(String string) throws SQLException {
		plugin.debug(string, 2);
		Statement statement = connection.createStatement();
		statement.execute(string);
		statement.close();
	}

	protected long put(TABLE table, ArrayList<DbEntry> entries) throws SQLException {
		if (!isConnected)
			return -1;
		synchronized (connection) {
			holdingConnectionSince = System.currentTimeMillis();
			holdingConnection = "put";
			String stmt = "INSERT INTO " + table.toString() + " ";
			String inc = "\n (?, ?, ?, ?, ?, ?, ?, ?, ?)";
			boolean hasLocation = true;
			boolean hasData = true;
			if (table == TABLE.AUXPROTECT_LONGTERM) {
				stmt += "(time, user, action_id, target)";
				inc = "\n (?, ?, ?, ?)";
				hasData = false;
				hasLocation = false;
			} else if (plugin.isBungee()) {
				stmt += "(time, user, action_id, target, data)";
				inc = "\n (?, ?, ?, ?, ?)";
				hasLocation = false;
			} else if (table == TABLE.AUXPROTECT || table == TABLE.AUXPROTECT_SPAM
					|| table == TABLE.AUXPROTECT_INVENTORY) {
				stmt += "(time, user, action_id, world_id, x, y, z, target, data)";
			} else if (table == TABLE.AUXPROTECT_ABANDONED) {
				stmt += "(time, user, action_id, world_id, x, y, z, target)";
				inc = "\n (?, ?, ?, ?, ?, ?, ?, ?)";
				hasData = false;
			} else {
				plugin.warning("Unknown table " + table.toString() + ". This is bad. (put)");
				return -1;
			}
			stmt += " VALUES";
			for (int i = 0; i < entries.size(); i++) {
				stmt += inc;
				if (i + 1 == entries.size()) {
					stmt += ";";
				} else {
					stmt += ",";
				}
			}
			PreparedStatement statement = connection.prepareStatement(stmt);

			int i = 1;
			for (DbEntry dbEntry : entries) {
				// statement.setString(i++, table);
				statement.setLong(i++, dbEntry.getTime());
				statement.setString(i++, dbEntry.userUuid.toLowerCase());
				int action = dbEntry.getState() ? dbEntry.getAction().idPos : dbEntry.getAction().id;

				statement.setInt(i++, action);
				if (hasLocation) {
					statement.setInt(i++, getWid(dbEntry.world));
					statement.setInt(i++, dbEntry.x);
					statement.setInt(i++, dbEntry.y);
					statement.setInt(i++, dbEntry.z);
				}
				statement.setString(i++, dbEntry.targetUuid);
				if (hasData) {
					statement.setString(i++, dbEntry.getData());
				}
			}

			statement.executeUpdate();
			ResultSet result = statement.getGeneratedKeys();
			int serialized_id = -1;

			if (result.next()) {
				serialized_id = result.getInt(1);
			}

			result.close();
			statement.close();
			count += entries.size();
			holdingConnectionSince = 0;
			return serialized_id;
		}
	}

	public enum LookupExceptionType {
		SYNTAX, PLAYER_NOT_FOUND, ACTION_NEGATE, UNKNOWN_ACTION, ACTION_INCOMPATIBLE, UNKNOWN_WORLD, UNKNOWN_TABLE,
		GENERAL, TOO_MANY
	}

	public static class LookupException extends Exception {
		private static final long serialVersionUID = -8329753973868577238L;

		public final LookupExceptionType error;
		public final String errorMessage;

		private LookupException(LookupExceptionType error, String errorMessage) {
			this.error = error;
			this.errorMessage = errorMessage;
		}

		@Override
		public String toString() {
			return error.toString() + ": " + errorMessage;
		}
	}

	public ArrayList<DbEntry> lookup(HashMap<String, String> params, Location location, boolean exact)
			throws LookupException {
		if (!isConnected)
			return null;

		try {
			TABLE table = null;
			TABLE forcedTable = null;

			String stmt = "\nWHERE (";
			HashMap<String, ArrayList<String>> dos = new HashMap<>();
			HashMap<String, ArrayList<String>> donts = new HashMap<>();
			if (exact && !params.containsKey("radius")) {
				if (location == null) {
					return null;
				}
				params.put("radius", "0");
			}
			for (Entry<String, String> entry : params.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();

				if (key.equalsIgnoreCase("time") || key.equalsIgnoreCase("after")) {
					ArrayList<String> dos_ = donts.get(key);
					if (dos_ == null) {
						dos_ = new ArrayList<>();
						dos.put(key, dos_);
					}
					dos_.add("time >= " + value);
					continue;
				} else if (key.equalsIgnoreCase("before")) {
					ArrayList<String> dos_ = donts.get(key);
					if (dos_ == null) {
						dos_ = new ArrayList<>();
						dos.put(key, dos_);
					}
					dos_.add("time <= " + value);
					continue;
				} else if (key.equalsIgnoreCase("db")) {
					try {
						forcedTable = TABLE.valueOf(value.toUpperCase());
					} catch (IllegalArgumentException e) {
						throw new LookupException(LookupExceptionType.SYNTAX,
								plugin.translate("lookup-invalid-syntax"));
					}
					continue;
				}
				boolean not = value.startsWith("!");
				if (not) {
					value = value.substring(1);
					if (key.equalsIgnoreCase("action")) {
						throw new LookupException(LookupExceptionType.ACTION_NEGATE,
								plugin.translate("lookup-action-negate"));
					}
				}
				for (String param : value.split(",")) {
					String theStmt = "";
					if (key.equalsIgnoreCase("target") && param.contains("*")) {
						theStmt = "target LIKE '" + param.replaceAll("-", " ").replaceAll("\\*", "%")
								+ "' OR target LIKE '" + param.replaceAll("\\*", "%") + "'";
					} else if (key.equalsIgnoreCase("user") || key.equalsIgnoreCase("target")) {
						if (param.startsWith("@")) {
							theStmt = "lower(" + key + ") = '" + param.substring(1).toLowerCase() + "'";
						} else if (!param.startsWith("#") && !param.startsWith("$")) {
							String targetUuid = this.getUuidFromUsername(param);
							if (targetUuid == null && !plugin.isBungee()) {
								@SuppressWarnings("deprecation")
								OfflinePlayer player = Bukkit.getOfflinePlayer(param);
								if (player != null) {
									targetUuid = "$" + player.getUniqueId().toString();
								}
							}
							if (targetUuid != null) {
								theStmt = key + " = '" + targetUuid + "'";
							} else if (key.equals("user")) {
								throw new LookupException(LookupExceptionType.PLAYER_NOT_FOUND,
										String.format(plugin.translate("lookup-playernotfound"), param));
							}
						}
						if (theStmt.length() > 0) {
							theStmt += " OR ";
						}
						theStmt += "lower(" + key + ") = '" + param.toLowerCase() + "' OR ";
						theStmt += "lower(" + key + ") = '" + param.toLowerCase().replaceAll("-", " ") + "'";

					} else if (key.equalsIgnoreCase("radius")) {
						String rStatement = stmtForRadius(location, param, !params.containsKey("world"), exact);
						if (rStatement != null) {
							theStmt = rStatement;
						}
					} else if (key.equalsIgnoreCase("action")) {
						String originalParam = param.toString();
						boolean state = param.startsWith("+");
						if (state || param.startsWith("-")) {
							param = param.substring(1);
						}
						EntryAction action = EntryAction.valueOfString(param);
						if (action == null || !action.isEnabled()) {
							throw new LookupException(LookupExceptionType.UNKNOWN_ACTION,
									String.format(plugin.translate("lookup-unknownaction"), param));
						} else {
							/*
							 * TODO if (sender != null) { if
							 * (!MyPermission.LOOKUP.hasPermission(action.toString().toLowerCase(), sender))
							 * { sender.sendMessage(String.format(plugin.translate("lookup-action-perm"),
							 * param)); return null; } }
							 */
							if (table == null) {
								table = action.getTable(plugin.isBungee());
							} else {
								if (table != action.getTable(plugin.isBungee())) {
									throw new LookupException(LookupExceptionType.ACTION_INCOMPATIBLE,
											plugin.translate("lookup-incompatible-tables"));
								}
							}
							if (action.hasDual) {
								if (!param.equals(originalParam)) {
									theStmt = "action_id = " + (state ? action.idPos : action.id);
								} else {
									theStmt = "action_id = " + action.id + " OR action_id =  " + action.idPos;
								}
							} else {
								theStmt = "action_id = " + action.id;
							}
						}
					} else if (key.equalsIgnoreCase("world")) {
						int wid = getWid(param);
						if (wid == -1) {
							throw new LookupException(LookupExceptionType.UNKNOWN_WORLD,
									String.format(plugin.translate("lookup-unknown-world"), param));
						}
						theStmt = "world_id = " + getWid(param);
					} else {
						theStmt = key + " = '" + param + "'";
					}
					if (theStmt.length() > 0) {
						if (not) {
							ArrayList<String> donts_ = donts.get(key);
							if (donts_ == null) {
								donts_ = new ArrayList<>();
								donts.put(key, donts_);
							}
							donts_.add(theStmt);
						} else {
							ArrayList<String> dos_ = dos.get(key);
							if (dos_ == null) {
								dos_ = new ArrayList<>();
								dos.put(key, dos_);
							}
							dos_.add(theStmt);
						}
					}
				}
			}
			int i = 0;
			if (dos.size() > 0) {
				stmt += "(";
				for (String key : dos.keySet()) {
					if (i > 0) {
						stmt += ") AND (";
					}
					ArrayList<String> values = dos.get(key);
					for (int i1 = 0; i1 < values.size(); i1++) {
						if (i1 > 0) {
							stmt += " OR ";
						}
						stmt += values.get(i1);
					}
					i++;
				}
				stmt += ")";
			}
			i = 0;
			if (donts.size() > 0) {
				if (donts.size() > 0) {
					stmt += " AND ";
				}
				stmt += "NOT (";
				for (String key : donts.keySet()) {
					if (i > 0) {
						stmt += " OR ";
					}
					ArrayList<String> values = donts.get(key);
					for (int i1 = 0; i1 < values.size(); i1++) {
						if (i1 > 0) {
							stmt += " OR ";
						}
						stmt += values.get(i1);
					}
					i++;
				}
				stmt += ")";
			}

			stmt += ")\nORDER BY time DESC\nLIMIT 100001;";
			if (forcedTable != null) {
				table = forcedTable;
			} else if (table == null) {
				table = TABLE.AUXPROTECT;
			}
			boolean hasLocation = true;
			boolean hasData = true;
			if (table == TABLE.AUXPROTECT_LONGTERM) {
				stmt = "SELECT time, user, action_id, target FROM " + table.toString() + stmt;
				hasLocation = false;
				hasData = false;
			} else if (plugin.isBungee()) {
				stmt = "SELECT time, user, action_id, target, data FROM auxprotect " + stmt;
				hasLocation = false;
			} else if (table == TABLE.AUXPROTECT || table == TABLE.AUXPROTECT_SPAM
					|| table == TABLE.AUXPROTECT_INVENTORY) {
				stmt = "SELECT time, user, action_id, world_id, x, y, z, target, data FROM " + table.toString() + stmt;
			} else if (table == TABLE.AUXPROTECT_ABANDONED) {
				stmt = "SELECT time, user, action_id, world_id, x, y, z, target FROM " + table.toString() + stmt;
				hasData = false;
			} else if (table == TABLE.WORLDS && plugin.getDebug() > 0) {
				stmt = "SELECT * FROM " + table.toString();
				plugin.debug(stmt, 3);
				try {
					PreparedStatement pstmt = connection.prepareStatement(stmt);
					ResultSet rs = pstmt.executeQuery();

					while (rs.next()) {
						String name = rs.getString("name");
						int wid = rs.getInt("wid");
						plugin.debug("§9" + wid + ": §f" + name, 1);
					}
				} catch (SQLException e) {

				}
				return null;
			} else {
				plugin.warning("Unknown table " + table.toString() + ". This is bad. (Lookup)");
				throw new LookupException(LookupExceptionType.UNKNOWN_TABLE, "Unknown Table");
			}
			plugin.debug(stmt, 3);
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			ArrayList<DbEntry> output = new ArrayList<>();
			long parseStart;
			synchronized (connection) {
				holdingConnectionSince = System.currentTimeMillis();
				holdingConnection = "lookup";
				long lookupStart = System.currentTimeMillis();
				try {
					pstmt = connection.prepareStatement(stmt);
					pstmt.setFetchSize(500);
					rs = pstmt.executeQuery();

					int count = 0;
					parseStart = System.currentTimeMillis();
					while (rs.next()) {
						long time = rs.getLong("time");
						String user = rs.getString("user");
						int action_id = rs.getInt("action_id");
						String world = null;
						int x = 0, y = 0, z = 0;
						if (hasLocation) {
							world = this.getWorld(rs.getInt("world_id"));
							x = rs.getInt("x");
							y = rs.getInt("y");
							z = rs.getInt("z");
						}
						String target = rs.getString("target");
						String data = null;
						if (hasData) {
							data = rs.getString("data");
						}
						EntryAction entryAction = EntryAction.fromId(action_id);
						if (entryAction == null) {
							plugin.debug("Unknown action_id: " + action_id, 1);
							continue;
						}
						boolean state = false;
						if (entryAction.hasDual && entryAction.id != action_id) {
							state = true;
						}
						DbEntry entry = new DbEntry(time, user, entryAction, state, world, x, y, z, target, data);

						output.add(entry);
						if (++count >= 100000) {
							throw new LookupException(LookupExceptionType.TOO_MANY,
									String.format(plugin.translate("lookup-toomany"), count));
						}
					}

					rs.close();
					pstmt.close();

				} catch (SQLException e) {
					plugin.warning("Error while executing command");
					plugin.warning("SQL Code: " + stmt);
					e.printStackTrace();
					holdingConnectionSince = 0;
					throw new LookupException(LookupExceptionType.GENERAL, plugin.translate("lookup-error"));
				}
				plugin.debug("Completed lookup. Total: " + (System.currentTimeMillis() - lookupStart) + "ms Lookup: "
						+ (parseStart - lookupStart) + "ms Parse: " + (System.currentTimeMillis() - parseStart) + "ms",
						1);

				holdingConnectionSince = 0;
				return output;
			}
		} catch (Exception e) {
			if (e instanceof LookupException) {
				throw e;
			}
			plugin.warning("Error while executing command");
			e.printStackTrace();
			holdingConnectionSince = 0;
			throw new LookupException(LookupExceptionType.GENERAL, plugin.translate("lookup-error"));
		}
	}

	public boolean purge(CommandSender sender, TABLE table, long time) throws SQLException {
		if (!isConnected)
			return false;
		if (time < 1000 * 3600 * 24 * 14) {
			return false;
		}
		synchronized (connection) {
			holdingConnectionSince = System.currentTimeMillis();
			holdingConnection = "purge";
			String stmt = "DELETE FROM " + table.toString();
			stmt += "\nWHERE (time < ";
			stmt += (System.currentTimeMillis() - time);
			stmt += ");";
			plugin.debug(stmt, 1);
			PreparedStatement pstmt = null;
			try {
				pstmt = connection.prepareStatement(stmt);
				pstmt.setFetchSize(500);
				pstmt.execute();

			} catch (SQLException e) {
				e.printStackTrace();
				return false;
			} finally {
				if (pstmt != null) {
					pstmt.close();
				}
			}
			holdingConnectionSince = 0;
		}
		return true;
	}

	public void removeEntry(TABLE table, DbEntry entry) {
		if (!isConnected)
			return;
		String stmt = "DELETE FROM " + table.toString()
				+ "\nWHERE time = ? AND user = ? AND action_id = ? AND world_id = ? AND x = ? AND y = ? AND z = ? AND target = ?;";

		plugin.debug(stmt, 3);
		try {
			PreparedStatement statement = connection.prepareStatement(stmt);

			int i = 1;
			statement.setLong(i++, entry.getTime());
			statement.setString(i++, entry.userUuid.toLowerCase());
			statement.setInt(i++, entry.getAction().getId(entry.getState()));
			statement.setInt(i++, getWid(entry.world));
			statement.setInt(i++, entry.x);
			statement.setInt(i++, entry.y);
			statement.setInt(i++, entry.z);
			statement.setString(i++, entry.targetUuid);
			statement.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private String stmtForRadius(Location location, String radiusStr, boolean specifyWorld, boolean exact) {
		int radius = -1;
		try {
			radius = Integer.parseInt(radiusStr);
		} catch (NumberFormatException e) {

		}
		if (radius < 0 || radius > 250) {
			return null;
		}
		String stmt = "(";
		if (location != null) {
			stmt += "x BETWEEN " + (location.getBlockX() - radius) + " AND " + (location.getBlockX() + radius);
			stmt += " AND ";
			if (exact) {
				stmt += "y BETWEEN " + (location.getBlockY() - radius) + " AND " + (location.getBlockY() + radius);
				stmt += " AND ";
			}
			stmt += "z BETWEEN " + (location.getBlockZ() - radius) + " AND " + (location.getBlockZ() + radius);
			if (specifyWorld) {
				stmt += " AND ";
				stmt += "world_id = " + getWid(location.getWorld().getName());
			}
		}
		stmt += ")";
		return stmt;
	}

	public String getWorld(int wid) {
		for (Entry<String, Integer> entry : worlds.entrySet()) {
			if (entry.getValue() == wid) {
				return entry.getKey();
			}
		}
		return null;
	}

	public void updateUsername(String uuid, String name) {
		if (!uuid.startsWith("$"))
			uuid = "$" + uuid;
		usernames.put(uuid, name);
	}

	HashMap<String, String> usernames = new HashMap<>();

	public String getUsernameFromUUID(String uuid) {
		if (!uuid.startsWith("$"))
			uuid = "$" + uuid;
		if (usernames.containsKey(uuid)) {
			return usernames.get(uuid);
		}
		if (plugin.isBungee()) {
			return uuid;
		} else {
			OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid.substring(1)));
			if (player != null) {
				usernames.put(uuid, player.getName());
				return player.getName();
			}
		}
		HashMap<String, String> params = new HashMap<>();
		params.put("user", uuid);
		params.put("action", "username");

		ArrayList<DbEntry> results = null;
		try {
			results = plugin.getSqlManager().lookup(params, null, false);
		} catch (LookupException e) {
			plugin.warning(e.toString());
		}
		if (results == null)
			return null;
		String newestusername = null;
		long highestusername = 0;
		for (DbEntry entry : results) {
			if (entry.getTime() > highestusername) {
				highestusername = entry.getTime();
				newestusername = entry.getTarget();
			}
		}
		usernames.put(uuid, newestusername);
		return newestusername;
	}

	public String getUuidFromUsername(String username) {
		if (username == null) {
			return null;
		}
		if (usernames.containsValue(username)) {
			return usernames.get(username);
		}
		String username_ = username.toLowerCase();
		for (Entry<String, String> entry : usernames.entrySet()) {
			if (entry.getValue().toLowerCase().equals(username_)) {
				return entry.getKey();
			}
		}
		HashMap<String, String> params = new HashMap<>();
		params.put("target", "@" + username);
		params.put("action", "username");

		ArrayList<DbEntry> results = null;
		try {
			results = plugin.getSqlManager().lookup(params, null, false);
		} catch (LookupException e) {
			plugin.warning(e.toString());
		}
		if (results == null)
			return null;
		String newestUuid = null;
		long highestusername = 0;
		for (DbEntry entry : results) {
			if (entry.getTime() > highestusername) {
				highestusername = entry.getTime();
				newestUuid = entry.userUuid;
			}
		}
		if (newestUuid != null) {
			usernames.put(newestUuid, username);
		}
		return newestUuid;
	}

	public int getWid(String world) {
		if (worlds.containsKey(world)) {
			return worlds.get(world);
		}
		if (Bukkit.getWorld(world) == null) {
			return -1;
		}
		try {

			String stmt = "INSERT INTO " + TABLE.WORLDS.toString() + " (name, wid)";
			stmt += "\nVALUES (?,?)";
			PreparedStatement pstmt = connection.prepareStatement(stmt);
			pstmt.setString(1, world);
			pstmt.setInt(2, nextWid);
			plugin.debug(stmt + "\n" + world + ":" + nextWid, 3);
			pstmt.execute();
			worlds.put(world, nextWid);
			count++;
			return nextWid++;
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return -1;
	}

	public void count() {
		int total = 0;
		plugin.debug("Counting rows..");
		for (TABLE table : TABLE.values()) {
			if (table == TABLE.AUXPROTECT_ABANDONED && !plugin.getAPConfig().isPrivate()) {
				continue;
			}
			synchronized (connection) {
				holdingConnectionSince = System.currentTimeMillis();
				holdingConnection = "count";
				try {
					PreparedStatement pstmt = connection.prepareStatement("SELECT COUNT(1) FROM " + table.toString());
					ResultSet rs = pstmt.executeQuery();
					int count = rs.getInt(1);
					total += count;
					plugin.debug(table.toString() + ": " + count + " rows.");
					rs.close();
					pstmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
				holdingConnectionSince = 0;
			}
		}
		plugin.debug("Counted all tables. " + total + " rows.");
		count = total;
	}
}
