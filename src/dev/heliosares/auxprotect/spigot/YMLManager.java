package dev.heliosares.auxprotect.spigot;

import dev.heliosares.auxprotect.core.IAuxProtect;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class YMLManager {
    IAuxProtect plugin;
    FileConfiguration data;
    File dfile;
    private final String fileName;

    public YMLManager(String fileName, IAuxProtect p) {
        this.fileName = fileName;
        this.plugin = p;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        this.dfile = new File(plugin.getDataFolder(), fileName + ".yml");
        if (!this.dfile.exists()) {
            try {
                dfile.createNewFile();
            } catch (IOException e) {
                Bukkit.getServer().getLogger().severe("�4Could not create " + fileName + ".yml!");
            }
        }
        reload();
    }

    public FileConfiguration getData() {
        return this.data;
    }

    public void save() {
        try {
            this.data.save(this.dfile);
        } catch (IOException e) {
            Bukkit.getServer().getLogger().severe("�4Could not save " + fileName + ".yml!");
        }
    }

    public void reload() {
        this.data = (FileConfiguration) YamlConfiguration.loadConfiguration(this.dfile);
    }
}
