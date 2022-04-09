package dev.heliosares.auxprotect.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import dev.heliosares.auxprotect.IAuxProtect;
import dev.heliosares.auxprotect.utils.BidiMapCache;
import dev.heliosares.auxprotect.utils.MovingAverage;
import dev.heliosares.auxprotect.utils.MySender;

public class SQLManager {
	private Connection connection;
	private String targetString;
	private final IAuxProtect plugin;
	private static String tablePrefix;
	private boolean mysql;

	private boolean isConnected;

	private HashMap<String, Integer> worlds = new HashMap<>();
	private int nextWid;

	private BidiMapCache<Integer, String> uuids = new BidiMapCache<>(10000L, 10000L, true);
	private BidiMapCache<Integer, String> usernames = new BidiMapCache<>(10000L, 10000L, true);

	private int version;

	private static SQLManager instance;

	public static SQLManager getInstance() {
		return instance;
	}

	public static final int DBVERSION = 3;

	public int getCount() {
		return count;
	}

	public boolean isConnected() {
		return isConnected;
	}

	public int getVersion() {
		return version;
	}

	public boolean isMySQL() {
		return mysql;
	}

	public MovingAverage putTimePerEntry = new MovingAverage(100);
	public MovingAverage putTimePerExec = new MovingAverage(100);
	public MovingAverage lookupTime = new MovingAverage(100);
	private int count;

	public static enum TABLE {
		AUXPROTECT, AUXPROTECT_SPAM, AUXPROTECT_LONGTERM, AUXPROTECT_ABANDONED, AUXPROTECT_INVENTORY, AUXPROTECT_WORLDS,
		AUXPROTECT_UIDS, AUXPROTECT_COMMANDS;

		@Override
		public String toString() {
			return tablePrefix + super.toString().toLowerCase();
		}

		public boolean hasData() {
			switch (this) {
			case AUXPROTECT:
			case AUXPROTECT_INVENTORY:
			case AUXPROTECT_SPAM:
				return true;
			default:
				return false;
			}
		}

		public boolean isOnBungee() {
			switch (this) {
			case AUXPROTECT:
			case AUXPROTECT_COMMANDS:
			case AUXPROTECT_LONGTERM:
				return true;
			default:
				return false;
			}
		}

		public boolean hasLocation() {
			switch (this) {
			case AUXPROTECT:
			case AUXPROTECT_ABANDONED:
			case AUXPROTECT_INVENTORY:
			case AUXPROTECT_SPAM:
			case AUXPROTECT_COMMANDS:
				return true;
			default:
				return false;
			}
		}

		public boolean hasActionId() {
			switch (this) {
			case AUXPROTECT_COMMANDS:
				return false;
			default:
				return true;
			}
		}

		public boolean hasStringTarget() {
			switch (this) {
			case AUXPROTECT_COMMANDS:
			case AUXPROTECT_LONGTERM:
				return true;
			default:
				return false;
			}
		}

		public String getValuesHeader(boolean bungee) {
			if (this == TABLE.AUXPROTECT_LONGTERM) {
				return "(time, uid, action_id, target)";
			} else if (this == TABLE.AUXPROTECT_COMMANDS) {
				if (bungee) {
					return "(time, uid, target)";
				}
				return "(time, uid, world_id, x, y, z, target)";
			} else if (bungee) {
				return "(time, uid, action_id, target_id, data)";
			} else if (this == TABLE.AUXPROTECT || this == TABLE.AUXPROTECT_SPAM
					|| this == TABLE.AUXPROTECT_INVENTORY) {
				return "(time, uid, action_id, world_id, x, y, z, target_id, data)";
			} else if (this == TABLE.AUXPROTECT_ABANDONED) {
				return "(time, uid, action_id, world_id, x, y, z, target_id)";
			}
			return null;
		}

		public String getValuesTemplate(boolean bungee) {
			if (this == TABLE.AUXPROTECT_LONGTERM) {
				return "(?, ?, ?, ?)";
			} else if (this == TABLE.AUXPROTECT_COMMANDS) {
				if (bungee) {
					return "(?, ?, ?)";
				}
				return "(?, ?, ?, ?, ?, ?, ?)";
			} else if (bungee) {
				return "(?, ?, ?, ?, ?)";
			} else if (this == TABLE.AUXPROTECT || this == TABLE.AUXPROTECT_SPAM
					|| this == TABLE.AUXPROTECT_INVENTORY) {
				return "(?, ?, ?, ?, ?, ?, ?, ?, ?)";
			} else if (this == TABLE.AUXPROTECT_ABANDONED) {
				return "(?, ?, ?, ?, ?, ?, ?, ?)";
			}
			return null;
		}

	}

	public String holdingConnection;
	public long holdingConnectionSince;

	public SQLManager(IAuxProtect plugin, String target, String prefix) {
		instance = this;
		this.plugin = plugin;
		this.targetString = target;
		if (prefix == null) {
			tablePrefix = "";
		} else {
			tablePrefix = prefix.replaceAll(" ", "_");
			if (tablePrefix.length() > 0 && !tablePrefix.endsWith("_")) {
				tablePrefix += "_";
			}
		}
	}

