package dev.heliosares.auxprotect.adapters;

import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.configuration.file.FileConfiguration;

import dev.heliosares.auxprotect.core.PlatformType;

public class SpigotConfigAdapter implements ConfigAdapter {
	private final FileConfiguration config;

	public SpigotConfigAdapter(FileConfiguration config) {
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
	public void set(String key, Object value) {
		config.set(key, value);
	}

	@Override
	public PlatformType getPlatform() {
		return PlatformType.SPIGOT;
	}

	@Override
	public Set<String> getKeys(boolean recur) {
		return config.getKeys(recur).stream().collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public Set<String> getKeys(String key, boolean recur) {
		return config.getConfigurationSection(key).getKeys(recur).stream().collect(Collectors.toUnmodifiableSet());
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
		return config.isConfigurationSection(key);
	}
}
