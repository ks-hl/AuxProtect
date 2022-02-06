package dev.heliosares.auxprotect.bungee;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import java.io.File;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class YMLManager {
	AuxProtectBungee plugin;
	Configuration data;
	File dfile;
	private final String fileName;

	public YMLManager(String fileName, AuxProtectBungee p) {
		this.fileName = fileName;
		this.plugin = p;
	}

	public void load() {
		if (!plugin.getDataFolder().exists())
			plugin.getDataFolder().mkdir();

		File file = new File(plugin.getDataFolder(), fileName);

		if (!file.exists()) {
			try (InputStream in = plugin.getResourceAsStream(fileName)) {
				Files.copy(in, file.toPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		reload();
		save();
	}

	public Configuration getData() {
		return this.data;
	}

	public void save() {
		try {
			ConfigurationProvider.getProvider(YamlConfiguration.class).save(data,
					new File(plugin.getDataFolder(), fileName));
		} catch (IOException e) {
			plugin.getProxy().getLogger().severe("§4Could not save " + fileName + ".yml!");
		}
	}

	public void reload() {
		try {
			this.data = ConfigurationProvider.getProvider(YamlConfiguration.class)
					.load(new File(plugin.getDataFolder(), fileName));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
