package dev.heliosares.auxprotect.bungee;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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

public class AuxProtectBungee extends Plugin implements IAuxProtect {
	private final APConfig config = new APConfig();
	SQLManager sqlManager;
	protected DatabaseRunnable dbRunnable;
	private static AuxProtectBungee instance;

	public AuxProtectBungee() {
		instance = this;
	}

	@Override
	public void onEnable() {
		try {
			config.load(this, new BungeeConfigAdapter(this.getDataFolder(), "config.yml", null,
					(s) -> getResourceAsStream(s), false));
		} catch (IOException e1) {
			warning("Failed to load config");
			print(e1);
		}
		// TODO reloadable
		try {
			Language.load(this,
					() -> new BungeeConfigAdapter(getDataFolder(),
							"lang/" + config.getConfig().getString("lang") + ".yml", null,
							(s) -> getResourceAsStream(s), false));

		} catch (FileNotFoundException e1) {
			warning("Language file not found");
		} catch (IOException e1) {
			warning("Failed to load lang");
			print(e1);
		}

		getProxy().getPluginManager().registerCommand(this, new APBCommand(this, this.getCommandPrefix()));
		getProxy().getPluginManager().registerCommand(this, new APBCommand(this, this.getCommandAlias()));
		getProxy().getPluginManager().registerListener(this, new APBListener(this));

		File sqliteFile = null;
		String uri = "";
		if (getAPConfig().isMySQL()) {
			uri = String.format("jdbc:mysql://%s:%s/%s", getAPConfig().getHost(), getAPConfig().getPort(),
					getAPConfig().getDatabase());
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
		sqlManager = new SQLManager(this, uri, getAPConfig().getTablePrefix(), sqliteFile);

		runAsync(new Runnable() {

			@Override
			public void run() {
				try {
					if (getAPConfig().isMySQL()) {
						sqlManager.connect(getAPConfig().getUser(), getAPConfig().getPass());
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
		if (getAPConfig().getDebug() >= verbosity) {
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
	public String getCommandPrefix() {
		return "auxprotectbungee";
	}

	@Override
	public SenderAdapter getConsoleSender() {
		return new BungeeSenderAdapter(this, this.getProxy().getConsole());
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

	@Override
	public String getCommandAlias() {
		return "apb";
	}
}
