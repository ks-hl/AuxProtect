package dev.heliosares.auxprotect.adapters;

import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.PlatformType;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BungeeConfigAdapter extends ConfigAdapter {
    private Configuration config;

    public BungeeConfigAdapter(File parent, String path, @Nullable Configuration config,
                               @Nullable Function<String, InputStream> defaults, boolean createBlank) {
        super(parent, path, defaults, createBlank);
        this.config = config;
    }

    @Override
    public String getString(String key) {
        return config.getString(key);
    }

    @Override
    public String getString(String key, String def) {
        return config.getString(key, def);
    }

    @Override
    public long getLong(String key) {
        return config.getLong(key);
    }

    @Override
    public long getLong(String key, long def) {
        return config.getLong(key, def);
    }

    @Override
    public boolean getBoolean(String key) {
        return config.getBoolean(key);
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        return config.getBoolean(key, def);
    }

    @Override
    public int getInt(String path) {
        return config.getInt(path);
    }

    @Override
    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    @Override
    public void set(String key, Object value) {
        config.set(key, value);
    }

    @Override
    public PlatformType getPlatform() {
        return PlatformType.BUNGEE;
    }

    @Override
    public Set<String> getKeys(boolean recur) {
        return config.getKeys().stream().collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<String> getKeys(String key, boolean recur) {
        return config.getSection(key).getKeys().stream().collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Object get(String key) {
        return config.get(key);
    }

    @Override
    public Object get(String key, Object def) {
        return config.get(key, def);
    }

    @Override
    public boolean isSection(String key) {
        return config.getSection(key) != null;
    }

    @Override
    public void save() throws IOException {
        if (config != null) {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, file);
        }
    }

    @Override
    public void load() throws IOException {
        super.load();
        Configuration def = null;
        if (defaults != null) {
            try (InputStream in = defaults.apply(path)) {
                if (in != null) {
                    def = ConfigurationProvider.getProvider(YamlConfiguration.class).load(in);
                }
            }
        }
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file, def);
        } catch (Exception e) {
            AuxProtectAPI.getInstance().warning("Error while loading " + path + ":");
            throw e;
        }
    }

    @Override
    public boolean isNull() {
        return config == null;
    }

    @Override
    public List<String> getStringList(String key) {
        return config.getStringList(key);
    }
}
