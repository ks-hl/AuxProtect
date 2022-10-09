package dev.heliosares.auxprotect.spigot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.milkbowl.vault.economy.Economy;
import dev.heliosares.auxprotect.core.APConfig;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.MySender;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.database.DatabaseRunnable;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.spigot.command.APCommand;
import dev.heliosares.auxprotect.spigot.command.APCommandTab;
import dev.heliosares.auxprotect.spigot.command.ClaimInvCommand;
import dev.heliosares.auxprotect.spigot.command.WatchCommand;
import dev.heliosares.auxprotect.spigot.listeners.*;
import dev.heliosares.auxprotect.utils.Language;
import dev.heliosares.auxprotect.utils.Telemetry;
import dev.heliosares.auxprotect.utils.UpdateChecker;

public class AuxProtectSpigot extends JavaPlugin implements IAuxProtect {
	public static final char LEFT_ARROW = 9668;
	public static final char RIGHT_ARROW = 9658;
	public static final char BLOCK = 9608;

	public static IAuxProtect getInstance() {
		return instance;
	}

	protected DatabaseRunnable dbRunnable;
	private static Language lang;
	public int debug;
	public YMLManager data;
	private APConfig config;

	private Economy econ;
	private static AuxProtectSpigot instance;

	private static SQLManager sqlManager;
	private VeinManager veinManager;

	public String update;
	long lastCheckedForUpdate;

	long lastloaded;

	private ClaimInvCommand claiminvcommand;
	private APCommand apcommand;

	public ClaimInvCommand getClaiminvcommand() {
		return claiminvcommand;
	}

	public APCommand getApcommand() {
		return apcommand;
	}

	private int SERVER_VERSION;

	public int getCompatabilityVersion() {
		return SERVER_VERSION;
	}

