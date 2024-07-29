package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.utils.KeyUtil;
import dev.heliosares.auxprotect.utils.TimeUtil;
import dev.kshl.kshlib.yaml.YamlConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.function.Supplier;

public class APConfig {

    private IAuxProtect plugin;
    private boolean inventoryOnWorldChange;
    private boolean checkforupdates;
    private long posInterval;
    private long inventoryInterval;
    private long inventoryDiffInterval;
    private long moneyInterval;
    private long townBankInterval;
    private long nationBankInterval;
    private boolean overrideCommands;
    private boolean logIncrementalPosition;
    private boolean disableVacuum;
    private boolean consoleSQL;
    private boolean sessionLogIP;
    private boolean skipRowCount;
    private KeyUtil key;
    private YamlConfig config;
    private int debug;
    private boolean mysql;
    private String host;
    private String port;
    private String user;
    private String pass;
    private String database;
    private String tablePrefix;
    private long autoPurgePeriodicity;
    private boolean demoMode;
    private boolean sanitizeUnicode;

    public void load(IAuxProtect plugin, File file, Supplier<InputStream> streamSupplier) throws IOException {
        this.plugin = plugin;
        this.config = new YamlConfig(file, streamSupplier);
        reload();
    }

    public void reload() throws IOException {
        config.load();
        loadKey(plugin);
        this.debug = config.getInt("debug").orElse(0);
        checkforupdates = config.getBoolean("checkforupdates").orElse(true);
        mysql = config.getBoolean("MySQL.use").orElse(false);
        if (mysql) {
            user = config.getString("MySQL.username").orElse("");
            pass = config.getString("MySQL.password").orElse("");
            host = config.getString("MySQL.host").orElse("localhost");
            port = config.getString("MySQL.port").orElse("3306");
            database = config.getString("MySQL.database").orElse("database");
            tablePrefix = config.getString("MySQL.table-prefix").orElse("");
        } else {
            disableVacuum = config.getBoolean("disablevacuum").orElse(false);
        }
        consoleSQL = config.getBoolean("ConsoleSQLCommands").orElse(false);
        if (plugin.getPlatform().getLevel() == PlatformType.Level.SERVER) {
            overrideCommands = config.getBoolean("OverrideCommands").orElse(false);
            inventoryOnWorldChange = config.getBoolean("Actions.inventory.WorldChange").orElse(false);
            posInterval = config.getLong("Actions.pos.Interval").orElse(10000L);
            inventoryInterval = config.getLong("Actions.inventory.Interval").orElse(3600000L);
            inventoryDiffInterval = config.getLong("Actions.inventory.Diff-Interval").orElse(0L);
            moneyInterval = config.getLong("Actions.money.Interval").orElse(60000L);
            townBankInterval = config.getLong("Actions.townbank.Interval").orElse(5000L);
            nationBankInterval = config.getLong("Actions.nationbank.Interval").orElse(5000L);
            logIncrementalPosition = config.getBoolean("Actions.pos.Incremental").orElse(false);
        }
        sanitizeUnicode = config.getBoolean("SanitizeUnicode").orElse(false);
        sessionLogIP = config.getBoolean("Actions.session.LogIP").orElse(true);
        skipRowCount = config.getBoolean("SkipRowCount").orElse(false);
        for (EntryAction action : EntryAction.values()) {
            if (!action.exists()) {
                action.setEnabled(false);
                continue;
            }
            if (action == EntryAction.USERNAME) {
                action.setEnabled(true);
                continue;
            }
            boolean enabled = config.getBoolean("Actions." + action.toString().toLowerCase() + ".Enabled").orElse(true);
            boolean priority = config.getBoolean("Actions." + action.toString().toLowerCase() + ".LowestPriority").orElse(false);
            action.setEnabled(enabled);
            action.setLowestpriority(priority);
            config.set("Actions." + action.toString().toLowerCase() + ".Enabled", enabled);
        }

        if (config.getBoolean("AutoPurge.Enabled").orElse(false)) {
            autoPurgePeriodicity = TimeUtil.stringToMillis(config.getString("AutoPurge.periodicity").orElse(""));
        }
        long autopurgeinterval = getAutoPurgeInterval("default", -1);
        for (Table table : Table.values()) {
            if (table.exists(plugin) && table.canPurge()) {
                long purge = getAutoPurgeInterval("Table." + table.getName(), autopurgeinterval);
                if (getAutoPurgePeriodicity() > 0) { // Checking here instead of at the beginning allows defaults to be set at first
                    // run
                    table.setAutoPurgeInterval(purge);
                }
            }
        }
        demoMode = config.getBoolean("demomode").orElse(false);
        config.save();
    }

