package dev.heliosares.auxprotect.database;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import com.google.common.io.Files;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.MySender;
import dev.heliosares.auxprotect.utils.BidiMapCache;
import dev.heliosares.auxprotect.utils.MovingAverage;

public class SQLManager {
	private static SQLManager instance;
	public static final int DBVERSION = 6;
	public static final int MAX_LOOKUP_SIZE = 500000;

	Connection connection;
	private String targetString;
	private final IAuxProtect plugin;
	private static String tablePrefix;
	private boolean mysql;
	private boolean isConnected;
	private int nextWid;
	private int nextActionId = 10000;
	private BidiMapCache<Integer, String> uuids = new BidiMapCache<>(10000L, 10000L, true);
	private BidiMapCache<Integer, String> usernames = new BidiMapCache<>(10000L, 10000L, true);
	private HashMap<String, Integer> worlds = new HashMap<>();
	private int version;
	private int originalVersion;
	public String holdingConnection;
	public long holdingConnectionSince;
	public MovingAverage putTimePerEntry = new MovingAverage(100);
	public MovingAverage putTimePerExec = new MovingAverage(100);
	public MovingAverage lookupTime = new MovingAverage(100);
	int rowcount;
	private final File sqliteFile;
	private final LookupManager lookupmanager;
	private final MigrationManager migrationmanager;

	public LookupManager getLookupManager() {
		return lookupmanager;
	}

	public static SQLManager getInstance() {
		return instance;
	}

	public static String getTablePrefix() {
		return tablePrefix;
	}

	public int getCount() {
		return rowcount;
	}

	public boolean isConnected() {
		return isConnected;
	}

	public int getVersion() {
		return version;
	}

	public int getOriginalVersion() {
		return originalVersion;
	}

	public boolean isMySQL() {
		return mysql;
	}