	@Override
	public void onEnable() {
		instance = this;

		this.saveDefaultConfig();
		this.reloadConfig();
		this.getConfig().options().copyDefaults(true);

		debug = getConfig().getInt("debug", 0);

		config = new APConfig(this.getConfig());

		YMLManager langManager = new YMLManager("en-us", this);
		langManager.load(true);
		lang = new Language(langManager.getData());

		data = new YMLManager("data", this);
		data.load(false);
		lastloaded = data.getData().getLong("lastloaded");
		data.getData().set("lastloaded", System.currentTimeMillis());
		data.save();

		debug("Parsing: " + Bukkit.getBukkitVersion());
		try {
			SERVER_VERSION = Integer.parseInt(Bukkit.getBukkitVersion().split("[\\.-]")[1]);
		} catch (Exception e) {
			warning("Failed to parse version string: \"" + Bukkit.getBukkitVersion() + "\". Defaulting to 1.16");
			SERVER_VERSION = 16;
			print(e);
		}
		debug("Compatability version: " + SERVER_VERSION, 1);

		File sqliteFile = null;
		boolean mysql = getConfig().getBoolean("MySQL.use", false);
		String user = getConfig().getString("MySQL.username", "");
		String pass = getConfig().getString("MySQL.password", "");
		String uri = "";
		if (mysql) {
			String host = getConfig().getString("MySQL.host", "localhost");
			String port = getConfig().getString("MySQL.port", "3306");
			String database = getConfig().getString("MySQL.database", "database");
			uri = String.format("jdbc:mysql://%s:%s/%s", host, port, database);
		} else {
			sqliteFile = new File(getDataFolder(), "database/auxprotect.db");
			if (!sqliteFile.getParentFile().exists()) {
				if (!sqliteFile.getParentFile().mkdirs()) {
					this.getLogger().severe("Failed to create database directory.");
					this.setEnabled(false);
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
					this.setEnabled(false);
					return;
				}
			}
			uri = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
		}

		sqlManager = new SQLManager(this, uri, getConfig().getString("MySQL.table-prefix"), sqliteFile);
		veinManager = new VeinManager();

		new BukkitRunnable() {

			@Override
			public void run() {
				try {
					if (mysql) {
						sqlManager.connect(user, pass);
					} else {
						sqlManager.connect();
					}
					sqlManager.count();
				} catch (Exception e) {
					print(e);
					getLogger().severe("Failed to connect to SQL database. Disabling.");
					setEnabled(false);
					return;
				}
				if (EntryAction.VEIN.isEnabled()) {
					try {
						ArrayList<DbEntry> veins = sqlManager
								.getAllUnratedXrayRecords(System.currentTimeMillis() - (3600000L * 24L * 7L));
						if (veins != null) {
							for (DbEntry vein : veins) {
								veinManager.add((XrayEntry) vein);
							}
						}
					} catch (Exception e) {
						print(e);
						return;
					}
				}

				/*
				 * for (Object command : getConfig().getList("purge-cmds")) { String cmd =
				 * (String) command; String[] argsOld = cmd.split(" "); String[] args = new
				 * String[argsOld.length + 1]; args[0] = "purge"; for (int i = 0; i <
				 * argsOld.length; i++) { args[i + 1] = argsOld[i]; }
				 * PurgeCommand.purge(AuxProtect.this, new MySender(Bukkit.getConsoleSender()),
				 * args); }
				 * 
				 * sqlManager.purgeUIDs();
				 * 
				 * try { sqlManager.vacuum(); } catch (SQLException e) { print(e); }
				 */
			}
		}.runTaskAsynchronously(this);

		dbRunnable = new DatabaseRunnable(this, sqlManager);

		getServer().getScheduler().runTaskTimerAsynchronously(this, dbRunnable, 60, 5);

		getServer().getPluginManager().registerEvents(new ProjectileListener(this), this);
		getServer().getPluginManager().registerEvents(new EntityListener(this), this);
		getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
		getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
		getServer().getPluginManager().registerEvents(new PaneListener(this), this);
		getServer().getPluginManager().registerEvents(new WorldListener(this), this);
		getServer().getPluginManager().registerEvents(new CommandListener(this), this);
		getServer().getPluginManager().registerEvents(new XrayListener(this), this);

		if (setupEconomy()) {
			Telemetry.reportHook(this, "Vault", true);
		} else {
			Telemetry.reportHook(this, "Vault", false);
		}

		try {
			String name = "ShopGuiPlus";
			Plugin plugin = getPlugin(name);
			if (plugin != null && plugin.isEnabled()) {
				getServer().getPluginManager().registerEvents(new ShopGUIPlusListener(this), this);
				Telemetry.reportHook(this, name, true);
			} else {
				Telemetry.reportHook(this, name, false);
			}

			name = "EconomyShopGUI";
			plugin = getPlugin(name);
			if (plugin == null) {
				plugin = getServer().getPluginManager().getPlugin("EconomyShopGUI-Premium");
			}
			if (plugin != null && plugin.isEnabled()) {
				getServer().getPluginManager().registerEvents(new EconomyShopGUIListener(this), this);
				Telemetry.reportHook(this, name, true);
			} else {
				Telemetry.reportHook(this, name, false);
			}

			name = "DynamicShop";
			plugin = getPlugin(name);
			if (plugin != null && plugin.isEnabled()) {
				getServer().getPluginManager().registerEvents(new DynamicShopListener(this), this);
				Telemetry.reportHook(this, name, true);
			} else {
				Telemetry.reportHook(this, name, false);
			}

			name = "ChestShop";
			plugin = getPlugin(name);
			if (plugin != null && plugin.isEnabled()) {
				getServer().getPluginManager().registerEvents(new ChestShopListener(this), this);
				Telemetry.reportHook(this, name, true);
			} else {
				Telemetry.reportHook(this, name, false);
			}

			name = "AuctionHouse";
			plugin = getPlugin(name);
			if (plugin != null && plugin.isEnabled()) {
				getServer().getPluginManager().registerEvents(new AuctionHouseListener(this), this);
				Telemetry.reportHook(this, name, true);
			} else {
				Telemetry.reportHook(this, name, false);
			}

			name = "Jobs";
			plugin = getPlugin(name);
			if (plugin != null && plugin.isEnabled()) {
				getServer().getPluginManager().registerEvents(new JobsListener(this), this);
				Telemetry.reportHook(this, name, true);
			} else {
				Telemetry.reportHook(this, name, false);
			}

			name = "Essentials";
			plugin = getPlugin(name);
			if (plugin != null && plugin.isEnabled()) {
				getServer().getPluginManager().registerEvents(new EssentialsListener(this), this);
				Telemetry.reportHook(this, name, true);
			} else {
				Telemetry.reportHook(this, name, false);
			}
		} catch (Exception e) {
			warning("Exception while hooking other plugins");
			print(e);
		}

		this.getCommand("claiminv").setExecutor(claiminvcommand = new ClaimInvCommand(this));
		this.getCommand("auxprotect").setExecutor(apcommand = new APCommand(this));
		this.getCommand("auxprotect").setTabCompleter(new APCommandTab(this));

		new BukkitRunnable() {

			@Override
			public void run() {
				checkcommand("auxprotect");
				checkcommand("ap");
				checkcommand("claiminv");
			}

			private void checkcommand(String commandlbl) {
				PluginCommand command = getCommand(commandlbl);
				if (command == null || !command.getPlugin().equals(AuxProtectSpigot.this)) {
					String output = "Command '" + commandlbl + "' taken by ";
					if (command == null) {
						output += "an unknown plugin.";
					} else {
						output += command.getPlugin().getName() + ".";
					}
					warning(output);
					if (config.isOverrideCommands()) {
						warning("Attempting to re-register tab completer.");
						getCommand("auxprotect").setTabCompleter(new APCommandTab(AuxProtectSpigot.this));
						getCommand("ap").setTabCompleter(new APCommandTab(AuxProtectSpigot.this));
					} else {
						warning("If this is causing issues, try enabling 'OverrideCommands' in the config.");
					}

				}
			}
		}.runTaskLater(this, 60);

		if (!config.isPrivate()) {
			EntryAction.ALERT.setEnabled(false);
			EntryAction.CENSOR.setEnabled(false);
			EntryAction.IGNOREABANDONED.setEnabled(false);
			EntryAction.VEIN.setEnabled(false);
			EntryAction.ACTIVITY.setEnabled(false);
		}

		new BukkitRunnable() {

			@Override
			public void run() {
				synchronized (apPlayers) {
					for (APPlayer apPlayer : apPlayers.values()) {
						if (!apPlayer.player.isOnline()) {
							continue;
						}

						if (config.getInventoryInterval() > 0) {
							if (System.currentTimeMillis() - apPlayer.lastLoggedInventory > config
									.getInventoryInterval()) {
								apPlayer.logInventory("periodic");
							}
						}

						if (config.getMoneyInterval() > 0) {
							if (System.currentTimeMillis() - apPlayer.lastLoggedMoney > config.getMoneyInterval()) {
								PlayerListener.logMoney(AuxProtectSpigot.this, apPlayer.player, "periodic");
							}
						}

						if (config.getPosInterval() > 0) {
							if (apPlayer.lastMoved > apPlayer.lastLoggedPos
									&& System.currentTimeMillis() - apPlayer.lastLoggedPos > config.getPosInterval()) {
								PlayerListener.logPos(AuxProtectSpigot.this, apPlayer, apPlayer.player,
										apPlayer.player.getLocation(), "");
							}
						}

						if (System.currentTimeMillis() - apPlayer.lastCheckedMovement > 1000) {
							if (apPlayer.lastLocation != null
									&& apPlayer.lastLocation.getWorld().equals(apPlayer.player.getWorld())) {
								apPlayer.movedAmountThisMinute += Math
										.min(apPlayer.lastLocation.distance(apPlayer.player.getLocation()), 10);
							}
							apPlayer.lastLocation = apPlayer.player.getLocation();
							apPlayer.lastCheckedMovement = System.currentTimeMillis();
						}

						if (apPlayer.lastLoggedActivity == 0) {
							apPlayer.lastLoggedActivity = System.currentTimeMillis();
						}
						if (System.currentTimeMillis() - apPlayer.lastLoggedActivity > 60000L && config.isPrivate()) {
							if (apPlayer.player.getWorld().getName().equals("flat") && config.isPrivate()) {
								apPlayer.activity[apPlayer.activityIndex] += 100;
							}
							apPlayer.addActivity(Math.floor((apPlayer.movedAmountThisMinute + 7) / 10));
							apPlayer.movedAmountThisMinute = 0;

							if (apPlayer.hasMovedThisMinute) {
								apPlayer.addActivity(1);
								apPlayer.hasMovedThisMinute = false;
							}

							add(new DbEntry(AuxProtectSpigot.getLabel(apPlayer.player), EntryAction.ACTIVITY, false,
									apPlayer.player.getLocation(), "",
									(int) Math.round(apPlayer.activity[apPlayer.activityIndex]) + ""));
							apPlayer.lastLoggedActivity = System.currentTimeMillis();

							int tallied = 0;
							int inactive = 0;
							for (double activity : apPlayer.activity) {
								if (activity < 0) {
									continue;
								}
								tallied++;
								if (activity < 10) {
									inactive++;
								}
							}
							if (tallied >= 15 && (double) inactive / (double) tallied > 0.75
									&& !APPermission.BYPASS_INACTIVE.hasPermission(apPlayer.player)) {
								if (System.currentTimeMillis() - apPlayer.lastNotifyInactive > 600000L) {
									apPlayer.lastNotifyInactive = System.currentTimeMillis();
									String msg = String.format(lang.translate("inactive-alert"),
											apPlayer.player.getName(), inactive, tallied);
									for (Player player : Bukkit.getOnlinePlayers()) {
										if (APPermission.NOTIFY_INACTIVE.hasPermission(player)) {
											player.sendMessage(msg);
										}
									}
									info(msg);
									add(new DbEntry(AuxProtectSpigot.getLabel(apPlayer.player), EntryAction.ALERT,
											false, apPlayer.player.getLocation(), "inactive",
											inactive + "/" + tallied));
								}
							}

							apPlayer.activityIndex++;
							if (apPlayer.activityIndex >= apPlayer.activity.length) {
								apPlayer.activityIndex = 0;
							}
							apPlayer.activity[apPlayer.activityIndex] = 0;
						}
					}
				}
			}
		}.runTaskTimerAsynchronously(this, 5, 5);
		new BukkitRunnable() {

			@Override
			public void run() {
				WatchCommand.tick(AuxProtectSpigot.this);
			}
		}.runTaskTimerAsynchronously(this, 1, 1);

		new BukkitRunnable() {

			@Override
			public void run() {
				if (config.shouldCheckForUpdates()
						&& System.currentTimeMillis() - lastCheckedForUpdate > 1000 * 60 * 60) {
					lastCheckedForUpdate = System.currentTimeMillis();
					debug("Checking for updates...", 1);
					String newVersion = UpdateChecker.getVersion(AuxProtectSpigot.this, 99147);
					debug("New Version: " + newVersion + " Current Version: "
							+ AuxProtectSpigot.this.getDescription().getVersion(), 1);
					if (newVersion != null) {
						int compare = UpdateChecker.compareVersions(AuxProtectSpigot.this.getDescription().getVersion(),
								newVersion);
						if (compare <= 0) {
							update = null;
						} else {
							boolean newUpdate = update == null;
							update = newVersion;
							if (newUpdate) {
								for (Player player : Bukkit.getOnlinePlayers()) {
									if (APPermission.ADMIN.hasPermission(player)) {
										AuxProtectSpigot.this.tellAboutUpdate(player);
									}
								}
								AuxProtectSpigot.this.tellAboutUpdate(Bukkit.getConsoleSender());
							}
						}
					}
				}
			}
		}.runTaskTimerAsynchronously(this, 1 * 20, 10 * 20);

		if (System.currentTimeMillis() - lastloaded > 1000 * 60 * 60) {
			debug("Initializing telemetry. THIS MESSAGE WILL DISPLAY REGARDLESS OF WHETHER BSTATS CONFIG IS ENABLED. THIS DOES NOT INHERENTLY MEAN ITS ENABLED",
					3);
			new Telemetry(this, 14232);
		} else {
			debug("Delaying telemetry initialization to avoid rate-limiting. THIS MESSAGE WILL DISPLAY REGARDLESS OF WHETHER BSTATS CONFIG IS ENABLED. THIS DOES NOT INHERENTLY MEAN ITS ENABLED",
					3);
			new BukkitRunnable() {

				@Override
				public void run() {
					new Telemetry(AuxProtectSpigot.this, 14232);
				}
			}.runTaskLater(this, (1000 * 60 * 60 - (System.currentTimeMillis() - lastloaded)) / 50);
		}
	}