    private long getAutoPurgeInterval(String table, long autopurgeinterval) {
        String interval = config.getString("AutoPurge." + table).orElse(null);
        if (interval == null) interval = "default";
        config.set("AutoPurge." + table, interval);
        if (interval.equalsIgnoreCase("off") || interval.equals("-1") || interval.equals("0")) {
            return -1;
        }
        if (interval.equalsIgnoreCase("default") && autopurgeinterval >= Table.MIN_PURGE_INTERVAL) {
            return autopurgeinterval;
        }
        try {
            long time = TimeUtil.stringToMillis(interval);
            if (time >= Table.MIN_PURGE_INTERVAL || time == 0) {
                return time;
            } else {
                plugin.warning("Auto purge interval for '" + table + "' too short: '" + interval + "', min 2w");
            }
        } catch (NumberFormatException e) {
            plugin.warning("Error in config, (AutoPurge." + table + "=" + interval + "): " + e.getMessage());
        }
        return -1;
    }

    private void loadKey(IAuxProtect plugin) {
        String key = null;
        try (Scanner sc = new Scanner(new File(plugin.getRootDirectory(), "donorkey.txt"))) {
            key = sc.nextLine();
        } catch (Exception ignored) {
        }
        if (key != null) {
            this.key = new KeyUtil(key);
        }

        if (this.key != null) {
            if (this.key.isMalformed()) {
                plugin.info("Invalid donor key");
                return;
            }
            if (isPrivate()) {
                plugin.info("Private key!");
                return;
            }
            if (isDonor()) {
                plugin.info("Valid donor key!");
                return;
            }
        }
        plugin.info("No donor key");
    }

    public boolean isInventoryOnWorldChange() {
        return inventoryOnWorldChange;
    }

    public boolean shouldCheckForUpdates() {
        return checkforupdates;
    }

    public long getPosInterval() {
        return posInterval;
    }

    public long getInventoryInterval() {
        return inventoryInterval;
    }

    public long getInventoryDiffInterval() {
        return inventoryDiffInterval;
    }

    public long getMoneyInterval() {
        return moneyInterval;
    }

    public long getTownBankInterval() {
        return townBankInterval;
    }

    public long getNationBankInterval() {
        return nationBankInterval;
    }

    public boolean isPrivate() {
        if (key == null)
            return false;
        return key.isPrivate();
    }

    public boolean isDonor() {
        if (key == null)
            return false;
        return key.isValid();
    }

    public String getKeyHolder() {
        return key.getKeyHolder();
    }

    public boolean isOverrideCommands() {
        return overrideCommands;
    }

    public YamlConfig getConfig() {
        return config;
    }

    public int getDebug() {
        return debug;
    }

    public void setDebug(int debug) throws IOException {
        this.debug = debug;
        config.set("debug", debug);
        config.save();
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    public String getUser() {
        return user;
    }

    public String getPass() {
        return pass;
    }

    public String getDatabase() {
        return database;
    }

    public boolean isMySQL() {
        return mysql;
    }

    public String getTablePrefix() {
        if (!mysql) {
            return null;
        }
        return tablePrefix;
    }

    public long getAutoPurgePeriodicity() {
        return autoPurgePeriodicity;
    }

    public boolean doLogIncrementalPosition() {
        return logIncrementalPosition;
    }

    public boolean doDisableVacuum() {
        return disableVacuum;
    }

    public boolean isDemoMode() {
        return demoMode;
    }

    public boolean isSessionLogIP() {
        return sessionLogIP;
    }

    public boolean isSanitizeUnicode() {
        return sanitizeUnicode;
    }

    public boolean isSkipRowCount() {
        return skipRowCount;
    }

    public boolean isConsoleSQL() {
        return consoleSQL;
    }
}
