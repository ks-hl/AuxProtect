package dev.heliosares.auxprotect;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.Acrobot.ChestShop.ChestShop;
import com.spawnchunk.auctionhouse.AuctionHouse;

import net.brcdev.shopgui.ShopGuiPlugin;
import net.milkbowl.vault.economy.Economy;
import dev.heliosares.auxprotect.command.APCommand;
import dev.heliosares.auxprotect.command.APCommandTab;
import dev.heliosares.auxprotect.command.ClaimInvCommand;
import dev.heliosares.auxprotect.command.PurgeCommand;
import dev.heliosares.auxprotect.database.DatabaseRunnable;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.listeners.*;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.Language;
import dev.heliosares.auxprotect.utils.MyPermission;
import dev.heliosares.auxprotect.utils.Telemetry;
import dev.heliosares.auxprotect.utils.UpdateChecker;
import dev.heliosares.auxprotect.utils.YMLManager;

/*-
 * TODO: 
 * 
 * better api
 * 
 * */

public class AuxProtect extends JavaPlugin implements IAuxProtect {
	public static final char LEFT_ARROW = 9668;
	public static final char RIGHT_ARROW = 9658;
	public static final char BLOCK = 9608;

	public static IAuxProtect getInstance() {
		return instance;
	}

	public DatabaseRunnable dbRunnable;
	public static Language lang;
	public int debug;
	public HashMap<String, Long> lastLogOfInventoryForUUID = new HashMap<>();
	public HashMap<String, Long> lastLogOfMoneyForUUID = new HashMap<>();
	public YMLManager data;
	public APConfig config;

	private Economy econ;
	private static AuxProtect instance;

	SQLManager sqlManager;

	public String update;
	long lastCheckedForUpdate;

	long lastloaded;

	private int SERVER_VERSION;

	public int getCompatabilityVersion() {
		return SERVER_VERSION;
	}

	@Override
	public void onEnable() {
		instance = this;
		AuxProtectApi.setInstance(this);
		this.saveDefaultConfig();
		this.reloadConfig();
		this.getConfig().options().copyDefaults(true);

		debug = getConfig().getInt("debug", 0);

		config = new APConfig(this.getConfig());
		config.load();

		YMLManager langManager = new YMLManager("en-us", this);
		langManager.load(true);
		lang = new Language(langManager.getData());

		data = new YMLManager("data", this);
		data.load(false);
		lastloaded = data.getData().getLong("lastloaded");
		data.getData().set("lastloaded", System.currentTimeMillis());
		data.save();

		try {
			SERVER_VERSION = Integer.parseInt(Bukkit.getBukkitVersion().split("\\.")[1]);
		} catch (Exception e) {
			warning("Failed to parse version string: \"" + Bukkit.getBukkitVersion() + "\". Defaulting to 1.16");
			SERVER_VERSION = 16;
			e.printStackTrace();
		}
		debug("Compatability version: " + SERVER_VERSION, 1);

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
			File sqliteFile = new File(getDataFolder(), "database/auxprotect.db");
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

		sqlManager = new SQLManager(this, uri, getConfig().getString("MySQL.table-prefix"));

		new BukkitRunnable() {

			@Override
			public void run() {
				boolean success = false;
				if (mysql) {
					success = sqlManager.connect(user, pass);
				} else {
					success = sqlManager.connect();
				}
				if (!success) {
					getLogger().severe("Failed to connect to SQL database. Disabling.");
					setEnabled(false);
					return;
				}

				for (Object command : getConfig().getList("purge-cmds")) {
					String cmd = (String) command;
					PurgeCommand purge = new PurgeCommand(AuxProtect.this);
					String[] argsOld = cmd.split(" ");
					String[] args = new String[argsOld.length + 1];
					args[0] = "purge";
					for (int i = 0; i < argsOld.length; i++) {
						args[i + 1] = argsOld[i];
					}
					purge.purge(Bukkit.getConsoleSender(), args);
				}
				sqlManager.count();
			}
		}.runTaskAsynchronously(this);

		if (!setupEconomy()) {
			getLogger().info("Not using vault");
		}

		dbRunnable = new DatabaseRunnable(this, sqlManager);

		getServer().getScheduler().runTaskTimerAsynchronously(this, dbRunnable, 60, 5);

		new BukkitRunnable() {
			long lastPos;

			@Override
			public void run() {
				if (System.currentTimeMillis() - lastPos > config.posInterval) {
					lastPos = System.currentTimeMillis();
					for (Player player : Bukkit.getOnlinePlayers()) {
						PlayerListener.logPos(AuxProtect.this, player, player.getLocation(), "");
					}
				}
			}
		}.runTaskTimerAsynchronously(this, 20, 20);

		new BukkitRunnable() {

			@Override
			public void run() {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (config.inventoryInterval > 0) {
						Long lastInv = lastLogOfInventoryForUUID.get(player.getUniqueId().toString());
						if (lastInv == null || System.currentTimeMillis() - lastInv > config.inventoryInterval) {
							lastLogOfInventoryForUUID.put(player.getUniqueId().toString(), System.currentTimeMillis());
							dbRunnable.add(new DbEntry(AuxProtect.getLabel(player), EntryAction.INVENTORY, false,
									player.getLocation(), "periodic", InvSerialization.playerToBase64(player)));
						}
					}

					if (config.moneyInterval > 0) {
						Long lastMoney = lastLogOfMoneyForUUID.get(player.getUniqueId().toString());
						if (lastMoney == null || System.currentTimeMillis() - lastMoney > config.moneyInterval) {
							PlayerListener.logMoney(AuxProtect.this, player, "periodic");
						}
					}
				}
				if (config.checkforupdates && System.currentTimeMillis() - lastCheckedForUpdate > 1000 * 60 * 60) {
					lastCheckedForUpdate = System.currentTimeMillis();
					debug("Checking for updates...", 1);
					String newVersion = UpdateChecker.getVersion(AuxProtect.this, 99147);
					if (newVersion != null) {
						int compare = UpdateChecker.compareVersions(AuxProtect.this.getDescription().getVersion(),
								newVersion);
						if (compare <= 0) {
							update = null;
						} else {
							boolean newUpdate = update == null;
							update = newVersion;
							if (newUpdate) {
								for (Player player : Bukkit.getOnlinePlayers()) {
									if (MyPermission.ADMIN.hasPermission(player)) {
										AuxProtect.this.tellAboutUpdate(player);
									}
								}
								AuxProtect.this.tellAboutUpdate(Bukkit.getConsoleSender());
							}
						}
					}
				}
			}
		}.runTaskTimerAsynchronously(this, 10 * 20, 10 * 20);

