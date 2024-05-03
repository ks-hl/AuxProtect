package dev.heliosares.auxprotect.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.heliosares.auxprotect.AuxProtectVersion;
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
import dev.kshl.kshlib.yaml.YamlConfig;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Plugin(id = "auxprotect", name = "AuxProtect", version = AuxProtectVersion.VERSION, url = "https://github.com/ks-hl/AuxProtect")
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
        if (o instanceof UUID) {
            return "$" + o;
        }
        if (o instanceof Player player) {
            return getLabel(player.getUniqueId());
        }
        if (o instanceof ConsoleCommandSource) {
            return "#console";
        }
        return "#null";
    }

    public Logger getLogger() {
        return logger;
    }


    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {

        AuxProtectAPI.setInstance(instance = this);
        enabled = true;
        try {
            config.load(this, new File(this.getDataFolder(), "config.yml"), () -> getResource("config.yml"));
        } catch (Exception e1) {
            warning("Failed to load config");
            print(e1);
        }
        // TODO reloadable
        try {
            String langFileName = "lang/" + config.getConfig().getString("lang").orElse("") + ".yml";
            Language.load(this, () -> new YamlConfig(new File(getDataFolder(), langFileName), () -> getResource(langFileName)), () -> new YamlConfig(null, () -> getResource(langFileName)));
        } catch (FileNotFoundException e1) {
            warning("Language file not found");
        } catch (Exception e1) {
            warning("Failed to load lang");
            print(e1);
        }

        server.getCommandManager().register(getCommandPrefix(), new APVCommand(this, this.getCommandPrefix()), getCommandAlias(), "apb");
        server.getEventManager().register(this, new APVListener(this));

        File sqliteFile = null;
        String uri;
        if (getAPConfig().isMySQL()) {
            uri = String.format("jdbc:mysql://%s:%s/%s", getAPConfig().getHost(), getAPConfig().getPort(), getAPConfig().getDatabase());
        } else {
            sqliteFile = new File(getDataFolder(), "database/auxprotect.db");
            if (!sqliteFile.getParentFile().exists()) {
                if (!sqliteFile.getParentFile().mkdirs()) {
                    this.getLogger().severe("Failed to create database directory.");
                    onProxyShutdown(null);
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
                    onProxyShutdown(null);
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
            onProxyShutdown(null);
            throw new RuntimeException(e);
        }

        runAsync(() -> {
            try {
                sqlManager.connect();
                if (!config.isSkipRowCount()) sqlManager.count();
            } catch (Exception e) {
                print(e);
                getLogger().severe("Failed to connect to SQL database. Disabling.");
                onProxyShutdown(null);
            }
        });

        dbRunnable = new DatabaseRunnable(this, sqlManager);

        server.getScheduler().buildTask(this, dbRunnable).repeat(250, TimeUnit.MILLISECONDS).schedule();

        dbRunnable.add(new DbEntry("#console", EntryAction.PLUGINLOAD, true, "AuxProtect", ""));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        enabled = false;
        isShuttingDown = true;
        server.getEventManager().unregisterListeners(this);
//        server.getCommandManager().unregisterCommands(this); TODO necessary?
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
        return dataDirectory.toFile();
    }

    @Override
    public InputStream getResource(String string) {
        return getClass().getClassLoader().getResourceAsStream(string);
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
        return PlatformType.VELOCITY;
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
    public String getCommandAlias() {
        return "apv";
    }

    @Override
    public VelocitySenderAdapter getConsoleSender() {
        return new VelocitySenderAdapter(this, this.getProxy().getConsoleCommandSource());
    }

    @Nullable
    @Override
    public VelocitySenderAdapter getSenderAdapter(String name) {
        return server.getPlayer(name).map(player -> new VelocitySenderAdapter(this, player)).orElse(null);
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
        return getPlugin().map(plugin -> plugin.getDescription().getVersion().orElse("!blank")).orElse("!plugin not found");
    }

    public Optional<PluginContainer> getPlugin() {
        return this.server.getPluginManager().getPlugin("auxprotect");
    }

    @Override
    public APPlayerVelocity getAPPlayer(SenderAdapter<?, ?> sender) {
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

    public void removeAPPlayer(UUID uuid) {
        synchronized (apPlayers) {
            apPlayers.remove(uuid);
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
