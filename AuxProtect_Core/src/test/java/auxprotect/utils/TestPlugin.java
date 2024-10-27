package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.adapters.message.MessageBuilder;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.*;
import dev.heliosares.auxprotect.database.DatabaseRunnable;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.exceptions.BusyException;
import jakarta.annotation.Nullable;

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
        apConfig = new APConfig();
        apConfig.load(this, new File(getDataFolder(), "config.yml"), () -> getClass().getClassLoader().getResourceAsStream("config.yml"));

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

    @Override
    public boolean doesWorldExist(String world) {
        return false;
    }

    @Override
    public Set<String> getWorlds() {
        return Set.of();
    }

    @Override
    public boolean isPrimaryThread() {
        return false;
    }

    @Override
    public MessageBuilder getMessageBuilder() {
        return null;
    }

    @Override
    public Set<String> getEntityTypes() {
        return Set.of();
    }

    @Override
    public Set<String> getItemTypes() {
        return Set.of();
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    public String formatMoney(double d) {
        if (!Double.isFinite(d) || Double.isNaN(d)) {
            return "$NaN";
        }
        if (Math.abs(d) <= 1E-6) {
            return "$0";
        }

        return "$" + (Math.round(d * 100) / 100.0);
    }
}