		getServer().getPluginManager().registerEvents(new ProjectileListener(this), this);
		getServer().getPluginManager().registerEvents(new EntityListener(this), this);
		getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
		getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
		getServer().getPluginManager().registerEvents(new PaneListener(this), this);

		Plugin plugin = getServer().getPluginManager().getPlugin("ShopGuiPlus");
		if (plugin != null && plugin.isEnabled() && plugin instanceof ShopGuiPlugin) {
			getServer().getPluginManager().registerEvents(new ShopGUIPlusListener(this), this);
		}

		plugin = getServer().getPluginManager().getPlugin("ChestShop");
		if (plugin != null && plugin.isEnabled() && plugin instanceof ChestShop) {
			getServer().getPluginManager().registerEvents(new ChestShopListener(this), this);
		}

		plugin = getServer().getPluginManager().getPlugin("AuctionHouse");
		if (plugin != null && plugin.isEnabled() && plugin instanceof AuctionHouse) {
			getServer().getPluginManager().registerEvents(new AuctionHouseListener(this, (AuctionHouse) plugin), this);
		}

		if (getServer().getPluginManager().getPlugin("EconomyShopGUI") != null
				|| getServer().getPluginManager().getPlugin("EconomyShopGUI-Premium") != null) {
			getServer().getPluginManager().registerEvents(new EconomyShopGUIListener(this), plugin);
		}

		this.getCommand("claiminv").setExecutor(new ClaimInvCommand(this));
		this.getCommand("auxprotect").setExecutor(new APCommand(this));
		this.getCommand("auxprotect").setTabCompleter(new APCommandTab(this));

		if (!config.isPrivate()) {
			EntryAction.ALERT.setEnabled(false);
			EntryAction.CENSOR.setEnabled(false);
			EntryAction.IGNOREABANDONED.setEnabled(false);
			EntryAction.XRAYCHECK.setEnabled(false);
		}

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
					new Telemetry(AuxProtect.this, 14232);
				}
			}.runTaskLater(this, (1000 * 60 * 60 - (System.currentTimeMillis() - lastloaded)) / 50);
		}
	}

	public void tellAboutUpdate(CommandSender sender) {
		lang.send(sender, "update", AuxProtect.this.getDescription().getVersion(), update);
	}

	@Override
	public void onDisable() {
		dbRunnable.run();
		if (sqlManager != null)
			sqlManager.close();
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
		if (econ == null) {
			return "$" + (Math.round(d * 100) / 100.0);
		}
		return econ.format(d);
	}

	public static String getLabel(Object o) {
		if (o == null) {
			return "#null";
		}
		if (o instanceof Player) {
			return "$" + ((Player) o).getUniqueId().toString();
		} else if (o instanceof Entity) {
			return "#" + ((Entity) o).getType().name().toLowerCase();
		}
		if (o instanceof Container) {
			return "#" + ((Container) o).getBlock().getType().toString().toLowerCase();
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
}
