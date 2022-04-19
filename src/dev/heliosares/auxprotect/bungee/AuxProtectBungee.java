package dev.heliosares.auxprotect.bungee;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.Acrobot.ChestShop.ChestShop;

import dev.heliosares.auxprotect.APConfig;
import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.IAuxProtect;
import dev.heliosares.auxprotect.bungee.command.APBCommand;
import dev.heliosares.auxprotect.command.PurgeCommand;
import dev.heliosares.auxprotect.database.DatabaseRunnable;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.listeners.ChestShopListener;
import dev.heliosares.auxprotect.listeners.EntityListener;
import dev.heliosares.auxprotect.listeners.ShopGUIPlusListener;
import dev.heliosares.auxprotect.utils.MySender;
import dev.heliosares.auxprotect.listeners.InventoryListener;
import dev.heliosares.auxprotect.listeners.ProjectileListener;
import net.brcdev.shopgui.ShopGuiPlugin;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

@SuppressWarnings("unused")
public class AuxProtectBungee extends Plugin implements Listener, IAuxProtect {
	protected Configuration config;
	public Language lang;
	public int debug;
	SQLManager sqlManager;
	protected DatabaseRunnable dbRunnable;
	private static AuxProtectBungee instance;

	public AuxProtectBungee() {
		instance = this;
	}

	@Override
	public void onEnable() {
		getProxy().getPluginManager().registerCommand(this, new APBCommand(this));
		getProxy().getPluginManager().registerListener(this, new APBListener(this));

		loadConfig();
		YMLManager langManager = new YMLManager("en-us.yml", this);
		langManager.load();
		lang = new Language(langManager.getData());

		File sqliteFile = null;
		boolean mysql = config.getBoolean("MySQL.use", false);
		String user = config.getString("MySQL.username", "");
		String pass = config.getString("MySQL.password", "");
		String uri = "";
		if (mysql) {
			String host = config.getString("MySQL.host", "localhost");
			String port = config.getString("MySQL.port", "3306");
			String database = config.getString("MySQL.database", "database");
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
		sqlManager = new SQLManager(this, uri, config.getString("MySQL.table-prefix"), sqliteFile);

		runAsync(new Runnable() {

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
		getProxy().getPluginManager().unregisterListeners(this);
		getProxy().getPluginManager().unregisterCommands(this);
		if (dbRunnable != null) {
			dbRunnable.run();
		}
		if (sqlManager != null) {
			sqlManager.close();
		}
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
			config = ConfigurationProvider.getProvider(YamlConfiguration.class)
					.load(new File(getDataFolder(), "config.yml"));
		} catch (IOException e) {
			print(e);
		}
	}

	@Override
	public InputStream getResource(String string) {
		return getResourceAsStream(string);
	}

	public SQLManager getSqlManager() {
		return sqlManager;
	}

	@Override
	public String translate(String key) {
		String translate = lang.translate(key);
		debug(String.format("Translated \"%s\" TO \"%s\"", key, translate), 5);
		return translate;
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
	}

	@Override
	public void print(Throwable t) {
		getLogger().log(Level.WARNING, t.getMessage(), t);
	}

	@Override
	public boolean isBungee() {
		return true;
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
		// TODO Implement APConfig
		return null;
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
}
