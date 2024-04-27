package dev.heliosares.auxprotect.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.heliosares.auxprotect.adapters.config.BungeeConfigAdapter;
import dev.heliosares.auxprotect.adapters.sender.BungeeSenderAdapter;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.adapters.sender.VelocitySenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
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
import net.kyori.adventure.text.Component;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
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
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Plugin(id="auxprotect", name="AuxProtect", version="CHANGEME", url="https://github.com/ks-hl/AuxProtect")
public final class AuxProtectVelocity implements IAuxProtect {
    private static final DateTimeFormatter ERROR_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static AuxProtectVelocity instance;
    private final APConfig config = new APConfig();
    private final HashMap<UUID, APPlayerVelocity> apPlayers = new HashMap<>();
    final Set<Integer> stackHashHistory = new HashSet<>();
    private DatabaseRunnable dbRunnable;
    SQLManager sqlManager;
    private boolean isShuttingDown;
    private String stackLog = "";
    private boolean enabled;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public AuxProtectVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }
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
        if (o instanceof Player proxiedPlayer) {
            return "$" + proxiedPlayer.getUniqueId().toString();
        }
//        if (o instanceof  pendingConnection) { TODO Velocity equivalent?
//            return "$" + pendingConnection.getUniqueId().toString();
//        }
        return "#null";
    }

    public Logger getLogger() {
        return logger;
    }

    @Override
    public void onEnable() {
        AuxProtectAPI.setInstance(instance = this);
        enabled = true;
        try {
            config.load(this, new BungeeConfigAdapter(this.getDataFolder(), "config.yml", null,
                    this::getResource, false));
        } catch (IOException e1) {
            warning("Failed to load config");
            print(e1);
        }
        // TODO reloadable
        try {
            Language.load(this,
                    () -> new BungeeConfigAdapter(getDataFolder(),
                            "lang/" + config.getConfig().getString("lang") + ".yml", null,
                            this::getResource, false), () -> new BungeeConfigAdapter(getResource("lang/en-us.yml")));

        } catch (FileNotFoundException e1) {
            warning("Language file not found");
        } catch (IOException e1) {
            warning("Failed to load lang");
            print(e1);
        }

        server.getPluginManager().registerCommand(this, new APVCommand(this, this.getCommandPrefix()));
        server.getPluginManager().registerCommand(this, new APVCommand(this, this.getCommandAlias()));
        server.getPluginManager().registerListener(this, new APVListener(this));

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
                if (!config.isSkipRowCount()) sqlManager.count();
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

        server.getScheduler().buildTask().schedule(this, dbRunnable, 250, 250, TimeUnit.MILLISECONDS);

        dbRunnable.add(new DbEntry("#console", EntryAction.PLUGINLOAD, true, "AuxProtect", ""));
    }

    @Override
    public void onDisable() {
        enabled = false;
        isShuttingDown = true;
        server.getPluginManager().unregisterListeners(this);
        server.getPluginManager().unregisterCommands(this);
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
    public File getDataFolder() {

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
        server.getScheduler().buildTask(this, run).schedule();
    }

    @Override
    public void runSync(Runnable run) {
        runAsync(run);
    }

    @Override
    public String getCommandPrefix() {
        return "auxprotectvelocity";
    }

    @Override
    public SenderAdapter getConsoleSender() {
        return new VelocitySenderAdapter(this, this.getProxy().getConsoleCommandSource());
    }

    @Nullable
    @Override
    public SenderAdapter getSenderAdapter(String name) {
        return server.getPlayer(name).map(player->new VelocitySenderAdapter(this, player)).orElse(null);
    }

    @Override
    public boolean isHooked(String name) {
        // TODO Future implementation
        return false;
    }

    @Override
    public File getRootDirectory() {
        return getDataFolder();
    }

    @Override
    public String getPlatformVersion() {
        return getProxy().getVersion().getVersion();
    }

    @Override
    public String getPluginVersion() {
        return this.getDescription().getVersion();
    }

    @Override
    public APPlayerVelocity getAPPlayer(SenderAdapter sender) {
        if (!(sender.getSender() instanceof Player proxiedPlayer)) return null;
        synchronized (apPlayers) {
            if (apPlayers.containsKey(sender.getUniqueId())) {
                return apPlayers.get(sender.getUniqueId());
            }
            APPlayerVelocity apPlayer = new APPlayerVelocity(this, proxiedPlayer);
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
        return server.getAllPlayers().stream().map(Player::getUsername).collect(Collectors.toUnmodifiableSet());
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
        server.getAllPlayers().stream().filter(player -> player.hasPermission(node.node)).forEach(player -> player.sendMessage(Component.text(msg)));
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

    public ProxyServer getProxy() {
        return server;
    }
}
