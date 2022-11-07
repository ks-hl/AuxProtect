package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.adapters.ConfigAdapter;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.utils.KeyUtil;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class APConfig {

    private IAuxProtect plugin;
    private boolean inventoryOnWorldChange;
    private boolean checkforupdates;
    private long posInterval;
    private long inventoryInterval;
    private long inventoryDiffInterval;
    private long moneyInterval;
    private boolean overrideCommands;
    private boolean skipV6Migration;
    private KeyUtil key;
    private ConfigAdapter config;
    private int debug;
    private boolean mysql;
    private String host;
    private String port;
    private String user;
    private String pass;
    private String database;
    private String tablePrefix;

    public void load(IAuxProtect plugin, ConfigAdapter config) throws IOException {
        this.plugin = plugin;
        this.config = config;
        reload();
    }

    public void reload() throws IOException {
        config.load();
        loadKey(plugin);
        this.debug = config.getInt("debug", 0);
        checkforupdates = config.getBoolean("checkforupdates", true);
        mysql = config.getBoolean("MySQL.use", false);
        if (mysql) {
            user = config.getString("MySQL.username", "");
            pass = config.getString("MySQL.password", "");
            host = config.getString("MySQL.host", "localhost");
            port = config.getString("MySQL.port", "3306");
            database = config.getString("MySQL.database", "database");
            tablePrefix = config.getString("MySQL.table-prefix");
        }
        if (config.getPlatform() == PlatformType.SPIGOT) {
            skipV6Migration = config.getBoolean("skipv6migration");
            overrideCommands = config.getBoolean("OverrideCommands");
            inventoryOnWorldChange = config.getBoolean("Actions.inventory.WorldChange", false);
            posInterval = config.getLong("Actions.pos.Interval", 10000);
            inventoryInterval = config.getLong("Actions.inventory.Interval", 3600000);
            inventoryDiffInterval = config.getLong("Actions.inventory.Diff-Interval", 0);
            moneyInterval = config.getLong("Actions.money.Interval", 600000);
        }
        for (EntryAction action : EntryAction.values()) {
            if (!action.exists()) {
                action.setEnabled(false);
                continue;
            }
            if (action == EntryAction.USERNAME) {
                action.setEnabled(true);
                continue;
            }
            boolean enabled = config.getBoolean("Actions." + action.toString().toLowerCase() + ".Enabled", true);
            boolean priority = config.getBoolean("Actions." + action.toString().toLowerCase() + ".LowestPriority",
                    false);
            action.setEnabled(enabled);
            action.setLowestpriority(priority);
            config.set("Actions." + action.toString().toLowerCase() + ".Enabled", enabled);
        }
        config.save();
    }

    private void loadKey(IAuxProtect plugin) {
        String key = null;
        try (Scanner sc = new Scanner(new File(plugin.getRootDirectory(), "donorkey.txt"))) {
            key = sc.nextLine();
        } catch (Exception e) {
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

    public boolean doSkipV6Migration() {
        return skipV6Migration;
    }

    public ConfigAdapter getConfig() {
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
}
