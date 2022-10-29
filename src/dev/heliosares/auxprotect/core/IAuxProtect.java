package dev.heliosares.auxprotect.core;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.SQLManager;

public interface IAuxProtect {

	File getDataFolder();

	InputStream getResource(String string);

	void info(String msg);

	void debug(String msg);

	void debug(String msg, int verb);

	void warning(String msg);

	void print(Throwable t);

	PlatformType getPlatform();

	SQLManager getSqlManager();

	APConfig getAPConfig();

	void add(DbEntry dbEntry);

	public void runAsync(Runnable run);

	public void runSync(Runnable runnable);
	
	public String getCommandPrefix();
	
	public String getCommandAlias();
	
	public SenderAdapter getConsoleSender();
	
	public boolean isShuttingDown();
	
	public boolean isHooked(String name);
	
	public File getRootDirectory();
	
	public String getPlatformVersion();

	public String getPluginVersion();
	
	public APPlayer getAPPlayer(SenderAdapter sender);

	public int queueSize();

	public String getStackLog();
	
	public List<String> listPlayers();
}