	private Plugin getPlugin(String name) {
		for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
			if (plugin.getName().equalsIgnoreCase(name)) {
				return plugin;
			}
		}
		return null;
	}

	private HashMap<UUID, APPlayer> apPlayers = new HashMap<>();

	public APPlayer getAPPlayer(Player player) {
		synchronized (apPlayers) {
			if (apPlayers.containsKey(player.getUniqueId())) {
				return apPlayers.get(player.getUniqueId());
			}
			APPlayer apPlayer = new APPlayer(this, player);
			apPlayers.put(player.getUniqueId(), apPlayer);
			return apPlayer;
		}
	}

	public void removeAPPlayer(Player player) {
		synchronized (apPlayers) {
			apPlayers.remove(player.getUniqueId());
		}
	}

	public void tellAboutUpdate(CommandSender sender) {
		lang.send(sender, "update", AuxProtectSpigot.this.getDescription().getVersion(), update);
	}

	@Override
	public void onDisable() {
		if (sqlManager != null) {
			if (dbRunnable != null && sqlManager.isConnected()) {
				dbRunnable.run();
			}
			sqlManager.close();
		}
		dbRunnable = null;
		sqlManager = null;
	}

	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		econ = rsp.getProvider();
		return econ != null;
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

	public SQLManager getSqlManager() {
		return sqlManager;
	}

	public Economy getEconomy() {
		return econ;
	}

	public String formatMoney(double d) {
		if (!Double.isFinite(d)) {
			return "NaN";
		}
		if (d <= 0) {
			return "$0";
		}
		if (econ == null) {
			return "$" + (Math.round(d * 100) / 100.0);
		}
		return econ.format(d);
	}

	public static String getLabel(Object o) {
		if (o == null) {
			return "#null";
		}
		if (o instanceof UUID) {
			return "$" + ((UUID) o).toString();
		}
		if (o instanceof Player) {
			return "$" + ((Player) o).getUniqueId().toString();
		}
		if (o instanceof Entity) {
			return "#" + ((Entity) o).getType().name().toLowerCase();
		}
		if (o instanceof Container) {
			return "#" + ((Container) o).getBlock().getType().toString().toLowerCase();
		}
		if (o instanceof Block) {
			return "#" + ((Block) o).getType().toString().toLowerCase();
		}
		if (o instanceof Material) {
			return ((Material) o).toString().toLowerCase();
		}
		return "#null";
	}

	@Override
	public String translate(String key) {
		return lang.translate(key);
	}

	@Override
	public void warning(String message) {
		getLogger().warning(message);
	}

	@Override
	public void print(Throwable t) {
		getLogger().log(Level.WARNING, t.getMessage(), t);
	}

	@Override
	public boolean isBungee() {
		return false;
	}

	@Override
	public int getDebug() {
		return debug;
	}

	public APConfig getAPConfig() {
		return config;
	}

	@Override
	public void add(DbEntry entry) {
		if (entry instanceof XrayEntry) {
			if (veinManager.add((XrayEntry) entry)) {
				return;
			}
		}
		dbRunnable.add(entry);
	}

	public VeinManager getVeinManager() {
		return veinManager;
	}

	@Override
	public void runAsync(Runnable run) {
		getServer().getScheduler().runTaskAsynchronously(this, run);
	}

	@Override
	public void runSync(Runnable run) {
		getServer().getScheduler().runTask(this, run);
	}

	public int queueSize() {
		return dbRunnable.queueSize();
	}

	@Override
	public void reloadConfig() {
		super.reloadConfig();
		config = new APConfig(this.getConfig());
	}

	@Override
	public String getCommandPrefix() {
		return "auxprotect";
	}

	@Override
	public MySender getConsoleSender() {
		return new MySender(this.getServer().getConsoleSender());
	}

	@Override
	public void setDebug(int debug) {
		this.debug = debug;
	}
}
