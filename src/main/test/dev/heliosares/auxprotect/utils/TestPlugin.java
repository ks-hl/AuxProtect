package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.adapters.config.JSONConfigAdapter;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.APConfig;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.database.DatabaseRunnable;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.exceptions.BusyException;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class TestPlugin implements IAuxProtect {
    private final SQLManager sql;
    private final APConfig apConfig;
    private final DatabaseRunnable dbRunnable;
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(10);

    public TestPlugin(String target, String prefix, File sqliteFile, boolean mysql, String user, String pass) throws ClassNotFoundException, SQLException, BusyException, IOException {
        try {
            AuxProtectAPI.setInstance(this);
        } catch (IllegalStateException ignored) {
        }
        File configFile = new File(getDataFolder(), "config.json");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();
            try (FileWriter writer = new FileWriter(configFile, false)) {
                writer.write("""
                        {
                          "lang": "en-us",
                          "MySQL": {
                            "use": false,
                            "host": "localhost",
                            "port": 3306,
                            "database": "database",
                            "username": "username",
                            "password": "password",
                            "table-prefix": ""
                          },
                          "OverrideCommands": false,
                          "SanitizeUnicode": false,
                          "AutoPurge": {
                            "Enabled": false,
                            "default": "180d",
                            "Table": {
                              "auxprotect_main": "default",
                              "auxprotect_spam": "default",
                              "auxprotect_abandoned": "default",
                              "auxprotect_xray": "default",
                              "auxprotect_inventory": "default",
                              "auxprotect_commands": "default",
                              "auxprotect_position": "default",
                              "auxprotect_towny": "default",
                              "auxprotect_api": "default",
                              "auxprotect_chat": "default"
                            }
                          },
                          "Actions": {
                            "money": {
                              "Enabled": true,
                              "Interval": 60000
                            },
                            "pos": {
                              "Enabled": true,
                              "Interval": 3000
                            },
                            "inventory": {
                              "Enabled": true,
                              "WorldChange": false,
                              "Interval": 3600000,
                              "Diff-Interval": 0,
                              "LowestPriority": false
                            },
                            "townbalance": {
                              "Enabled": true,
                              "Interval": 5000
                            },
                            "nationbalance": {
                              "Enabled": true,
                              "Interval": 5000
                            },
                            "session": {
                              "Enabled": true,
                              "LogIP": true
                            }
                          },
                          "checkforupdates": true
                        }""");
                writer.flush();
            }
        }
        apConfig = new APConfig();
        JSONConfigAdapter jsonConfigAdapter = new JSONConfigAdapter(getDataFolder(), "config.json");
        apConfig.load(this, jsonConfigAdapter);

        sql = new SQLManager(this, target, prefix, sqliteFile, mysql, user, pass);
        sql.connect();

        dbRunnable = new DatabaseRunnable(this, sql);
        executor.scheduleAtFixedRate(dbRunnable, 50, 50, TimeUnit.MILLISECONDS);
    }

    @Override
    public File getDataFolder() {
        return new File("test_run");
    }

    @Override
    public InputStream getResource(String string) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void info(String msg) {
        System.out.println(msg);
    }

    @Override
    public void debug(String msg) {
        info("[DEBUG] " + msg);
    }

    @Override
    public void debug(String msg, int verb) {
        debug(msg);
    }

    @Override
    public void warning(String msg) {
        System.err.println(msg);
    }

    @Override
    public void print(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public PlatformType getPlatform() {
        return PlatformType.SPIGOT;
    }

    @Override
    public SQLManager getSqlManager() {
        return sql;
    }

    @Override
    public APConfig getAPConfig() {
        return apConfig;
    }

    @Override
    public void add(DbEntry dbEntry) {
        dbRunnable.add(dbEntry);
    }

    @Override
    public void runAsync(Runnable run) {
        executor.submit(run);
    }

    @Override
    public void runSync(Runnable runnable) {
        runnable.run();
    }

    @Override
    public String getCommandPrefix() {
        return null;
    }

    @Override
    public String getCommandAlias() {
        return null;
    }

    @Override
    public SenderAdapter getConsoleSender() {
        return null;
    }

    @Nullable
    @Override
    public SenderAdapter getSenderAdapter(String name) {
        return null;
    }

    @Override
    public boolean isShuttingDown() {
        return false;
    }

    @Override
    public boolean isHooked(String name) {
        return false;
    }

    @Override
    public File getRootDirectory() {
        return null;
    }

    @Override
    public String getPlatformVersion() {
        return null;
    }

    @Override
    public String getPluginVersion() {
        return null;
    }

    @Override
    public APPlayer<?> getAPPlayer(SenderAdapter sender) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int queueSize() {
        return 0;
    }

    @Override
    public String getStackLog() {
        return null;
    }

    @Override
    public Set<String> listPlayers() {
        return null;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void addRemoveEntryListener(Consumer<DbEntry> consumer, boolean add) {

    }

    @Override
    public void broadcast(String msg, APPermission node) {

    }
}
