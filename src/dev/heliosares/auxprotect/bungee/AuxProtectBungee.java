package dev.heliosares.auxprotect.bungee;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import com.Acrobot.ChestShop.ChestShop;

import dev.heliosares.auxprotect.APConfig;
import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.IAuxProtect;
import dev.heliosares.auxprotect.bungee.command.APCommand;
import dev.heliosares.auxprotect.database.DatabaseRunnable;
import dev.heliosares.auxprotect.database.SQLiteManager;
import dev.heliosares.auxprotect.listeners.ChestShopListener;
import dev.heliosares.auxprotect.listeners.EntityListener;
import dev.heliosares.auxprotect.listeners.GuiShopListener;
import dev.heliosares.auxprotect.listeners.InventoryListener;
import dev.heliosares.auxprotect.listeners.ProjectileListener;
import net.brcdev.shopgui.ShopGuiPlugin;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
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
	SQLiteManager sqlManager;
	public DatabaseRunnable dbRunnable;
	private static AuxProtectBungee instance;

	public AuxProtectBungee() {
		instance = this;
	}

	@Override
	public void onEnable() {
		getProxy().getPluginManager().registerCommand(this, new APCommand(this));
		getProxy().getPluginManager().registerListener(this, new APListener(this));

		loadConfig();
		YMLManager langManager = new YMLManager("en-us.yml", this);
		langManager.load();
		lang = new Language(langManager.getData());

		File sqliteFile = new File(getDataFolder(), "database/auxprotect.db");
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
		sqlManager = new SQLiteManager(this, "jdbc:sqlite:" + sqliteFile.getAbsolutePath());
		if (!sqlManager.connect()) {
			this.getLogger().severe("Failed to connect to SQL database. Disabling.");
			this.onDisable();
			return;
		}
		dbRunnable = new DatabaseRunnable(this, sqlManager);

		getProxy().getScheduler().schedule(this, dbRunnable, 250, 250, TimeUnit.MILLISECONDS);
	}

	@Override
	public void onDisable() {
		getProxy().getPluginManager().unregisterListeners(this);
		getProxy().getPluginManager().unregisterCommands(this);
		if (sqlManager != null)
			sqlManager.close();
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
				e.printStackTrace();
			}
		}
		try {
			config = ConfigurationProvider.getProvider(YamlConfiguration.class)
					.load(new File(getDataFolder(), "config.yml"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public InputStream getResource(String string) {
		return getResourceAsStream(string);
	}

	public SQLiteManager getSqlManager() {
		return sqlManager;
	}

	@Override
	public String translate(String key) {
		String translate = lang.translate(key);
		debug(String.format("Translated \"%s\" TO \"%s\"", key, translate), 5);
		return translate;
	}

	public void debug(String string) {
		this.getLogger().info(string);
	}

	public void debug(String string, int verbosity) {
		if (debug >= verbosity) {
			this.debug(string);
		}
	}

	@Override
	public void warning(String message) {
		getLogger().warning(message);
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
}
