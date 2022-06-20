package dev.heliosares.auxprotect.spigot;

import org.bukkit.configuration.file.YamlConfiguration;

import dev.heliosares.auxprotect.core.IAuxProtect;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;

import org.bukkit.Bukkit;
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;

public class YMLManager {
	IAuxProtect plugin;
	FileConfiguration data;
	File dfile;
	private final String fileName;
	private boolean hasDefaults;

	public YMLManager(String fileName, IAuxProtect p) {
		this.fileName = fileName;
		this.plugin = p;
		hasDefaults = plugin.getResource(fileName + ".yml") != null;
	}

	public void load(boolean checkversion) {
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdir();
		}
		this.dfile = new File(plugin.getDataFolder(), fileName + ".yml");
		if (!this.dfile.exists()) {
			try {
				if (hasDefaults) {
					Files.copy(plugin.getResource(fileName + ".yml"), dfile.toPath());
				} else {
					dfile.createNewFile();
				}
			} catch (IOException e) {
				Bukkit.getServer().getLogger().severe("§4Could not create " + fileName + ".yml!");
			}
		}
		reload();
		if (checkversion && data.getInt("version") < data.getDefaults().getInt("version")) {
			try {
				dfile.delete();
				Files.copy(plugin.getResource(fileName + ".yml"), dfile.toPath());
				reload();
			} catch (IOException e) {
				plugin.print(e);
			}
		}
		save();
	}

	public FileConfiguration getData() {
		return this.data;
	}

	public void save() {
		try {
			this.data.save(this.dfile);
		} catch (IOException e) {
			Bukkit.getServer().getLogger().severe("§4Could not save " + fileName + ".yml!");
		}
	}

	public void reload() {
		this.data = (FileConfiguration) YamlConfiguration.loadConfiguration(this.dfile);
		if (hasDefaults) {
			data.setDefaults(
					YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource(fileName + ".yml"))));
		}
	}
}
