package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.SQLManager;

import java.io.File;
import java.io.InputStream;
import java.util.List;

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

    void runAsync(Runnable run);

    void runSync(Runnable runnable);

    String getCommandPrefix();

    String getCommandAlias();

    SenderAdapter getConsoleSender();

    boolean isShuttingDown();

    boolean isHooked(String name);

    File getRootDirectory();

    String getPlatformVersion();

    String getPluginVersion();

    APPlayer getAPPlayer(SenderAdapter sender);

    int queueSize();

    String getStackLog();

    List<String> listPlayers();

    boolean isEnabled();
}
