package dev.heliosares.auxprotect.adapters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;

import dev.heliosares.auxprotect.AuxProtectAPI;
import dev.heliosares.auxprotect.core.PlatformType;

public abstract class ConfigAdapter {
	protected final File file;
	protected final String path;
	protected final Function<String, InputStream> defaults;
	protected final boolean createBlank;

	public ConfigAdapter(File parent, String path, @Nullable Function<String, InputStream> defaults,
			boolean createBlank) {
		this.file = new File(parent, path);
		this.path = path;
		this.defaults = defaults;
		this.createBlank = createBlank;
	}

	public File getFile() {
		return file;
	}

	public abstract String getString(String key);

	public abstract String getString(String key, String def);

	public abstract long getLong(String key);

	public abstract long getLong(String key, long def);

	public abstract boolean getBoolean(String key);

	public abstract boolean getBoolean(String key, boolean def);

	public abstract int getInt(String path);

	public abstract int getInt(String path, int def);

	public abstract Object get(String key);

	public abstract Object get(String key, Object def);

	public abstract void set(String key, Object value);

	public abstract boolean isSection(String key);

	public abstract PlatformType getPlatform();

	public abstract Set<String> getKeys(boolean recur);

	public abstract Set<String> getKeys(String key, boolean recur);

	public abstract void save() throws IOException;

	public void load() throws IOException {
		if (!file.getParentFile().exists())
			file.getParentFile().mkdirs();

		if (!file.exists()) {
			if (defaults != null) {
				try (InputStream in = defaults.apply(path)) {
					if (in != null) {
						Files.copy(in, file.toPath());
						AuxProtectAPI.getInstance().info("Generated default " + path);
						return;
					}
				}
			}
			if (createBlank) {
				file.createNewFile();
				AuxProtectAPI.getInstance().info("Created " + path);
			} else {
				throw new FileNotFoundException(file.getAbsolutePath());
			}
		}
	}

	public abstract boolean isNull();

	public abstract List<String> getStringList(String key);
}
