package dev.heliosares.auxprotect.bungee;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import dev.heliosares.auxprotect.adapters.BungeeConfigAdapter;
import dev.heliosares.auxprotect.adapters.BungeeSenderAdapter;
import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APConfig;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.database.DatabaseRunnable;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.utils.StackUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class AuxProtectBungee extends Plugin implements IAuxProtect {
	protected Configuration configurationFile;
	public APConfig config;
	public int debug;
	SQLManager sqlManager;
	protected DatabaseRunnable dbRunnable;
	private static AuxProtectBungee instance;

	public AuxProtectBungee() {
		instance = this;
	}

	@Override
	public void onEnable() {
		loadConfig();

		YMLManager langManager = new YMLManager("en-us.yml", this);
		langManager.load();
		Language.load(new BungeeConfigAdapter(langManager.getData()));

		getProxy().getPluginManager().registerCommand(this, new APBCommand(this));
		getProxy().getPluginManager().registerListener(this, new APBListener(this));

		File sqliteFile = null;
		boolean mysql = configurationFile.getBoolean("MySQL.use", false);
		String user = configurationFile.getString("MySQL.username", "");
		String pass = configurationFile.getString("MySQL.password", "");
		String uri = "";
		if (mysql) {
			String host = configurationFile.getString("MySQL.host", "localhost");
			String port = configurationFile.getString("MySQL.port", "3306");
			String database = configurationFile.getString("MySQL.database", "database");
			uri = String.format("jdbc:mysql://%s:%s/%s", host, port, database);
		} else {
			sqliteFile = new File(getDataFolder(), "database/auxprotect.db");
			if (!sqliteFile.getParentFile().exists()) {
				if (!sqliteFile.getParentFile().mkdirs()) {
					this.getLogger().severe("Failed to create database directory.");
					this.onDisable();
					return;
				}
			}
			if (!sqliteFile.exists()) {
				try {
					if (!sqliteFile.createNewFile()) {
						throw new IOException();
					}
				} catch (IOException e) {
					this.getLogger().severe("Failed to create database file.");
					this.onDisable();
					return;
				}
			}
			uri = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
		}
		sqlManager = new SQLManager(this, uri, configurationFile.getString("MySQL.table-prefix"), sqliteFile);

		runAsync(new Runnable() {

			@Override
			public void run() {
				try {
					if (mysql) {
						sqlManager.connect(user, pass);
					} else {
						sqlManager.connect(null, null);
					}
				} catch (Exception e) {
					print(e);
					getLogger().severe("Failed to connect to SQL database. Disabling.");
					onDisable();
					return;
				}

				/*
				 * for (Object command : config.getList("purge-cmds")) { String cmd = (String)
				 * command; String[] argsOld = cmd.split(" "); String[] args = new
				 * String[argsOld.length + 1]; args[0] = "purge"; for (int i = 0; i <
				 * argsOld.length; i++) { args[i + 1] = argsOld[i]; }
				 * PurgeCommand.purge(AuxProtectBungee.this, new
				 * MySender(getProxy().getConsole()), args); } sqlManager.purgeUIDs();
				 * 
				 * try { sqlManager.vacuum(); } catch (SQLException e) { print(e); }
				 */
			}
		});

		dbRunnable = new DatabaseRunnable(this, sqlManager);

		getProxy().getScheduler().schedule(this, dbRunnable, 250, 250, TimeUnit.MILLISECONDS);
	}

	@Override
	public void onDisable() {
		isShuttingDown = true;
		getProxy().getPluginManager().unregisterListeners(this);
		getProxy().getPluginManager().unregisterCommands(this);
		if (dbRunnable != null) {
			dbRunnable.run();
		}
		if (sqlManager != null) {
			sqlManager.close();
		}
	}

	private boolean isShuttingDown;

	@Override
	public boolean isShuttingDown() {
		return isShuttingDown;
	}

	public static void tell(CommandSender to, String message) {
		to.sendMessage(TextComponent.fromLegacyText(message));
	}

	public void loadConfig() {

		if (!getDataFolder().exists())
			getDataFolder().mkdir();

		File file = new File(getDataFolder(), "config.yml");

		if (!file.exists()) {
			try (InputStream in = getResourceAsStream("config.yml")) {
				Files.copy(in, file.toPath());
			} catch (IOException e) {
				print(e);
			}
		}
		try {
			configurationFile = ConfigurationProvider.getProvider(YamlConfiguration.class)
					.load(new File(getDataFolder(), "config.yml"));
		} catch (IOException e) {
			print(e);
		}

		config = new APConfig();
		config.load(this, new BungeeConfigAdapter(configurationFile));
	}

	@Override
	public InputStream getResource(String string) {
		return getResourceAsStream(string);
	}

	public SQLManager getSqlManager() {
		return sqlManager;
	}

	@Override
	public void info(String string) {
		this.getLogger().info(string);
	}

	@Override
	public void debug(String string) {
		debug(string, 1);
	}

	@Override
	public void debug(String string, int verbosity) {
		if (debug >= verbosity) {
			this.info("DEBUG" + verbosity + ": " + string);
		}
	}

	@Override
	public void warning(String message) {
		getLogger().warning(message);
		logToStackLog("[WARNING] " + message);
	}

	@Override
	public void print(Throwable t) {
		getLogger().log(Level.WARNING, t.getMessage(), t);
		String stack = StackUtil.format(t, 3);
		if (stackHashHistory.add(stack.hashCode())) {
			stack = StackUtil.format(t, 20);
		}
		logToStackLog(stack);
	}

	private void logToStackLog(String msg) {
		stackLog += "[" + LocalDateTime.now().format(ERROR_TIME_FORMAT) + "] " + msg + "\n";
	}

	private static final DateTimeFormatter ERROR_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	Set<Integer> stackHashHistory = new HashSet<>();
	private String stackLog = "";

	@Override
	public String getStackLog() {
		return stackLog;
	}

	@Override
	public PlatformType getPlatform() {
		return PlatformType.BUNGEE;
	}

	public static IAuxProtect getInstance() {
		return instance;
	}

	@Override
	public int getDebug() {
		return debug;
	}

	@Override
	public APConfig getAPConfig() {
		return config;
	}

	@Override
	public void add(DbEntry dbEntry) {
		dbRunnable.add(dbEntry);
	}

	@Override
	public void runAsync(Runnable run) {
		getProxy().getScheduler().runAsync(this, run);
	}

	@Override
	public void runSync(Runnable run) {
		runAsync(run);
	}

	public static String getLabel(Object o) {
		if (o == null) {
			return "#null";
		}
		if (o instanceof UUID) {
			return "$" + ((UUID) o).toString();
		}
		if (o instanceof ProxiedPlayer) {
			return "$" + ((ProxiedPlayer) o).getUniqueId().toString();
		}
		return "#null";
	}

	@Override
	public void reloadConfig() {
		loadConfig();
	}

	@Override
	public String getCommandPrefix() {
		return "apb";
	}

	@Override
	public SenderAdapter getConsoleSender() {
		return new BungeeSenderAdapter(this, this.getProxy().getConsole());
	}

	@Override
	public void setDebug(int debug) {
		this.debug = debug;
	}

	@Override
	public boolean isHooked(String name) {
		// TODO zz Future implementation
		return false;
	}

	@Override
	public File getRootDirectory() {
		return getDataFolder();
	}

	@Override
	public String getPlatformVersion() {
		return getProxy().getVersion();
	}

	@Override
	public String getPluginVersion() {
		return this.getDescription().getVersion();
	}

	@Override
	public APPlayer getAPPlayer(SenderAdapter sender) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int queueSize() {
		return dbRunnable.queueSize();
	}

	@Override
	public List<String> listPlayers() {
		return getProxy().getPlayers().stream().map((p) -> p.getName()).collect(Collectors.toList());
	}
}
