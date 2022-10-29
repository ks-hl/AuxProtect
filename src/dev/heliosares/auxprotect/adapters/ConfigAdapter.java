package dev.heliosares.auxprotect.adapters;

import java.util.Set;

import dev.heliosares.auxprotect.core.PlatformType;

public interface ConfigAdapter {
	public String getString(String key);

	public String getString(String key, String def);

	public long getLong(String key);

	public long getLong(String key, long def);

	public boolean getBoolean(String key);

	public boolean getBoolean(String key, boolean def);
	
	public Object get(String key);
	
	public Object get(String key, Object def);

	public void set(String key, Object value);
	
	public boolean isSection(String key);
	
	public PlatformType getPlatform();
	
	public Set<String> getKeys(boolean recur);
	
	public Set<String> getKeys(String key, boolean recur);
}
