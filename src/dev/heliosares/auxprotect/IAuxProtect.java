package dev.heliosares.auxprotect;

import java.io.File;
import java.io.InputStream;

import dev.heliosares.auxprotect.database.SQLManager;

public interface IAuxProtect {

	File getDataFolder();

	InputStream getResource(String string);

	String translate(String key);

	void info(String msg);

	void debug(String msg);

	void debug(String msg, int verb);

	void warning(String msg);

	boolean isBungee();

	SQLManager getSqlManager();

	int getDebug();

	APConfig getAPConfig();
	
}
