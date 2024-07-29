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

    SenderAdapter<?,?> getConsoleSender();

    @Nullable
    SenderAdapter<?,?> getSenderAdapter(String name);

    boolean isShuttingDown();

    boolean isHooked(String name);

    File getRootDirectory();

    String getPlatformVersion();

    String getPluginVersion();

    @Nullable
    APPlayer<?> getAPPlayer(SenderAdapter<?,?> sender);

    String formatMoney(double amount);

    int queueSize();

    String getStackLog();

    Set<String> listPlayers();

    boolean isEnabled();

    void addRemoveEntryListener(Consumer<DbEntry> consumer, boolean add);

    void broadcast(String msg, APPermission node);

    boolean doesWorldExist(String world);

    boolean isPrimaryThread();
}