	public void connect(String user, String pass) throws SQLException {
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
		}

		if (user != null && pass != null) {
			mysql = true;
			connection = DriverManager.getConnection(targetString, user, pass);
		} else {
			mysql = false;
			connection = DriverManager.getConnection(targetString);
		}
		init();

		isConnected = true;

	}

	public void connect() throws SQLException {
		connect(null, null);
	}

	public void close() {
		isConnected = false;
		if (connection != null) {
			checkAsync();
			synchronized (connection) {
				holdingConnectionSince = System.currentTimeMillis();
				holdingConnection = "close";
				try {
					connection.close();
				} catch (SQLException e) {
					plugin.print(e);
				}
				holdingConnectionSince = 0;
			}
		}
	}

	private void init() throws SQLException {
		checkAsync();
		synchronized (connection) {
			holdingConnectionSince = System.currentTimeMillis();
			holdingConnection = "init";

			execute("CREATE TABLE IF NOT EXISTS version (time BIGINT,version INTEGER);");

			String stmt = "SELECT * FROM version;";
			plugin.debug(stmt, 3);
			try (Statement statement = connection.createStatement()) {
				try (ResultSet results = statement.executeQuery(stmt)) {
					long versionTime = 0;
					while (results.next()) {
						long versionTime_ = results.getLong("time");
						int version_ = results.getInt("version");
						if (versionTime_ > versionTime) {
							version = version_;
							versionTime = versionTime_;
						}
						plugin.debug("Version at " + versionTime_ + " was v" + version_ + ".", 1);
					}
				}
			}

			if (version < 1) {
				execute("INSERT INTO version (time,version) VALUES (" + System.currentTimeMillis() + ","
						+ (version = DBVERSION) + ")");
			}

			if (version < 2 && !plugin.isBungee()) {
				plugin.info("Migrating database to v2");
				execute("ALTER TABLE worlds RENAME TO auxprotect_worlds;");

				execute("\"INSERT INTO version (time,version) VALUES (" + System.currentTimeMillis() + ","
						+ (version = 2) + ")");
				plugin.info("Done migrating.");
			}
			int rowcountformerge = 0;
			TABLE[] migrateTablesV3 = new TABLE[] { TABLE.AUXPROTECT, TABLE.AUXPROTECT_SPAM, TABLE.AUXPROTECT_LONGTERM,
					TABLE.AUXPROTECT_ABANDONED, TABLE.AUXPROTECT_INVENTORY };
			if (plugin.isBungee()) {
				migrateTablesV3 = new TABLE[] { TABLE.AUXPROTECT, TABLE.AUXPROTECT_LONGTERM };
			}

			if (version < 3) {
				plugin.info("Migrating database to v3. DO NOT INTERRUPT");
				for (TABLE table : migrateTablesV3) {
					rowcountformerge += count(table);// TODO: Lock error maybe?
					execute("ALTER TABLE " + table.toString() + " RENAME TO " + table.toString() + "_temp;");
					plugin.info(".");
				}
				plugin.info("Tables renamed");
			}

			stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT.toString() + " (\n";
			stmt += "    time BIGINT(255),\n";
			stmt += "    uid integer,\n";
			stmt += "    action_id SMALLINT,\n";
			if (!plugin.isBungee()) {
				stmt += "    world_id SMALLINT,\n";
				stmt += "    x INTEGER,\n";
				stmt += "    y SMALLINT,\n";
				stmt += "    z INTEGER,\n";
			}
			stmt += "    target_id integer,\n";
			stmt += "    data LONGTEXT\n";
			stmt += ");";
			execute(stmt);

			stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT_LONGTERM.toString() + " (\n";
			stmt += "    time BIGINT(255),\n";
			stmt += "    uid integer,\n";
			stmt += "    action_id SMALLINT,\n";
			stmt += "    target varchar(255)\n";
			stmt += ");";
			execute(stmt);

			stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT_COMMANDS.toString() + " (\n";
			stmt += "    time BIGINT(255),\n";
			stmt += "    uid integer,\n";
			if (!plugin.isBungee()) {
				stmt += "    world_id SMALLINT,\n";
				stmt += "    x INTEGER,\n";
				stmt += "    y SMALLINT,\n";
				stmt += "    z INTEGER,\n";
			}
			stmt += "    target LONGTEXT\n";
			stmt += ");";
			execute(stmt);

			if (!plugin.isBungee()) {
				stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT_INVENTORY.toString() + " (\n";
				stmt += "    time BIGINT(255),\n";
				stmt += "    uid integer,\n";
				stmt += "    action_id SMALLINT,\n";
				stmt += "    world_id SMALLINT,\n";
				stmt += "    x INTEGER,\n";
				stmt += "    y SMALLINT,\n";
				stmt += "    z INTEGER,\n";
				stmt += "    target_id integer,\n";
				stmt += "    data LONGTEXT\n";
				stmt += ");";
				plugin.debug(stmt, 3);
				execute(stmt);

				stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT_SPAM.toString() + " (\n";
				stmt += "    time BIGINT(255),\n";
				stmt += "    uid integer,\n";
				stmt += "    action_id SMALLINT,\n";
				stmt += "    world_id SMALLINT,\n";
				stmt += "    x INTEGER,\n";
				stmt += "    y SMALLINT,\n";
				stmt += "    z INTEGER,\n";
				stmt += "    target_id integer,\n";
				stmt += "    data LONGTEXT\n";
				stmt += ");";
				plugin.debug(stmt, 3);
				execute(stmt);

				if (plugin.getAPConfig().isPrivate()) {
					stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT_ABANDONED.toString() + " (\n";
					stmt += "    time BIGINT(255),\n";
					stmt += "    uid integer,\n";
					stmt += "    action_id SMALLINT,\n";
					stmt += "    world_id SMALLINT,\n";
					stmt += "    x INTEGER,\n";
					stmt += "    y SMALLINT,\n";
					stmt += "    z INTEGER,\n";
					stmt += "    target_id integer\n";
					stmt += ");";
					plugin.debug(stmt, 3);
					execute(stmt);
				}

				stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT_WORLDS.toString()
						+ " (name varchar(255), wid SMALLINT);";
				plugin.debug(stmt, 3);
				execute(stmt);

				stmt = "SELECT * FROM " + TABLE.AUXPROTECT_WORLDS.toString() + ";";
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

			stmt = "CREATE TABLE IF NOT EXISTS " + TABLE.AUXPROTECT_UIDS.toString()
					+ " (uuid varchar(255), uid INTEGER PRIMARY KEY AUTOINCREMENT);";
			plugin.debug(stmt, 3);
			execute(stmt);

			if (version < 3) {
				plugin.info("Merging data into new tables...");
				int progress = 0;
				int count = 0;

				for (TABLE table : migrateTablesV3) {
					ArrayList<Object[]> output = new ArrayList<>();
					ArrayList<Object[]> commands = new ArrayList<>();
					final boolean hasLocation = plugin.isBungee() ? false : table.hasLocation();
					final boolean hasData = table.hasData();
					final boolean hasStringTarget = table.hasStringTarget();
					plugin.info("Merging table: " + table.toString());
					stmt = "SELECT * FROM " + table.toString() + "_temp;";
					plugin.debug(stmt, 3);
					try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
						pstmt.setFetchSize(500);
						try (ResultSet results = pstmt.executeQuery()) {
							while (results.next()) {
								ArrayList<Object> entry = new ArrayList<>();
								entry.add(results.getLong("time"));
								entry.add(this.getUIDFromUUID(results.getString("user"), true));
								int action_id = results.getInt("action_id");
								if (action_id != 260) {
									entry.add(action_id);
								}
								if (hasLocation) {
									entry.add(results.getInt("world_id"));
									entry.add(results.getInt("x"));
									entry.add(results.getInt("y"));
									entry.add(results.getInt("z"));
								}
								String target = results.getString("target");
								if (hasStringTarget || action_id == 260) {
									entry.add(target);
								} else {
									entry.add(this.getUIDFromUUID(target, true));
								}
								if (hasData) {
									entry.add(results.getString("data"));
								}

								if (action_id == 260) {
									commands.add(entry.toArray(new Object[0]));
								} else {
									output.add(entry.toArray(new Object[0]));
								}
								if (output.size() >= 5000) {
									this.putRaw(table, output);
									output.clear();
								}
								if (commands.size() >= 5000) {
									this.putRaw(TABLE.AUXPROTECT_COMMANDS, commands);
									commands.clear();
								}
								count++;
								int progressPercentage = (int) Math.floor((double) count / rowcountformerge * 100);
								if (progressPercentage / 5 > progress) {
									progress = progressPercentage / 5;
									plugin.info("Migration " + progress * 5 + "% complete. (" + count + "/"
											+ rowcountformerge + "). DO NOT INTERRUPT");
								}
							}
						}
					}
					if (output.size() > 0) {
						this.putRaw(table, output);
					}
					if (commands.size() > 0) {
						this.putRaw(TABLE.AUXPROTECT_COMMANDS, commands);
					}
				}

				execute("INSERT INTO version (time,version) VALUES (" + System.currentTimeMillis() + "," + (version = 3)
						+ ")");
				plugin.info("Done migrating.");
			}
			plugin.debug("Purging temporary tables");
			for (TABLE table : TABLE.values()) {
				execute("DROP TABLE IF EXISTS " + table.toString() + "temp;");
				execute("DROP TABLE IF EXISTS " + table.toString() + "_temp;");
			}

			plugin.debug("init done.", 1);
			holdingConnectionSince = 0;
		}
	}

	public void purgeUIDs() {
		plugin.info("Performing UID purge");
		Set<Integer> inUseUids = new HashSet<>();
		Set<Integer> savedUids = new HashSet<>();

		synchronized (connection) {
			for (TABLE table : new TABLE[] { TABLE.AUXPROTECT, TABLE.AUXPROTECT_SPAM, TABLE.AUXPROTECT_LONGTERM,
					TABLE.AUXPROTECT_ABANDONED, TABLE.AUXPROTECT_INVENTORY, TABLE.AUXPROTECT_UIDS }) {
				try {

					boolean hasTargetId = !table.hasStringTarget() && table != TABLE.AUXPROTECT_UIDS;
					String stmt = "SELECT uid" + (hasTargetId ? ", target_id" : "") + " FROM " + table.toString() + ";";
					plugin.debug(stmt, 3);
					try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
						pstmt.setFetchSize(500);
						try (ResultSet results = pstmt.executeQuery()) {
							while (results.next()) {
								int uid = results.getInt("uid");
								if (table == TABLE.AUXPROTECT_UIDS) {
									savedUids.add(uid);
									continue;
								}
								inUseUids.add(uid);
								if (hasTargetId) {
									int target_id = results.getInt("target_id");
									inUseUids.add(target_id);
								}
							}
						}
					}
				} catch (SQLException ignored) {
				}
			}
			plugin.debug(savedUids.size() + " saved UIDS");
			plugin.debug(inUseUids.size() + " in use UIDS");
			int i = 0;
			final String hdr = "DELETE FROM " + TABLE.AUXPROTECT_UIDS.toString() + " WHERE ";
			String stmt = "";
			try {
				for (int uid : savedUids) {
					if (inUseUids.contains(uid)) {
						continue;
					}
					if (!stmt.isEmpty()) {
						stmt += " OR ";
					}
					plugin.debug("Purging UID " + uid, 5);
					stmt += "uid=" + uid;
					if (++i >= 1000) {
						execute(hdr + stmt);
						stmt = "";
						i = 0;
					}
				}
				if (!stmt.isEmpty())
					execute(hdr + stmt);
			} catch (SQLException e) {

			}
		}
		plugin.info("UID purge complete.");
	}

	public void vacuum() throws SQLException {
		synchronized (connection) {
			execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "last_vacuum (time BIGINT);");

			String stmt = "SELECT * FROM " + tablePrefix + "last_vacuum;";
			plugin.debug(stmt, 3);
			long lastvacuum = 0;
			int vacuumcount = 0;
			try (Statement statement = connection.createStatement()) {
				try (ResultSet results = statement.executeQuery(stmt)) {
					while (results.next()) {
						long time = results.getLong("time");
						if (time > lastvacuum) {
							lastvacuum = time;
						}
						vacuumcount++;
					}
				}
			}

			if (System.currentTimeMillis() - lastvacuum > 1000L * 3600L * 24L * 30L) {
				plugin.info(
						"Performing vacuum operation. Please do not shutdown your server or unload the plugin. This may take a while...");
				execute("VACUUM;");
				if (vacuumcount == 0) {
					execute("INSERT INTO " + tablePrefix + "last_vacuum (time) VALUES (" + System.currentTimeMillis()
							+ ");");
				} else {
					execute("UPDATE " + tablePrefix + "last_vacuum SET time=" + System.currentTimeMillis() + ";");
				}
				plugin.info("Vacuum complete.");
			}
		}
	}

	public void execute(String stmt) throws SQLException {
		plugin.debug(stmt, 2);
		checkAsync();
		synchronized (connection) {
			try (Statement statement = connection.createStatement()) {
				statement.execute(stmt);
			}
		}
	}

	public List<List<String>> executeUpdate(String string) throws SQLException {
		plugin.debug(string, 2);
		final List<List<String>> rowList = new LinkedList<List<String>>();
		checkAsync();
		synchronized (connection) {
			try (Statement statement = connection.createStatement()) {
				try (ResultSet rs = statement.executeQuery(string)) {
					final ResultSetMetaData meta = rs.getMetaData();
					final int columnCount = meta.getColumnCount();
					while (rs.next()) {
						final List<String> columnList = new LinkedList<String>();
						rowList.add(columnList);

						for (int column = 1; column <= columnCount; ++column) {
							final Object value = rs.getObject(column);
							columnList.add(String.valueOf(value));
						}
					}
				}
			}
		}
		return rowList;
	}

	private void putRaw(TABLE table, ArrayList<Object[]> datas)
			throws SQLException, ClassCastException, IndexOutOfBoundsException {
		checkAsync();
		synchronized (connection) {
			holdingConnectionSince = System.currentTimeMillis();
			holdingConnection = "put";
			String stmt = "INSERT INTO " + table.toString() + " ";
			final boolean hasLocation = plugin.isBungee() ? false : table.hasLocation();
			final boolean hasData = table.hasData();
			final boolean hasAction = table.hasActionId();
			stmt += table.getValuesHeader(plugin.isBungee());
			String inc = table.getValuesTemplate(plugin.isBungee());
			stmt += " VALUES";
			for (int i = 0; i < datas.size(); i++) {
				stmt += "\n" + inc;
				if (i + 1 == datas.size()) {
					stmt += ";";
				} else {
					stmt += ",";
				}
			}
			try (PreparedStatement statement = connection.prepareStatement(stmt)) {

				int i = 1;
				for (Object[] data : datas) {
					int y = 0;
					try {
						// statement.setString(i++, table);
						statement.setLong(i++, (long) data[y++]);
						statement.setInt(i++, (int) data[y++]);

						if (hasAction) {
							statement.setInt(i++, (int) data[y++]);
						}
						if (hasLocation) {
							statement.setInt(i++, (int) data[y++]);
							statement.setInt(i++, (int) data[y++]);
							statement.setInt(i++, (int) data[y++]);
							statement.setInt(i++, (int) data[y++]);
						}
						if (table.hasStringTarget()) {
							statement.setString(i++, (String) data[y++]);
						} else {
							statement.setInt(i++, (int) data[y++]);
						}
						if (hasData) {
							statement.setString(i++, (String) data[y++]);
						}
					} catch (Exception e) {
						String error = "";
						for (Object o : data) {
							error += o + ", ";
						}
						plugin.warning(error + "\nError at index " + y);
						throw e;
					}
				}

				statement.executeUpdate();
			}
			count += datas.size();
			holdingConnectionSince = 0;
		}
	}

	protected void put(TABLE table, ArrayList<DbEntry> entries) throws SQLException {
		long start = System.nanoTime();
		checkAsync();
		synchronized (connection) {
			holdingConnectionSince = System.currentTimeMillis();
			holdingConnection = "put";
			String stmt = "INSERT INTO " + table.toString() + " ";
			String inc = table.getValuesTemplate(plugin.isBungee());
			final boolean hasLocation = plugin.isBungee() ? false : table.hasLocation();
			final boolean hasData = table.hasData();
			final boolean hasAction = table.hasActionId();
			stmt += table.getValuesHeader(plugin.isBungee());
			stmt += " VALUES";
			for (int i = 0; i < entries.size(); i++) {
				stmt += "\n" + inc;
				if (i + 1 == entries.size()) {
					stmt += ";";
				} else {
					stmt += ",";
				}
			}
			try (PreparedStatement statement = connection.prepareStatement(stmt)) {

				int i = 1;
				for (DbEntry dbEntry : entries) {
					// statement.setString(i++, table);
					statement.setLong(i++, dbEntry.getTime());
					statement.setInt(i++, getUIDFromUUID(dbEntry.getUserUUID(), true));
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
					if (table.hasStringTarget()) {
						statement.setString(i++, dbEntry.getTargetUUID());
					} else {
						statement.setInt(i++, getUIDFromUUID(dbEntry.getTargetUUID(), true));
					}
					if (hasData) {
						statement.setString(i++, dbEntry.getData());
					}
				}

				statement.executeUpdate();
			}

			count += entries.size();
			holdingConnectionSince = 0;
			this.putTimePerEntry.addData((System.nanoTime() - start) / (double) entries.size());
			this.putTimePerExec.addData(System.nanoTime() - start);
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
		long start = System.nanoTime();
		if (!isConnected)
			return null;

		try {
			TABLE table = null;
			TABLE forcedTable = null;

			HashMap<String, ArrayList<String>> dos = new HashMap<>();
			HashMap<String, ArrayList<String>> donts = new HashMap<>();
			ArrayList<String> writeParams = new ArrayList<>();
			ArrayList<String> targets = new ArrayList<>();
			boolean targetNot = false;
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
					if (key.equalsIgnoreCase("target")) {
						if (not) {
							targetNot = true;
						}
						targets.add(param);
					} else if (key.equalsIgnoreCase("user")) {
						int uid = this.getUIDFromUsername(param);
						int altuid = this.getUIDFromUUID(param);
						if (uid > 0 && altuid > 0) {
							theStmt = "(uid = '" + uid + "' OR uid = '" + altuid + "')";
						} else if (uid > 0) {
							theStmt = "uid = '" + uid + "'";
						} else if (altuid > 0) {
							theStmt = "uid = '" + altuid + "'";
						} else {
							throw new LookupException(LookupExceptionType.PLAYER_NOT_FOUND,
									String.format(plugin.translate("lookup-playernotfound"), param));

						}
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
								table = action.getTable();
							} else {
								if (table != action.getTable()) {
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
						int wid = getWID(param);
						if (wid == -1) {
							throw new LookupException(LookupExceptionType.UNKNOWN_WORLD,
									String.format(plugin.translate("lookup-unknown-world"), param));
						}
						theStmt = "world_id = " + getWID(param);
					} else {
						theStmt = key + " = ?";
						writeParams.add(param);
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
			if (forcedTable != null) {
				table = forcedTable;
			} else if (table == null) {
				table = TABLE.AUXPROTECT;
			}
			ArrayList<String> targetArray = new ArrayList<>();
			for (String target : targets) {
				String theStmt = "";
				if (table.hasStringTarget()) {
					if (target.contains("*")) {
						theStmt = "target LIKE ? OR target LIKE ?";
						writeParams.add(target.replaceAll("-", " ").replaceAll("\\*", "%"));
						writeParams.add(target.replaceAll("\\*", "%"));
					} else {
						if (theStmt.length() > 0) {
							theStmt += " OR ";
						}
						theStmt += "lower(target) = ? OR lower(target) = ?";
						writeParams.add(target.toLowerCase());
						writeParams.add(target.toLowerCase().replaceAll("-", " "));

					}
				} else {
					int uid = this.getUIDFromUsername(target);
					int altuid = this.getUIDFromUUID(target);
					if (uid > 0 && altuid > 0) {
						theStmt = "(target_id = '" + uid + "' OR target_id = '" + altuid + "')";
					} else if (uid > 0) {
						theStmt = "target_id = '" + uid + "'";
					} else if (altuid > 0) {
						theStmt = "target_id = '" + altuid + "'";
					} else {
						throw new LookupException(LookupExceptionType.PLAYER_NOT_FOUND,
								String.format(plugin.translate("lookup-playernotfound"), target));
					}
				}
				if (theStmt.length() > 0) {
					targetArray.add(theStmt);
				}
			}
			if (targetArray.size() > 0) {
				if (targetNot) {
					donts.put("target", targetArray);
				} else {
					dos.put("target", targetArray);
				}
			}
			String stmt = "\nWHERE (";
			int conditions = 0;
			int i = 0;
			if (dos.size() > 0) {
				stmt += "(";
				for (String key : dos.keySet()) {
					if (key.equalsIgnoreCase("action") && !table.hasActionId()) {
						continue;
					}
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
					conditions++;
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
					if (key.equalsIgnoreCase("action") && !table.hasActionId()) {
						continue;
					}
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
					conditions++;
				}
				stmt += ")";
			}
			if (conditions == 0) {
				stmt = "";
			} else {
				stmt += ")";
			}

			final boolean hasLocation = plugin.isBungee() ? false : table.hasLocation();
			final boolean hasData = table.hasData();
			final boolean hasAction = table.hasActionId();
			if (table == TABLE.AUXPROTECT_WORLDS) {
				return null;
			} else {
				stmt = "SELECT * FROM " + table.toString() + stmt;
			}
			stmt += "\nORDER BY time DESC\nLIMIT 100001;";
			plugin.debug(stmt, 3);
			ArrayList<DbEntry> output = new ArrayList<>();
			long parseStart;
			checkAsync();
			synchronized (connection) {
				holdingConnectionSince = System.currentTimeMillis();
				holdingConnection = "lookup";
				long lookupStart = System.currentTimeMillis();
				try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {

					pstmt.setFetchSize(500);
					for (int i1 = 0; i1 < writeParams.size(); i1++) {
						String param = writeParams.get(i1);
						pstmt.setString(i1 + 1, param);
					}
					try (ResultSet rs = pstmt.executeQuery()) {

						int count = 0;
						parseStart = System.currentTimeMillis();
						while (rs.next()) {
							long time = rs.getLong("time");
							int uid = rs.getInt("uid");
							int action_id = -1;
							if (hasAction) {
								action_id = rs.getInt("action_id");
							} else if (table == TABLE.AUXPROTECT_COMMANDS) {
								action_id = EntryAction.COMMAND.id;
							}
							String world = null;
							int x = 0, y = 0, z = 0;
							if (hasLocation) {
								world = this.getWorld(rs.getInt("world_id"));
								x = rs.getInt("x");
								y = rs.getInt("y");
								z = rs.getInt("z");
							}
							String target = null;

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
							DbEntry entry = null;
							if (table.hasStringTarget()) {
								target = rs.getString("target");
								entry = new DbEntry(time, uid, entryAction, state, world, x, y, z, target, data);
							} else {
								int target_id = rs.getInt("target_id");
								entry = new DbEntry(time, uid, entryAction, state, world, x, y, z, target_id, data);
							}

							output.add(entry);
							if (++count >= 100000) {
								throw new LookupException(LookupExceptionType.TOO_MANY,
										String.format(plugin.translate("lookup-toomany"), count));
							}
						}
					}
				} catch (SQLException e) {
					plugin.warning("Error while executing command");
					plugin.warning("SQL Code: " + stmt);
					plugin.print(e);
					holdingConnectionSince = 0;
					throw new LookupException(LookupExceptionType.GENERAL, plugin.translate("lookup-error"));
				}
				plugin.debug("Completed lookup. Total: " + (System.currentTimeMillis() - lookupStart) + "ms Lookup: "
						+ (parseStart - lookupStart) + "ms Parse: " + (System.currentTimeMillis() - parseStart) + "ms",
						1);

				holdingConnectionSince = 0;
				this.lookupTime.addData(System.nanoTime() - start);
				return output;
			}
		} catch (Exception e) {
			if (e instanceof LookupException) {
				throw e;
			}
			plugin.warning("Error while executing command");
			plugin.print(e);
			holdingConnectionSince = 0;
			throw new LookupException(LookupExceptionType.GENERAL, plugin.translate("lookup-error"));
		}
	}

	public boolean purge(MySender sender, TABLE table, long time) throws SQLException {
		if (!isConnected)
			return false;
		if (time < 1000 * 3600 * 24 * 14) {
			return false;
		}
		checkAsync();
		synchronized (connection) {
			holdingConnectionSince = System.currentTimeMillis();
			holdingConnection = "purge";
			String stmt = "DELETE FROM " + table.toString();
			stmt += "\nWHERE (time < ";
			stmt += (System.currentTimeMillis() - time);
			stmt += ");";
			plugin.debug(stmt, 1);
			try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
				pstmt.setFetchSize(500);
				pstmt.execute();
			} catch (SQLException e) {
				plugin.print(e);
				return false;
			}
			holdingConnectionSince = 0;
		}
		return true;
	}

	public void removeEntry(TABLE table, DbEntry entry) {
		if (!isConnected)
			return;
		String stmt = "DELETE FROM " + table.toString()
				+ "\nWHERE time = ? AND uid = ? AND action_id = ? AND world_id = ? AND x = ? AND y = ? AND z = ?;";

		plugin.debug(stmt, 3);
		checkAsync();
		synchronized (connection) {
			try {
				PreparedStatement statement = connection.prepareStatement(stmt);

				int i = 1;
				statement.setLong(i++, entry.getTime());
				statement.setInt(i++, entry.getUid());
				statement.setInt(i++, entry.getAction().getId(entry.getState()));
				statement.setInt(i++, getWID(entry.world));
				statement.setInt(i++, entry.x);
				statement.setInt(i++, entry.y);
				statement.setInt(i++, entry.z);
				statement.executeUpdate();
			} catch (SQLException e) {
				plugin.print(e);
			}
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
				stmt += "world_id = " + getWID(location.getWorld().getName());
			}
		}
		stmt += ")";
		return stmt;
	}

	public void updateUsernameAndIP(String uuid, String name, String ip) {
		if (!uuid.startsWith("$")) {
			uuid = "$" + uuid;
		}
		final int uid = this.getUIDFromUUID(uuid, true);
		if (uid <= 0) {
			return;
		}
		usernames.put(uid, name);

		checkAsync();
		synchronized (connection) {
			String newestusername = null;
			long newestusernametime = 0;
			boolean newip = true;
			String stmt = "SELECT * FROM " + TABLE.AUXPROTECT_LONGTERM.toString() + " WHERE uid=?;";
			plugin.debug(stmt, 3);
			try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
				pstmt.setInt(1, uid);
				try (ResultSet results = pstmt.executeQuery()) {
					while (results.next()) {
						String target = results.getString("target");
						if (target == null) {
							continue;
						}
						long time = results.getLong("time");
						int action_id = results.getInt("action_id");
						if (action_id == EntryAction.IP.id) {
							if (target.equals(ip)) {
								newip = false;
							}
						} else if (action_id == EntryAction.USERNAME.id) {
							if (time > newestusernametime) {
								newestusername = target;
								newestusernametime = time;
							}
						}
					}
				} catch (SQLException e) {
					plugin.print(e);
				}
			} catch (SQLException e) {
				plugin.print(e);
			}
			if (newip) {
				plugin.add(new DbEntry(uuid, EntryAction.IP, false, "", 0, 0, 0, ip, ""));
			}
			if (!name.equalsIgnoreCase(newestusername)) {
				plugin.debug("New username: " + name + " for " + newestusername);
				plugin.add(new DbEntry(uuid, EntryAction.USERNAME, false, "", 0, 0, 0, name, ""));
			}
		}

	}

	public String getUsernameFromUID(int uid) {
		if (uid < 0) {
			return null;
		}
		if (uid == 0) {
			return "";
		}
		if (usernames.containsKey(uid)) {
			return usernames.get(uid);
		}
		/*
		 * if (plugin.isBungee()) { return uuid; } else { OfflinePlayer player =
		 * Bukkit.getOfflinePlayer(UUID.fromString(uuid.substring(1))); if (player !=
		 * null) { usernames.put(uuid, player.getName()); return player.getName
		 */

		String stmt = "SELECT * FROM " + TABLE.AUXPROTECT_LONGTERM.toString()
				+ " WHERE action_id=? AND uid=?\nORDER BY time DESC\nLIMIT 1;";
		plugin.debug(stmt, 3);
		checkAsync();
		synchronized (connection) {
			try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
				pstmt.setInt(1, EntryAction.USERNAME.id);
				pstmt.setInt(2, uid);
				try (ResultSet results = pstmt.executeQuery()) {
					if (results.next()) {
						String username = results.getString("target");
						plugin.debug("Resolved UID " + uid + " to " + username, 5);
						if (username != null) {
							usernames.put(uid, username);
							return username;
						}
					}
				} catch (SQLException e) {
					plugin.print(e);
				}
			} catch (SQLException e) {
				plugin.print(e);
			}
		}
		return null;
	}

	public int getUIDFromUsername(String username) {
		if (username == null) {
			return -1;
		}
		if (usernames.containsValue(username)) {
			return usernames.getKey(username);
		}
		String stmt = "SELECT * FROM " + TABLE.AUXPROTECT_LONGTERM.toString()
				+ " WHERE action_id=? AND lower(target)=?\nORDER BY time DESC\nLIMIT 1;";
		plugin.debug(stmt, 3);

		checkAsync();
		synchronized (connection) {
			try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
				pstmt.setInt(1, EntryAction.USERNAME.id);
				pstmt.setString(2, username.toLowerCase());
				try (ResultSet results = pstmt.executeQuery()) {
					if (results.next()) {
						int uid = results.getInt("uid");
						String username_ = results.getString("target");
						plugin.debug("Resolved username " + username_ + " to UID " + uid, 5);
						if (username_ != null && uid > 0) {
							usernames.put(uid, username_);
							return uid;
						}
					}
				} catch (SQLException e) {
					plugin.print(e);
				}
			} catch (SQLException e) {
				plugin.print(e);
			}
		}
		plugin.debug("Unknown UID for " + username, 3);
		return -1;
	}

	public int getWID(String world) {
		if (worlds.containsKey(world)) {
			return worlds.get(world);
		}
		if (Bukkit.getWorld(world) == null) {
			return -1;
		}
		checkAsync();
		synchronized (connection) {
			try {

				String stmt = "INSERT INTO " + TABLE.AUXPROTECT_WORLDS.toString() + " (name, wid)";
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
				plugin.print(e);
			}
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

	public int getUIDFromUUID(String uuid) {
		return getUIDFromUUID(uuid, false);
	}

	public int getUIDFromUUID(String uuid, boolean insert) {
		if (uuid == null || uuid.equalsIgnoreCase("#null")) {
			return -1;
		}
		if (uuid.length() == 0) {
			return 0;
		}
		uuid = uuid.toLowerCase();
		if (uuids.containsValue(uuid)) {
			return uuids.getKey(uuid);
		}
		checkAsync();
		synchronized (connection) {
			String stmt = "SELECT * FROM " + TABLE.AUXPROTECT_UIDS.toString() + " WHERE uuid=?;";
			plugin.debug(stmt, 3);
			try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
				pstmt.setString(1, uuid);
				try (ResultSet results = pstmt.executeQuery()) {
					if (results.next()) {
						int uid = results.getInt("uid");
						uuids.put(uid, uuid);
						return uid;
					}
				} catch (SQLException e) {
					plugin.print(e);
				}
			} catch (SQLException e) {
				plugin.print(e);
			}
			if (insert) {
				stmt = "INSERT INTO " + TABLE.AUXPROTECT_UIDS.toString() + " (uuid)\nVALUES (?)";
				try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
					pstmt.setString(1, uuid);
					pstmt.execute();

					try (ResultSet result = pstmt.getGeneratedKeys()) {
						if (result.next()) {
							int uid = result.getInt(1);
							uuids.put(uid, uuid);
							plugin.debug("New UUID: " + uuid + ":" + uid, 3);
							count++;
							return uid++;
						}
					}
				} catch (SQLException e) {
					plugin.print(e);
				}
			}
		}

		return -1;
	}

	public String getUUIDFromUID(int uid) {
		if (uid < 0) {
			return "#null";
		}
		if (uid == 0) {
			return "";
		}
		if (uuids.containsKey(uid)) {
			return uuids.get(uid);
		}

		checkAsync();
		synchronized (connection) {
			try (Statement statement = connection.createStatement()) {
				String stmt = "SELECT * FROM " + TABLE.AUXPROTECT_UIDS.toString() + " WHERE uid='" + uid + "';";
				plugin.debug(stmt, 3);
				try (ResultSet results = statement.executeQuery(stmt)) {
					if (results.next()) {
						String uuid = results.getString("uuid");
						uuids.put(uid, uuid);
						return uuid;
					}
				} catch (SQLException e) {
					plugin.print(e);
				}
			} catch (SQLException e) {
				plugin.print(e);
			}
		}
		return null;
	}

	public void count() throws SQLException {
		int total = 0;
		plugin.debug("Counting rows..");
		for (TABLE table : TABLE.values()) {
			if (plugin.isBungee() && !table.isOnBungee()) {
				continue;
			}
			if (table == TABLE.AUXPROTECT_ABANDONED && !plugin.getAPConfig().isPrivate()) {
				continue;
			}
			total += count(table);
		}
		plugin.debug("Counted all tables. " + total + " rows.");
		count = total;
	}

	public int count(TABLE table) throws SQLException {
		String stmtStr = "";
		if (mysql) {
			stmtStr = "SELECT COUNT(*) FROM " + table.toString();
		} else {
			stmtStr = "SELECT COUNT(1) FROM " + table.toString();
		}
		plugin.debug(stmtStr, 5);
		checkAsync();
		synchronized (connection) {
			holdingConnectionSince = System.currentTimeMillis();
			holdingConnection = "count";
			try (PreparedStatement pstmt = connection.prepareStatement(stmtStr)) {
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						return rs.getInt(1);
					}
				}
			}
			holdingConnectionSince = 0;
		}

		return -1;
	}

	public void cleanup() {
		usernames.cleanup();
		uuids.cleanup();
	}

	private boolean checkAsync() {
		if (plugin.isBungee()) {
			return true;
		}
		boolean sync = Bukkit.isPrimaryThread();
		if (sync) {
			plugin.warning("Synchronous call to database. This may cause lag.");
			if (plugin.getDebug() > 0) {
				Thread.dumpStack();
			}
		}
		return !sync;
	}
}
