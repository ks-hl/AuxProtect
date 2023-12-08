package dev.heliosares.auxprotect.bungee;

import dev.heliosares.auxprotect.adapters.config.BungeeConfigAdapter;
import dev.heliosares.auxprotect.adapters.sender.BungeeSenderAdapter;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APConfig;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.database.DatabaseRunnable;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.utils.StackUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AuxProtectBungee extends Plugin implements IAuxProtect {
    private static final DateTimeFormatter ERROR_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static AuxProtectBungee instance;
    private final APConfig config = new APConfig();
    private final HashMap<UUID, APPlayerBungee> apPlayers = new HashMap<>();
    final Set<Integer> stackHashHistory = new HashSet<>();
    protected DatabaseRunnable dbRunnable;
    SQLManager sqlManager;
    private boolean isShuttingDown;
    private String stackLog = "";
    private boolean enabled;

    public static IAuxProtect getInstance() {
        return instance;
    }

    public static String getLabel(Object o) {
        if (o == null) {
            return "#null";
        }
        if (o instanceof UUID) {
            return "$" + o;
        }
        if (o instanceof ProxiedPlayer proxiedPlayer) {
            return "$" + proxiedPlayer.getUniqueId().toString();
        }
        if (o instanceof PendingConnection pendingConnection) {
            return "$" + pendingConnection.getUniqueId().toString();
        }
        return "#null";
    }

    @Override
    public void onEnable() {
        instance = this;
        enabled = true;
        try {
            config.load(this, new BungeeConfigAdapter(this.getDataFolder(), "config.yml", null,
                    this::getResourceAsStream, false));
        } catch (IOException e1) {
            warning("Failed to load config");
            print(e1);
        }
        // TODO reloadable
        try {
            Language.load(this,
                    () -> new BungeeConfigAdapter(getDataFolder(),
                            "lang/" + config.getConfig().getString("lang") + ".yml", null,
                            this::getResourceAsStream, false), () -> new BungeeConfigAdapter(getResource("lang/en-us.yml")));

        } catch (FileNotFoundException e1) {
            warning("Language file not found");
        } catch (IOException e1) {
            warning("Failed to load lang");
            print(e1);
        }

        getProxy().getPluginManager().registerCommand(this, new APBCommand(this, this.getCommandPrefix()));
        getProxy().getPluginManager().registerCommand(this, new APBCommand(this, this.getCommandAlias()));
        getProxy().getPluginManager().registerListener(this, new APBListener(this));

        File sqliteFile = null;
        String uri = "";
        if (getAPConfig().isMySQL()) {
            uri = String.format("jdbc:mysql://%s:%s/%s", getAPConfig().getHost(), getAPConfig().getPort(),
                    getAPConfig().getDatabase());
        } else {
            sqliteFile = new File(getDataFolder(), "database/auxprotect.db");
            if (!sqliteFile.getParentFile().exists()) {
                if (!sqliteFile.getParentFile().mkdirs()) {
                    this.getLogger().severe("Failed to create database directory.");
                    this.onDisable();
                    return;
                }
            }
            if (!sqliteFile.exists()) {
                try {
                    if (!sqliteFile.createNewFile()) {
                        throw new IOException();
                    }
                } catch (IOException e) {
                    this.getLogger().severe("Failed to create database file.");
                    this.onDisable();
                    return;
                }
            }
            uri = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
        }
        String user = null;
        String pass = null;
        boolean mysql = getAPConfig().isMySQL();
        if (mysql) {
            user = getAPConfig().getUser();
            pass = getAPConfig().getPass();
        }
        try {
            sqlManager = new SQLManager(this, uri, getAPConfig().getTablePrefix(), sqliteFile, mysql, user, pass);
        } catch (ClassNotFoundException e) {
            warning("No driver for SQL found. Disabling");
            onDisable();
            throw new RuntimeException(e);
        }

        runAsync(() -> {
            try {
                sqlManager.connect();
            } catch (Exception e) {
                print(e);
                getLogger().severe("Failed to connect to SQL database. Disabling.");
                onDisable();
            }

            /*
             * for (Object command : config.getList("purge-cmds")) { String cmd = (String)
             * command; String[] argsOld = cmd.split(" "); String[] args = new
             * String[argsOld.length + 1]; args[0] = "purge"; for (int i = 0; i <
             * argsOld.length; i++) { args[i + 1] = argsOld[i]; }
             * PurgeCommand.purge(AuxProtectBungee.this, new
             * MySender(getProxy().getConsole()), args); } sqlManager.purgeUIDs();
             *
             * try { sqlManager.vacuum(); } catch (SQLException e) { print(e); }
             */
        });

        dbRunnable = new DatabaseRunnable(this, sqlManager);

        getProxy().getScheduler().schedule(this, dbRunnable, 250, 250, TimeUnit.MILLISECONDS);

        dbRunnable.add(new DbEntry("#console", EntryAction.PLUGINLOAD, true, "AuxProtect", ""));
    }

    @Override
    public void onDisable() {
        enabled = false;
        isShuttingDown = true;
        getProxy().getPluginManager().unregisterListeners(this);
        getProxy().getPluginManager().unregisterCommands(this);
        if (dbRunnable != null) {
            dbRunnable.add(new DbEntry("#console", EntryAction.PLUGINLOAD, false, "AuxProtect", ""));
            try {
                info("Logging final entries... (If you are reloading the plugin, this may cause lag)");
                sqlManager.setSkipAsyncCheck(true);
                sqlManager.execute(connection -> dbRunnable.run(true), 3000L);
            } catch (BusyException e) {
                warning("Database busy, some entries will be lost.");
            } catch (SQLException e) {
                warning("Error while logging final entries, some entries will be lost.");
                print(e);
            }
            dbRunnable = null;
        }
        if (sqlManager != null) {
            sqlManager.close();
            sqlManager = null;
        }
        info("Done disabling.");
    }

    @Override
    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    @Override
    public InputStream getResource(String string) {
        return getResourceAsStream(string);
    }

    public SQLManager getSqlManager() {
        return sqlManager;
    }

    @Override
    public void info(String string) {
        this.getLogger().info(string);
    }

    @Override
    public void debug(String string) {
        debug(string, 1);
    }

    @Override
    public void debug(String string, int verbosity) {
        if (getAPConfig().getDebug() >= verbosity) {
            this.info("DEBUG" + verbosity + ": " + string);
        }
    }

    @Override
    public void warning(String message) {
        getLogger().warning(message);
        logToStackLog("[WARNING] " + message);
    }

    @Override
    public void print(Throwable t) {
        getLogger().log(Level.WARNING, t.getMessage(), t);
        String stack = StackUtil.format(t, 3);
        if (stackHashHistory.add(stack.hashCode())) {
            stack = StackUtil.format(t, 20);
        }
        logToStackLog(stack);
    }

    private void logToStackLog(String msg) {
        stackLog += "[" + LocalDateTime.now().format(ERROR_TIME_FORMAT) + "] " + msg + "\n";
    }

    @Override
    public String getStackLog() {
        return stackLog;
    }

    @Override
    public PlatformType getPlatform() {
        return PlatformType.BUNGEE;
    }

    @Override
    public APConfig getAPConfig() {
        return config;
    }

    @Override
    public void add(DbEntry dbEntry) {
        dbRunnable.add(dbEntry);
    }

    @Override
    public void runAsync(Runnable run) {
        getProxy().getScheduler().runAsync(this, run);
    }

    @Override
    public void runSync(Runnable run) {
        runAsync(run);
    }

    @Override
    public String getCommandPrefix() {
        return "auxprotectbungee";
    }

    @Override
    public SenderAdapter getConsoleSender() {
        return new BungeeSenderAdapter(this, this.getProxy().getConsole());
    }

    @Nullable
    @Override
    public SenderAdapter getSenderAdapter(String name) {
        ProxiedPlayer target = getProxy().getPlayer(name);
        if (target == null) return null;
        return new BungeeSenderAdapter(this, target);
    }

    @Override
    public boolean isHooked(String name) {
        // TODO zz Future implementation
        return false;
    }

    @Override
    public File getRootDirectory() {
        return getDataFolder();
    }

    @Override
    public String getPlatformVersion() {
        return getProxy().getVersion();
    }

    @Override
    public String getPluginVersion() {
        return this.getDescription().getVersion();
    }

    @Override
    public APPlayerBungee getAPPlayer(SenderAdapter sender) {
        synchronized (apPlayers) {
            if (apPlayers.containsKey(sender.getUniqueId())) {
                return apPlayers.get(sender.getUniqueId());
            }
            APPlayerBungee apPlayer = new APPlayerBungee(this, (ProxiedPlayer) sender.getSender());
            apPlayers.put(sender.getUniqueId(), apPlayer);
            return apPlayer;
        }
    }

    public void removeAPPlayer(SenderAdapter sender) {
        synchronized (apPlayers) {
            apPlayers.remove(sender.getUniqueId());
        }
    }

    @Override
    public int queueSize() {
        return dbRunnable.queueSize();
    }

    @Override
    public Set<String> listPlayers() {
        return getProxy().getPlayers().stream().map(CommandSender::getName).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getCommandAlias() {
        return "apb";
    }

    @Override
    public void addRemoveEntryListener(Consumer<DbEntry> consumer, boolean add) {
        dbRunnable.addRemoveEntryListener(consumer, add);
    }

    @Override
    public void broadcast(String msg, APPermission node) {
        getProxy().getPlayers().stream().filter(player -> player.hasPermission(node.node)).forEach(player -> player.sendMessage(TextComponent.fromLegacyText(msg)));
    }
}
