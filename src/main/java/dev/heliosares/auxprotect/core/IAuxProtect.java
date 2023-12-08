package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.SQLManager;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.util.Set;
import java.util.function.Consumer;

public interface IAuxProtect {

    InputStream getResource(String string);

    void info(String msg);

    void debug(String msg);

    void debug(String msg, int verb);

    void warning(String msg);

    void print(Throwable t);

    void add(DbEntry dbEntry);

    void runAsync(Runnable run);

    void runSync(Runnable runnable);

    @Nullable
    SenderAdapter getSenderAdapter(String name);

    boolean isHooked(String name);

    APPlayer getAPPlayer(SenderAdapter sender);

    int queueSize();

    Set<String> listPlayers();

    void addRemoveEntryListener(Consumer<DbEntry> consumer, boolean add);

    void broadcast(String msg, APPermission node);

    File getDataFolder();

    PlatformType getPlatform();

    SQLManager getSqlManager();

    APConfig getAPConfig();

    String getCommandPrefix();

    String getCommandAlias();

    SenderAdapter getConsoleSender();

    boolean isShuttingDown();

    File getRootDirectory();

    String getPlatformVersion();

    String getPluginVersion();

    String getStackLog();

    boolean isEnabled();
}