	public SQLManager(IAuxProtect plugin, String target, String prefix, File sqliteFile) {
		instance = this;
		this.plugin = plugin;
		this.lookupmanager = new LookupManager(this, plugin);
		this.targetString = target;
		this.migrationmanager = new MigrationManager(this, plugin);
		if (prefix == null || prefix.length() == 0) {
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

	public void connect(String user, String pass) throws SQLException, IOException {
		plugin.info("connecting");
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
		try {
			init();
		} catch (Exception e) {
			if (migrationmanager.isMigrating()) {
				plugin.warning(
						"Error while migrating database. This database will likely not work with the current version. You will need to restore a backup (plugins/AuxProtect/database/backups) and try again. Please contact the plugin developer if you are unable to complete migration.");
			}
			throw e;
		}

		isConnected = true;

	}

	public void connect() throws SQLException, IOException {
		connect(null, null);
	}

	public void close() {
		isConnected = false;
		if (connection != null) {
			// checkAsync();
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

	public String backup() throws IOException {
		if (mysql) {
			return null;
		}
		checkAsync();
		synchronized (connection) {
			File backup = new File(sqliteFile.getParentFile(),
					"backups/backup-v" + version + "-" + System.currentTimeMillis() + ".db");
			if (!backup.getParentFile().exists()) {
				backup.getParentFile().mkdirs();
			}
			Files.copy(sqliteFile, backup);
			return backup.getAbsolutePath();
		}
	}

	private void init() throws SQLException, IOException {
		checkAsync();
		synchronized (connection) {
			holdingConnectionSince = System.currentTimeMillis();
			holdingConnection = "init";

//			try {
//				execute("ALTER TABLE version RENAME TO " + Table.AUXPROTECT_VERSION.toString());
//			} catch (SQLException ignored) {
//			}

			execute("CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_VERSION + " (time BIGINT,version INTEGER);");

			String stmt = "SELECT * FROM " + Table.AUXPROTECT_VERSION + ";";
			plugin.debug(stmt, 3);
			try (Statement statement = connection.createStatement()) {
				try (ResultSet results = statement.executeQuery(stmt)) {
					long newestVersionTime = 0;
					long oldestVersionTime = Long.MAX_VALUE;
					while (results.next()) {
						long versionTime_ = results.getLong("time");
						int version_ = results.getInt("version");
						if (versionTime_ > newestVersionTime) {
							version = version_;
							newestVersionTime = versionTime_;
						}
						if (versionTime_ < oldestVersionTime) {
							originalVersion = version_;
							oldestVersionTime = versionTime_;
						}
						plugin.debug("Version at " + versionTime_ + " was v" + version_ + ".", 1);
					}
				}
			}

			if (version < 1) {
				setVersion(DBVERSION);
			}

			migrationmanager.preTables();

			for (Table table : Table.values()) {
				if (table.hasAPEntries()) {
					execute(table.getSQLCreateString(plugin));
				}
			}
//
			if (!plugin.isBungee()) {
				stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_INVBLOB.toString();
				stmt += " (time BIGINT(255), `blob` MEDIUMBLOB);";
				execute(stmt);

				stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_WORLDS.toString();
				stmt += " (name varchar(255), wid SMALLINT);";
				execute(stmt);

				stmt = "SELECT * FROM " + Table.AUXPROTECT_WORLDS.toString() + ";";
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

			stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_API_ACTIONS.toString()
					+ " (name varchar(255), nid SMALLINT, pid SMALLINT, ntext varchar(255), ptext varchar(255));";
			execute(stmt);

			stmt = "SELECT * FROM " + Table.AUXPROTECT_API_ACTIONS.toString() + ";";
			plugin.debug(stmt, 3);
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

			stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_UIDS.toString();
			if (this.isMySQL()) {
				stmt += " (uid INTEGER AUTO_INCREMENT, uuid varchar(255), PRIMARY KEY (uid));";
			} else {
				stmt += " (uuid varchar(255), uid INTEGER PRIMARY KEY AUTOINCREMENT);";
			}
			plugin.debug(stmt, 3);
			execute(stmt);

			migrationmanager.postTables();

			plugin.debug("init done.");
			holdingConnectionSince = 0;
		}
	}

	void setVersion(int version) throws SQLException {
		execute("INSERT INTO " + Table.AUXPROTECT_VERSION + " (time,version) VALUES (" + System.currentTimeMillis()
				+ "," + (this.version = version) + ")");
		plugin.info("Done migrating to version " + version);
	}

	private Set<Integer> getAllDistinctUIDs(Table table) throws SQLException {
		boolean hasTargetId = !table.hasStringTarget() && table != Table.AUXPROTECT_UIDS;
		String[] columns = new String[hasTargetId ? 2 : 1];
		columns[0] = "uid";
		if (hasTargetId) {
			columns[1] = "target_id";
		}
		Set<Integer> inUseUids = new HashSet<>();
		for (String column : columns) {
			String stmt = "SELECT DISTINCT " + column + " FROM " + table.toString();
			plugin.debug(stmt, 3);
			try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
				pstmt.setFetchSize(500);
				try (ResultSet results = pstmt.executeQuery()) {
					while (results.next()) {
						int uid = results.getInt(column);
						inUseUids.add(uid);
					}
				}
			}
		}
		return inUseUids;
	}

	public void purgeUIDs() throws SQLException {
		synchronized (connection) {
			Set<Integer> inUseUids = new HashSet<>();
			for (Table table : Table.values()) {
				if (!table.hasAPEntries() || !table.exists(plugin)) {
					continue;
				}

				inUseUids.addAll(getAllDistinctUIDs(table));
			}
			Set<Integer> savedUids = getAllDistinctUIDs(Table.AUXPROTECT_UIDS);
			plugin.debug(savedUids.size() + " total UIDs");
			plugin.debug(inUseUids.size() + " currently in use UIDs");
			savedUids.removeAll(inUseUids);
			plugin.debug("Purging " + savedUids.size() + " UIDs");
			int i = 0;
			final String hdr = "DELETE FROM " + Table.AUXPROTECT_UIDS.toString() + " WHERE uid IN ";
			String stmt = "";
			for (int uid : savedUids) {
				if (!stmt.isEmpty()) {
					stmt += ",";
				}
				plugin.debug("Purging UID " + uid, 5);
				stmt += uid;
				if (++i >= 1000) {
					execute(hdr + "(" + stmt + ")");
					stmt = "";
					i = 0;
				}
			}
			if (!stmt.isEmpty())
				execute(hdr + "(" + stmt + ")");
		}
	}

	public void vacuum() throws SQLException {
		synchronized (connection) {
			execute("VACUUM;");
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

	public List<List<String>> executeUpdate(String string, String... args) throws SQLException {
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

	public Connection getConnection() {
		return connection;
	}

	protected void put(Table table, ArrayList<DbEntry> entries) throws SQLException {
		long start = System.nanoTime();
		checkAsync();
		synchronized (connection) {
			holdingConnectionSince = System.currentTimeMillis();
			holdingConnection = "put";
			String stmt = "INSERT INTO " + table.toString() + " ";
			int numColumns = table.getNumColumns(plugin.isBungee());
			String inc = Table.getValuesTemplate(numColumns);
			final boolean hasLocation = plugin.isBungee() ? false : table.hasLocation();
			final boolean hasData = table.hasData();
			final boolean hasAction = table.hasActionId();
			final boolean hasLook = table.hasLook();
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
			HashMap<Long, byte[]> blobsToLog = new HashMap<>();
			try (PreparedStatement statement = connection.prepareStatement(stmt)) {

				int i = 1;
				for (DbEntry dbEntry : entries) {
					int prior = i;
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
					if (hasLook) {
						statement.setInt(i++, dbEntry.pitch);
						statement.setInt(i++, dbEntry.yaw);
					}
					if (table.hasStringTarget()) {
						statement.setString(i++, dbEntry.getTargetUUID());
					} else {
						statement.setInt(i++, getUIDFromUUID(dbEntry.getTargetUUID(), true));
					}
					if (dbEntry instanceof XrayEntry) {
						statement.setShort(i++, ((XrayEntry) dbEntry).getRating());
					}
					if (hasData) {
						statement.setString(i++, dbEntry.getData());
					}
					if (table.hasBlob()) {
						statement.setBoolean(i++, dbEntry.hasBlob());
						if (dbEntry.hasBlob()) {
							blobsToLog.put(dbEntry.getTime(), dbEntry.getBlob());
						}
					}
					if (i - prior != numColumns) {
						plugin.warning("Incorrect number of columns provided inserting action "
								+ dbEntry.getAction().toString() + " into " + table.toString());
						plugin.warning(i - prior + " =/= " + numColumns);
						plugin.warning("Statement: " + stmt);
						throw new IllegalArgumentException();
					}
				}

				statement.executeUpdate();
			}
			if (table.hasBlob() && blobsToLog.size() > 0) {
				putBlobs(blobsToLog);
			}

			rowcount += entries.size();
			holdingConnectionSince = 0;
			this.putTimePerEntry.addData((System.nanoTime() - start) / (double) entries.size());
			this.putTimePerExec.addData(System.nanoTime() - start);
		}
	}

	private String getSize(double bytes) {
		int oom = 0;
		while (bytes > 1024) {
			bytes /= 1024;
			oom++;
		}
		String out = "";
		switch (oom) {
		case 0:
			out = "B";
			break;
		case 1:
			out = "KB";
			break;
		case 2:
			out = "MB";
			break;
		case 3:
			out = "GB";
			break;
		case 4:
			out = "TB";
			break;
		}
		return (Math.round(bytes * 100.0) / 100.0) + " " + out;
	}

	void putBlobs(HashMap<Long, byte[]> blobsToLog) throws SQLException {
		plugin.debug("Logging " + blobsToLog.size() + " blobs");
		HashMap<Long, byte[]> subBlobs = new HashMap<>();
		int size = 0;
		Iterator<Entry<Long, byte[]>> it = blobsToLog.entrySet().iterator();
		while (it.hasNext()) {
			Entry<Long, byte[]> entry = it.next();
			if (entry.getValue() == null || entry.getValue().length == 0) {
				continue;
			}
			if (size + entry.getValue().length > 16777215 || subBlobs.size() >= 1000) {
				if (subBlobs.size() == 0) {
					plugin.warning("Blob too big. Skipping. " + entry.getKey() + "e");
					continue;
				}
				plugin.debug("Logging " + subBlobs.size() + " blobs, " + getSize(size));
				putBlobs_(subBlobs);
				subBlobs.clear();
				size = 0;
			}
			size += entry.getValue().length;
			subBlobs.put(entry.getKey(), entry.getValue());
		}
		if (!subBlobs.isEmpty()) {
			plugin.debug("Logging " + subBlobs.size() + " blobs, " + getSize(size));
			putBlobs_(subBlobs);
		}
	}

	private void putBlobs_(HashMap<Long, byte[]> blobsToLog) throws SQLException {
		String stmt = "INSERT INTO " + Table.AUXPROTECT_INVBLOB.toString() + " (time, blob) VALUES ";
		for (int i = 0; i < blobsToLog.size(); i++) {
			stmt += "\n(?, ?),";
		}

		try (PreparedStatement statement = connection.prepareStatement(stmt.substring(0, stmt.length() - 1))) {
			int i = 1;
			for (Entry<Long, byte[]> entry : blobsToLog.entrySet()) {
				plugin.debug("blob: " + entry.getKey(), 5);
				statement.setLong(i++, entry.getKey());
				if (mysql) {
					Blob blob = connection.createBlob();
					blob.setBytes(1, entry.getValue());
					statement.setBlob(i++, blob);
				} else {
					statement.setBytes(i++, entry.getValue());
				}
			}

			statement.executeUpdate();
		}
	}

	/**
	 * Replaced with LookupManager#lookup
	 */
	@Deprecated
	public ArrayList<DbEntry> lookup(HashMap<String, String> params, Location location, boolean exact)
			throws LookupManager.LookupException {
		if (!isConnected)
			return null;

		try {
			Table table = null;
			Table forcedTable = null;

			HashMap<String, ArrayList<String>> dos = new HashMap<>();
			HashMap<String, ArrayList<String>> donts = new HashMap<>();
			ArrayList<String> writeParams = new ArrayList<>();
			ArrayList<String> targets = new ArrayList<>();
			boolean targetNot = false;
			ArrayList<String> datas = new ArrayList<>();
			boolean dataNot = false;
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
					if (value.endsWith("e")) {
						dos_.add("time = " + value.substring(0, value.length() - 1));
						continue;
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
						forcedTable = Table.valueOf(value.toUpperCase());
					} catch (IllegalArgumentException e) {
						throw new LookupManager.LookupException(LookupManager.LookupExceptionType.SYNTAX,
								plugin.translate("lookup-invalid-syntax"));
					}
					continue;
				}
				boolean not = value.startsWith("!");
				if (not) {
					value = value.substring(1);
					if (key.equalsIgnoreCase("action")) {
						throw new LookupManager.LookupException(LookupManager.LookupExceptionType.ACTION_NEGATE,
								plugin.translate("lookup-action-negate"));
					}
				}
				String build = "";
				boolean escape = false;
				final boolean allowEscape = key.equalsIgnoreCase("target") || key.equalsIgnoreCase("data");
				ArrayList<String> values = new ArrayList<>();
				for (char current : value.toCharArray()) {
					if (allowEscape && current == '\\') {
						escape = true;
						continue;
					}
					if (!escape && current == ',') {
						values.add(build);
						build = "";
						continue;
					}
					if (escape && current != ',') {
						build += '\\';
					}
					build += current;

					if (escape) {
						escape = false;
					}
				}
				if (escape) {
					build += '\\';
				}
				if (build.length() > 0) {
					values.add(build);
				}
				for (String param : values) {
					String theStmt = "";
					if (key.equalsIgnoreCase("target")) {
						if (not) {
							targetNot = true;
						}
						targets.add(param);
					} else if (key.equalsIgnoreCase("data")) {
						if (not) {
							dataNot = true;
						}
						datas.add(param);
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
							throw new LookupManager.LookupException(LookupManager.LookupExceptionType.PLAYER_NOT_FOUND,
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
						EntryAction action = EntryAction.getAction(param);
						if (action == null || !action.isEnabled()) {
							throw new LookupManager.LookupException(LookupManager.LookupExceptionType.UNKNOWN_ACTION,
									String.format(plugin.translate("lookup-unknownaction"), param));
						} else {
							if (table == null) {
								table = action.getTable();
							} else {
								if (table != action.getTable()) {
									throw new LookupManager.LookupException(
											LookupManager.LookupExceptionType.ACTION_INCOMPATIBLE,
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
							throw new LookupManager.LookupException(LookupManager.LookupExceptionType.UNKNOWN_WORLD,
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
				table = Table.AUXPROTECT_MAIN;
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
						throw new LookupManager.LookupException(LookupManager.LookupExceptionType.PLAYER_NOT_FOUND,
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
			ArrayList<String> dataArray = new ArrayList<>();
			for (String data : datas) {
				String theStmt = "";
				if (data.contains("*")) {
					theStmt = "data LIKE ? OR data LIKE ?";
					writeParams.add(data.replaceAll("-", " ").replaceAll("\\*", "%"));
					writeParams.add(data.replaceAll("\\*", "%"));
				} else {
					if (theStmt.length() > 0) {
						theStmt += " OR ";
					}
					theStmt += "lower(data) = ? OR lower(data) = ?";
					writeParams.add(data.toLowerCase());
					writeParams.add(data.toLowerCase().replaceAll("-", " "));
				}

				if (theStmt.length() > 0) {
					dataArray.add(theStmt);
				}
			}
			if (dataArray.size() > 0) {
				if (dataNot) {
					donts.put("data", dataArray);
				} else {
					dos.put("data", dataArray);
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
				if (dos.size() > 0) {
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

			if (table == Table.AUXPROTECT_WORLDS) {
				return null;
			} else {
				stmt = "SELECT * FROM " + table.toString() + stmt;
			}
			stmt += "\nORDER BY time DESC\nLIMIT " + (MAX_LOOKUP_SIZE + 1) + ";";

			return lookup(table, stmt, writeParams);
		} catch (Exception e) {
			if (e instanceof LookupManager.LookupException) {
				throw e;
			}
			plugin.warning("Error while executing command");
			plugin.print(e);
			holdingConnectionSince = 0;
			throw new LookupManager.LookupException(LookupManager.LookupExceptionType.GENERAL,
					plugin.translate("lookup-error"));
		}
	}

	/**
	 * Performs a SQL Lookup in the table provided with the statement provided.
	 * 
	 * @param table       The table being utilized. This is not user in the
	 *                    statement and is merely provided for entry parsing
	 * @param stmt        The statement to be executed
	 * @param writeParams An in-order array of parameters to be inserted into ? of
	 *                    stmt
	 * @return An ArrayList of the DbEntry's meeting the provided conditions
	 * @throws LookupManager.LookupException
	 */
	public ArrayList<DbEntry> lookup(Table table, String stmt, @Nullable ArrayList<String> writeParams)
			throws LookupManager.LookupException {
		final boolean hasLocation = plugin.isBungee() ? false : table.hasLocation();
		final boolean hasData = table.hasData();
		final boolean hasAction = table.hasActionId();
		final boolean hasLook = table.hasLook();
		long start = System.nanoTime();
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
				if (writeParams != null) {
					int i1 = 1;
					plugin.debug("writeParamsSize: " + writeParams.size());
					for (String param : writeParams) {
						pstmt.setString(i1++, param);
					}
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
						} else if (table == Table.AUXPROTECT_COMMANDS) {
							action_id = EntryAction.COMMAND.id;
						} else if (table == Table.AUXPROTECT_XRAY) {
							action_id = EntryAction.VEIN.id;
						}
						String world = null;
						int x = 0, y = 0, z = 0;
						if (hasLocation) {
							world = this.getWorld(rs.getInt("world_id"));
							x = rs.getInt("x");
							y = rs.getInt("y");
							z = rs.getInt("z");
						}

						int pitch = 0, yaw = 180;
						if (hasLook) {
							pitch = rs.getInt("pitch");
							yaw = rs.getInt("yaw");
						}

						String data = null;
						if (hasData) {
							data = rs.getString("data");
						}
						EntryAction entryAction = EntryAction.getAction(action_id);
						if (entryAction == null) {
							plugin.debug("Unknown action_id: " + action_id, 1);
							continue;
						}
						boolean state = false;
						if (entryAction.hasDual && entryAction.id != action_id) {
							state = true;
						}
						DbEntry entry = null;
						String target = null;
						int target_id = -1;
						if (table.hasStringTarget()) {
							target = rs.getString("target");
						} else {
							target_id = rs.getInt("target_id");
						}
						if (table == Table.AUXPROTECT_XRAY) {
							short rating = rs.getShort("rating");
							entry = new XrayEntry(time, uid, world, x, y, z, target_id, rating, data);
						} else {
							entry = new DbEntry(time, uid, entryAction, state, world, x, y, z, pitch, yaw, target,
									target_id, data);
						}
						if (table.hasBlob() && rs.getBoolean("hasblob")) {
							entry.setHasBlob();
						}

						output.add(entry);
						if (++count >= MAX_LOOKUP_SIZE) {
							throw new LookupManager.LookupException(LookupManager.LookupExceptionType.TOO_MANY,
									String.format(plugin.translate("lookup-toomany"), count));
						}
					}
				}
			} catch (SQLException e) {
				plugin.warning("Error while executing command");
				plugin.warning("SQL Code: " + stmt);
				plugin.print(e);
				holdingConnectionSince = 0;
				throw new LookupManager.LookupException(LookupManager.LookupExceptionType.GENERAL,
						plugin.translate("lookup-error"));
			}
			plugin.debug("Completed lookup. Total: " + (System.currentTimeMillis() - lookupStart) + "ms Lookup: "
					+ (parseStart - lookupStart) + "ms Parse: " + (System.currentTimeMillis() - parseStart) + "ms", 1);

			holdingConnectionSince = 0;
			this.lookupTime.addData(System.nanoTime() - start);
			return output;
		}
	}

	public void purge(MySender sender, Table table, long time) throws SQLException {
		if (!isConnected)
			return;
		if (time < 1000 * 3600 * 24 * 14) {
			return;
		}
		if (table == null) {
			for (Table table1 : Table.values()) {
				if (!table1.hasAPEntries() && table1 != Table.AUXPROTECT_INVBLOB) {
					continue;
				}
				if (!table1.exists(plugin)) {
					continue;
				}
				if (table1 == Table.AUXPROTECT_LONGTERM) {
					continue;
				}
				purge(sender, table1, time);
			}
			return;
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
			}
			holdingConnectionSince = 0;
		}
	}

	public void updateXrayEntry(XrayEntry entry) throws SQLException {
		if (!isConnected)
			return;
		String stmt = "UPDATE " + entry.getAction().getTable().toString();
		stmt += "\nSET rating=?, data=?";
		stmt += "\nWHERE time = ? AND uid = ? AND target_id = ?";

		plugin.debug(stmt, 3);
		checkAsync();
		synchronized (connection) {
			try (PreparedStatement statement = connection.prepareStatement(stmt)) {
				int i = 1;
				statement.setShort(i++, entry.getRating());
				statement.setString(i++, entry.getData());
				statement.setLong(i++, entry.getTime());
				statement.setInt(i++, entry.getUid());
				statement.setInt(i++, entry.getTargetId());
				if (statement.executeUpdate() > 1) {
					plugin.warning("Updated multiple entries when updating the following entry:");
					Results.sendEntry(plugin, plugin.getConsoleSender(), entry, 0, true, true);
				}
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

	public void updateUsernameAndIP(UUID uuid, String name, String ip) {
		final int uid = this.getUIDFromUUID("$" + uuid, true);
		if (uid <= 0) {
			return;
		}
		usernames.put(uid, name);

		checkAsync();
		synchronized (connection) {
			String newestusername = null;
			long newestusernametime = 0;
			boolean newip = true;
			String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM.toString() + " WHERE uid=?;";
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
				plugin.add(new DbEntry("$" + uuid, EntryAction.IP, false, ip, ""));
			}
			if (!name.equalsIgnoreCase(newestusername)) {
				plugin.debug("New username: " + name + " for " + newestusername);
				plugin.add(new DbEntry("$" + uuid, EntryAction.USERNAME, false, name, ""));
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

		String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM.toString()
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

	public HashMap<Long, String> getUsernamesFromUID(int uid) {
		HashMap<Long, String> out = new HashMap<>();
		String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM.toString() + " WHERE action_id=? AND uid=?;";
		plugin.debug(stmt, 3);
		checkAsync();
		synchronized (connection) {
			try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
				pstmt.setInt(1, EntryAction.USERNAME.id);
				pstmt.setInt(2, uid);
				try (ResultSet results = pstmt.executeQuery()) {
					while (results.next()) {
						long time = results.getLong("time");
						String username = results.getString("target");
						if (username != null) {
							out.put(time, username);
						}
					}
				} catch (SQLException e) {
					plugin.print(e);
				}
			} catch (SQLException e) {
				plugin.print(e);
			}
		}

		return out;
	}

	public int getUIDFromUsername(String username) {
		if (username == null) {
			return -1;
		}
		if (usernames.containsValue(username)) {
			return usernames.getKey(username);
		}
		String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM.toString()
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

				String stmt = "INSERT INTO " + Table.AUXPROTECT_WORLDS.toString() + " (name, wid)";
				stmt += "\nVALUES (?,?)";
				PreparedStatement pstmt = connection.prepareStatement(stmt);
				pstmt.setString(1, world);
				pstmt.setInt(2, nextWid);
				plugin.debug(stmt + "\n" + world + ":" + nextWid, 3);
				pstmt.execute();
				worlds.put(world, nextWid);
				rowcount++;
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
			String stmt = "SELECT * FROM " + Table.AUXPROTECT_UIDS.toString() + " WHERE uuid=?;";
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
				stmt = "INSERT INTO " + Table.AUXPROTECT_UIDS.toString() + " (uuid)\nVALUES (?)";
				try (PreparedStatement pstmt = connection.prepareStatement(stmt, Statement.RETURN_GENERATED_KEYS)) {
					pstmt.setString(1, uuid);
					pstmt.execute();

					try (ResultSet result = pstmt.getGeneratedKeys()) {
						if (result.next()) {
							int uid = result.getInt(1);
							uuids.put(uid, uuid);
							plugin.debug("New UUID: " + uuid + ":" + uid, 3);
							rowcount++;
							return uid;
						}
					}
				} catch (SQLException e) {
					plugin.print(e);
				}
			}
		}

		return -1;
	}

	public ArrayList<DbEntry> getAllUnratedXrayRecords(long since) {
		checkAsync();
		String stmt = "SELECT * FROM " + Table.AUXPROTECT_XRAY + " WHERE rating=-1";
		if (since > 0) {
			stmt += " AND time>" + since;
		}
		try {
			return lookup(Table.AUXPROTECT_XRAY, stmt, null);
		} catch (LookupManager.LookupException e) {
			plugin.print(e);
		}
		return null;
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
				String stmt = "SELECT * FROM " + Table.AUXPROTECT_UIDS.toString() + " WHERE uid='" + uid + "';";
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

	public static class AlreadyExistsException extends Exception {
		private static final long serialVersionUID = -4118326876128319175L;
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
	 * 
	 * @throws AlreadyExistsException if the action you are attempting to create
	 *                                already exists or the name is taken.
	 * @throws SQLException           if there is a problem connecting to the
	 *                                database.
	 * @returns The created EntryAction.
	 */
	public EntryAction createAction(String key, String ntext, String ptext)
			throws AlreadyExistsException, SQLException {
		if (EntryAction.getAction(key) != null) {
			throw new AlreadyExistsException();
		}
		int pid = -1;
		int nid = -1;
		EntryAction action = null;
		checkAsync();
		synchronized (connection) {
			if (ptext == null) {
				nid = nextActionId++;
				action = new EntryAction(key, nid, ntext);
			} else {
				nid = nextActionId++;
				pid = nextActionId++;
				action = new EntryAction(key, nid, pid, ntext, ptext);
			}
			try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO " + Table.AUXPROTECT_API_ACTIONS
					+ " (name, nid, pid, ntext, ptext) VALUES (?, ?, ?, ?, ?)")) {
				int i = 1;
				pstmt.setString(i++, key);
				pstmt.setInt(i++, nid);
				pstmt.setInt(i++, pid);
				pstmt.setString(i++, ntext);
				pstmt.setString(i++, ptext);

				pstmt.executeUpdate();
			}
			return action;
		}
	}

	public void count() {
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

	int count(Table table) throws SQLException {
		return count(table.toString());
	}

	int count(String table) throws SQLException {
		String stmtStr = "";
		if (mysql) {
			stmtStr = "SELECT COUNT(*) FROM " + table;
		} else {
			stmtStr = "SELECT COUNT(1) FROM " + table;
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

	public byte[] getBlob(DbEntry entry) {
		String stmt = "SELECT * FROM " + Table.AUXPROTECT_INVBLOB.toString() + " WHERE time=?;";
		plugin.debug(stmt, 3);
		try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
			pstmt.setLong(1, entry.getTime());
			try (ResultSet results = pstmt.executeQuery()) {
				if (results.next()) {
					if (mysql) {
						try (InputStream in = results.getBlob("blob").getBinaryStream()) {
							return in.readAllBytes();
						}
					} else {
						return results.getBytes("blob");
					}
				}
			}
		} catch (SQLException e) {
			plugin.print(e);
		} catch (IOException e) {
			plugin.print(e);
		}
		return null;
	}

	public void cleanup() {
		usernames.cleanup();
		uuids.cleanup();
	}

	boolean checkAsync() {
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
