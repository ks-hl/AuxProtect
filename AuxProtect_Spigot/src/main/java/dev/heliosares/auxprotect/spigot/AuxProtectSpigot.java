package dev.heliosares.auxprotect.spigot;

import dev.heliosares.auxprotect.adapters.message.MessageBuilder;
import dev.heliosares.auxprotect.adapters.message.SpigotMessageBuilder;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.adapters.sender.SpigotSenderAdapter;
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
import dev.heliosares.auxprotect.database.SpigotDatabaseRunnable;
import dev.heliosares.auxprotect.database.SpigotSQLManager;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.commands.ClaimInvCommand;
import dev.heliosares.auxprotect.spigot.listeners.AuctionHouseListener;
import dev.heliosares.auxprotect.spigot.listeners.ChestShopListener;
import dev.heliosares.auxprotect.spigot.listeners.CommandListener;
import dev.heliosares.auxprotect.spigot.listeners.DynamicShopListener;
import dev.heliosares.auxprotect.spigot.listeners.EconomyShopGUIListener;
import dev.heliosares.auxprotect.spigot.listeners.EntityListener;
import dev.heliosares.auxprotect.spigot.listeners.EssentialsListener;
import dev.heliosares.auxprotect.spigot.listeners.InventoryListener;
import dev.heliosares.auxprotect.spigot.listeners.JobsListener;
import dev.heliosares.auxprotect.spigot.listeners.PaneListener;
import dev.heliosares.auxprotect.spigot.listeners.PlayerAuctionsListener;
import dev.heliosares.auxprotect.spigot.listeners.PlayerListener;
import dev.heliosares.auxprotect.spigot.listeners.ProjectileListener;
import dev.heliosares.auxprotect.spigot.listeners.ShopGUIPlusListener;
import dev.heliosares.auxprotect.spigot.listeners.WorldListener;
import dev.heliosares.auxprotect.towny.TownyListener;
import dev.heliosares.auxprotect.towny.TownyManager;
import dev.heliosares.auxprotect.utils.Pane;
import dev.heliosares.auxprotect.utils.StackUtil;
import dev.heliosares.auxprotect.utils.UpdateChecker;
import dev.heliosares.auxprotect.utils.YamlConfig;
import jakarta.annotation.Nullable;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AuxProtectSpigot extends JavaPlugin implements IAuxProtect {
    public static final char BLOCK = 9608;
    private static final DateTimeFormatter ERROR_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static AuxProtectSpigot instance;
    private static SpigotSQLManager sqlManager;
    private final APConfig config = new APConfig();
    private final Set<String> hooks = new HashSet<>();
    private final HashMap<UUID, APPlayerSpigot> apPlayers = new HashMap<>();
    final Set<Integer> stackHashHistory = new HashSet<>();
    public String update;
    private DatabaseRunnable dbRunnable;
    long lastCheckedForUpdate;
    private Economy econ;
    private ClaimInvCommand claiminvcommand;
    private APSCommand apcommand;
    private int SERVER_VERSION;
    private boolean isShuttingDown;
    private String stackLog = "";

    public static AuxProtectSpigot getInstance() {
        return instance;
    }

    public static String getLabel(Object o) {
        if (o == null) {
            return "#null";
        }
        if (o instanceof UUID) {
            return "$" + o;
        }
        if (o instanceof Player) {
            return "$" + ((Player) o).getUniqueId();
        }
        if (o instanceof Entity) {
            return "#" + ((Entity) o).getType().name().toLowerCase();
        }
        if (o instanceof Container) {
            return "#" + ((Container) o).getBlock().getType().toString().toLowerCase();
        }
        if (o instanceof Block) {
            return "#" + ((Block) o).getType().toString().toLowerCase();
        }
        if (o instanceof Material) {
            return o.toString().toLowerCase();
        }
        return "#null";
    }

    public ClaimInvCommand getClaiminvcommand() {
        return claiminvcommand;
    }

    public APSCommand getApcommand() {
        return apcommand;
    }

    public int getCompatabilityVersion() {
        return SERVER_VERSION;
    }

    protected APSCommand createAPSCommand() {
        return new APSCommand(this);
    }

    @Override
    public void onEnable() {
        AuxProtectAPI.setInstance(instance = this);
        this.saveDefaultConfig();
        super.reloadConfig();
        this.getConfig().options().copyDefaults(true);

        try {
            config.load(this, new File(this.getRootDirectory(), "config.yml"), () -> getResource("config.yml"));
        } catch (IOException e1) {
            warning("Failed to load config");
            print(e1);
        }

        try {
            String langFileName = "lang/" + config.getConfig().getString("lang").orElse("") + ".yml";
            Language.load(this,
                    () -> new YamlConfig(new File(getDataFolder(), langFileName),
                            () -> getResource(langFileName)),
                    () -> new YamlConfig(null, () -> getResource(langFileName)));
        } catch (FileNotFoundException e1) {
            warning("Language file not found");
        } catch (IOException e1) {
            warning("Failed to load lang");
            print(e1);
        }

        debug("Parsing: " + Bukkit.getBukkitVersion());
        try {
            SERVER_VERSION = Integer.parseInt(Bukkit.getBukkitVersion().split("[.-]")[1]);
        } catch (Exception e) {
            warning("Failed to parse version string: \"" + Bukkit.getBukkitVersion() + "\". Defaulting to 1.16");
            SERVER_VERSION = 16;
            print(e);
        }
        debug("Compatibility version: " + SERVER_VERSION, 1);

        File sqliteFile = null;
        String uri;
        if (getAPConfig().isMySQL()) {
            uri = String.format("jdbc:mysql://%s:%s/%s", getAPConfig().getHost(), getAPConfig().getPort(), getAPConfig().getDatabase());
        } else {
            sqliteFile = new File(getDataFolder(), "database/auxprotect.db");
            if (!sqliteFile.getParentFile().exists()) {
                if (!sqliteFile.getParentFile().mkdirs()) {
                    this.getLogger().severe("Failed to create database directory. Disabling");
                    this.setEnabled(false);
                    return;
                }
            }
            if (!sqliteFile.exists()) {
                try {
                    if (!sqliteFile.createNewFile()) {
                        throw new IOException();
                    }
                } catch (IOException e) {
                    this.getLogger().severe("Failed to create database file. Disabling");
                    this.setEnabled(false);
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
            sqlManager = newSQLManager(uri, getAPConfig().getTablePrefix(), sqliteFile, mysql, user, pass);
        } catch (ClassNotFoundException e) {
            warning("No driver for SQL found. Disabling");
            setEnabled(false);
            throw new RuntimeException(e);
        }

        getServer().getScheduler().runTaskAsynchronously(this, this::initDatabase);

        dbRunnable = new SpigotDatabaseRunnable(this, sqlManager);
        getServer().getScheduler().runTaskTimerAsynchronously(this, dbRunnable, 60, 5);

        getServer().getPluginManager().registerEvents(new ProjectileListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new PaneListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandListener(this), this);

        // this feels cursed to run setupEconomy() like this...
        Telemetry.reportHook(this, "Vault", setupEconomy());

        EntryAction.SHOP_SGP.setEnabled(hook(() -> new ShopGUIPlusListener(this), "ShopGuiPlus"));
        EntryAction.SHOP_ESG.setEnabled(hook(() -> new EconomyShopGUIListener(this), "EconomyShopGUI", "EconomyShopGUI-Premium"));
        EntryAction.SHOP_DS.setEnabled(hook(() -> new DynamicShopListener(this), "DynamicShop"));
        EntryAction.SHOP_CS.setEnabled(hook(() -> new ChestShopListener(this), "ChestShop"));

        boolean auctionHook = hook(() -> new AuctionHouseListener(this), "AuctionHouse");
        if (hook(() -> new PlayerAuctionsListener(this), "PlayerAuctions")) auctionHook = true;
        if (!auctionHook) {
            EntryAction.AUCTIONBUY.setEnabled(false);
            EntryAction.AUCTIONLIST.setEnabled(false);
        }
        if (!hook(() -> new JobsListener(this), "Jobs")) {
            EntryAction.JOBS.setEnabled(false);
        }
        if (!hook(() -> new EssentialsListener(this), "Essentials")) {
            EntryAction.PAY.setEnabled(false);
        }
        if (!hook(() -> new TownyListener(this), "Towny")) {
            for (EntryAction action : EntryAction.values()) {
                if (action.getTable() == Table.AUXPROTECT_TOWNY) {
                    action.setEnabled(false);
                }
            }
            EntryAction.TOWNYNAME.setEnabled(false);
        }

        Objects.requireNonNull(this.getCommand("claiminv")).setExecutor(claiminvcommand = new ClaimInvCommand(this));
        Objects.requireNonNull(this.getCommand("auxprotect")).setExecutor((apcommand = createAPSCommand()));
        Objects.requireNonNull(this.getCommand("auxprotect")).setTabCompleter(apcommand);

        new BukkitRunnable() {

            @Override
            public void run() {
                checkcommand("auxprotect");
                checkcommand(getCommandAlias());
                checkcommand("claiminv");
            }

            private void checkcommand(String commandlbl) {
                PluginCommand command = getCommand(commandlbl);
                if (command == null || !command.getPlugin().equals(AuxProtectSpigot.this)) {
                    String output = "Command '" + commandlbl + "' taken by ";
                    if (command == null) {
                        output += "an unknown plugin.";
                    } else {
                        output += command.getPlugin().getName() + ".";
                    }
                    warning(output);
                    if (config.isOverrideCommands()) {
                        warning("Attempting to re-register tab completer.");
                        Objects.requireNonNull(getCommand("auxprotect")).setTabCompleter(apcommand);
                        Objects.requireNonNull(getCommand(getCommandAlias())).setTabCompleter(apcommand);
                    } else {
                        warning("If this is causing issues, try enabling 'OverrideCommands' in the config.");
                    }

                }
            }
        }.runTaskLater(this, 60);

        if (!isPrivate()) {
            EntryAction.ALERT.setEnabled(false);
            EntryAction.CENSOR.setEnabled(false);
            EntryAction.IGNOREABANDONED.setEnabled(false);
            EntryAction.VEIN.setEnabled(false);
        }

        // This is a patch for when reloading the plugin, online players will receive
        // immediate periodic logs
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.getAPPlayer(player);
        }

        new BukkitRunnable() {

            private boolean running;

            @Override
            public void run() {
                if (!isEnabled() || sqlManager == null || !sqlManager.isConnected() || running) {
                    return;
                }
                running = true;
                try {
                    List<APPlayerSpigot> players;
                    // Make a new list to not tie up other calls to apPlayers
                    synchronized (apPlayers) {
                        players = new ArrayList<>(apPlayers.values());
                    }

                    for (APPlayerSpigot apPlayer : players) {
                        periodicPlayerTick(apPlayer);
                    }
                } finally {
                    running = false;
                }
            }
        }.runTaskTimerAsynchronously(this, 40, 4);
        new BukkitRunnable() {

            private boolean running;

            @Override
            public void run() {
                if (!isEnabled() || sqlManager == null) return;

                String migrationStatus = sqlManager.getMigrationStatus();
                if (migrationStatus != null) info(migrationStatus);

                if (running || !sqlManager.isConnected()) return;
                running = true;
                try {
                    List<APPlayerSpigot> players;
                    // Make a new list to not tie up other calls to apPlayers
                    synchronized (apPlayers) {
                        players = new ArrayList<>(apPlayers.values());
                    }
                    for (APPlayerSpigot apPlayer : players) {
                        if (!apPlayer.getPlayer().isOnline()) {
                            continue;
                        }
                        if (config.getInventoryDiffInterval() > 0) {
                            if (System.currentTimeMillis() - apPlayer.lastLoggedInventoryDiff >= config.getInventoryDiffInterval()) {
                                apPlayer.tickDiffInventory();
                            }
                        }
                    }
                } finally {
                    running = false;
                }
            }
        }.runTaskTimerAsynchronously(this, 40, 20);

        new BukkitRunnable() {

            @Override
            public void run() {
                if (config.shouldCheckForUpdates() && System.currentTimeMillis() - lastCheckedForUpdate > 1000 * 60 * 60) {
                    lastCheckedForUpdate = System.currentTimeMillis();
                    debug("Checking for updates...", 1);
                    String newVersion;
                    try {
                        newVersion = UpdateChecker.getVersion(99147);
                    } catch (IOException e) {
                        print(e);
                        return;
                    }
                    debug("New Version: " + newVersion + " Current Version: " + AuxProtectSpigot.this.getDescription().getVersion(), 1);
                    if (newVersion != null) {
                        int compare = UpdateChecker.compareVersions(AuxProtectSpigot.this.getDescription().getVersion(), newVersion);
                        if (compare <= 0) {
                            update = null;
                        } else {
                            boolean newUpdate = update == null;
                            update = newVersion;
                            if (newUpdate) {
                                for (Player player : Bukkit.getOnlinePlayers()) {
                                    if (APPermission.ADMIN.hasPermission(new SpigotSenderAdapter(AuxProtectSpigot.this, player))) {
                                        AuxProtectSpigot.this.tellAboutUpdate(player);
                                    }
                                }
                                AuxProtectSpigot.this.tellAboutUpdate(Bukkit.getConsoleSender());
                            }
                        }
                    }
                }
            }
        }.runTaskTimerAsynchronously(this, 20, 10 * 20);

        dbRunnable.add(new DbEntry("#console", EntryAction.PLUGINLOAD, true, "AuxProtect", ""));
    }

    protected SpigotSQLManager newSQLManager(String uri, String tablePrefix, File sqliteFile, boolean mysql, String user, String pass) throws ClassNotFoundException {
        return new SpigotSQLManager(this, uri, tablePrefix, sqliteFile, mysql, user, pass);
    }

    protected void periodicPlayerTick(APPlayerSpigot apPlayer) {
        if (!apPlayer.getPlayer().isOnline()) {
            return;
        }

        if (config.getInventoryInterval() > 0) {
            if (System.currentTimeMillis() - apPlayer.lastLoggedInventory >= config.getInventoryInterval()) {
                apPlayer.logInventory("periodic");
            }
        }

        if (config.getMoneyInterval() > 0) {
            if (System.currentTimeMillis() - apPlayer.lastLoggedMoney >= config.getMoneyInterval()) {
                PlayerListener.logMoney(AuxProtectSpigot.this, apPlayer.getPlayer(), "periodic");
            }
        }

        if (config.getPosInterval() > 0) {
            if (apPlayer.lastMoved > apPlayer.lastLoggedPos && System.currentTimeMillis() - apPlayer.lastLoggedPos >= config.getPosInterval()) {
                apPlayer.logPos(apPlayer.getPlayer().getLocation());
            } else if (config.doLogIncrementalPosition()) {
                apPlayer.tickDiffPos();
            }
        }

        if (getTownyManager() != null) {
            getTownyManager().run();
        }

        if (System.currentTimeMillis() - apPlayer.lastCheckedMovement >= 1000) {
            apPlayer.lastCheckedMovement = System.currentTimeMillis();
            apPlayer.move();
        }
    }

    protected void initDatabase() {
        try {
            sqlManager.connect();
            if (!config.isSkipRowCount()) sqlManager.count();
        } catch (Exception e) {
            print(e);
            getLogger().severe("Failed to connect to SQL database. Disabling.");
            setEnabled(false);
            return;
        }

        long lastloaded = 0;
        try {
            lastloaded = sqlManager.getLast(SQLManager.LastKeys.TELEMETRY);
        } catch (SQLException | BusyException ignored) {
        }
        long delay = 15 * 20;
        if (System.currentTimeMillis() - lastloaded > 1000 * 60 * 60) {
            debug("Initializing telemetry. THIS MESSAGE WILL DISPLAY REGARDLESS OF WHETHER BSTATS CONFIG IS ENABLED. THIS DOES NOT INHERENTLY MEAN ITS ENABLED", 3);
        } else {
            debug("Delaying telemetry initialization to avoid rate-limiting. THIS MESSAGE WILL DISPLAY REGARDLESS OF WHETHER BSTATS CONFIG IS ENABLED. THIS DOES NOT INHERENTLY MEAN ITS ENABLED", 3);
            delay = (1000 * 60 * 60 - (System.currentTimeMillis() - lastloaded)) / 50;
        }

        getServer().getScheduler().runTaskLater(AuxProtectSpigot.this, () -> Telemetry.init(AuxProtectSpigot.this, 14232), delay);

    }

    private boolean hook(Supplier<Listener> listener, String... names) {
        boolean hook;
        try {
            Plugin plugin = null;
            for (String name : names) {
                plugin = getPlugin(name);
                if (plugin != null) {
                    break;
                }
            }
            hook = plugin != null && plugin.isEnabled();
            if (hook) {
                getServer().getPluginManager().registerEvents(listener.get(), this);
                hooks.add(plugin.getName());
            }
        } catch (Exception e) {
            warning("Exception while hooking " + names[0]);
            print(e);
            hook = false;
        }
        Telemetry.reportHook(this, names[0], hook);
        return hook;
    }

    public boolean isHooked(String name) {
        return hooks.contains(name);
    }

    private Plugin getPlugin(String name) {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.getName().equalsIgnoreCase(name)) {
                return plugin;
            }
        }
        return null;
    }

    protected APPlayerSpigot newAPPlayer(Player player) {
        return new APPlayerSpigot(this, player);
    }

    public APPlayerSpigot getAPPlayer(Player player) {
        synchronized (apPlayers) {
            if (apPlayers.containsKey(player.getUniqueId())) {
                return apPlayers.get(player.getUniqueId());
            }
            APPlayerSpigot apPlayer = newAPPlayer(player);
            apPlayers.put(player.getUniqueId(), apPlayer);
            return apPlayer;
        }
    }

    public void removeAPPlayer(Player player) {
        synchronized (apPlayers) {
            apPlayers.remove(player.getUniqueId());
        }
    }

    public void tellAboutUpdate(CommandSender sender) {
        new SpigotSenderAdapter(this, sender).sendLang(Language.L.UPDATE, AuxProtectSpigot.this.getDescription().getVersion(), update);
    }

    @Override
    public void onDisable() {
        isShuttingDown = true;
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
        Pane.shutdown();
        info("Done disabling.");
    }

    @Override
    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return true;
    }

    @Override
    public boolean doesWorldExist(String world) {
        return world != null && Bukkit.getWorld(world) != null;
    }

    @Override
    public Set<String> getWorlds() {
        return Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toSet());
    }

    @Override
    public boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public MessageBuilder getMessageBuilder() {
        return new SpigotMessageBuilder();
    }

    @Override
    public Set<String> getEntityTypes() {
        return Arrays.stream(EntityType.values()).map(EntityType::toString).collect(Collectors.toSet());
    }

    @Override
    public Set<String> getItemTypes() {
        return Arrays.stream(Material.values()).map(Material::toString).collect(Collectors.toSet());
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public void info(String string) {
        string = ChatColor.stripColor(string);
        this.getLogger().info(string);
        logToStackLog("[INFO] " + string);
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

    public SpigotSQLManager getSqlManager() {
        return sqlManager;
    }

    public Economy getEconomy() {
        return econ;
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
        return PlatformType.SPIGOT;
    }

    public APConfig getAPConfig() {
        return config;
    }

    @Override
    public void add(DbEntry entry) {
        dbRunnable.add(entry);
    }

    @Override
    public void runAsync(Runnable run) {
        getServer().getScheduler().runTaskAsynchronously(this, run);
    }

    @Override
    public void runSync(Runnable run) {
        getServer().getScheduler().runTask(this, run);
    }

    public int queueSize() {
        return dbRunnable.queueSize();
    }

    @Override
    public String getCommandPrefix() {
        return "auxprotect";
    }

    @Override
    public SpigotSenderAdapter getConsoleSender() {
        return new SpigotSenderAdapter(this, this.getServer().getConsoleSender());
    }

    @Nullable
    @Override
    public SpigotSenderAdapter getSenderAdapter(String name) {
        Player target = getServer().getPlayer(name);
        if (target == null) return null;
        return new SpigotSenderAdapter(this, target);
    }

    @Override
    public File getRootDirectory() {
        return getDataFolder();
    }

    @Override
    public String getPlatformVersion() {
        return getServer().getVersion();
    }

    @Override
    public String getPluginVersion() {
        return this.getDescription().getVersion();
    }

    @Override
    public APPlayerSpigot getAPPlayer(SenderAdapter<?, ?> sender) {
        if (!(sender.getSender() instanceof Player player)) return null;
        return getAPPlayer(player);
    }

    @Override
    public String formatMoney(double amount) {
        if (econ != null) return econ.format(amount);

        if (!Double.isFinite(amount) || Double.isNaN(amount)) {
            return "$NaN";
        }
        if (Math.abs(amount) <= 1E-6) {
            return "$0";
        }

        return "$" + (Math.round(amount * 100) / 100.0);
    }

    @Override
    public Set<String> listPlayers() {
        return getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void addRemoveEntryListener(Consumer<DbEntry> consumer, boolean add) {
        dbRunnable.addRemoveEntryListener(consumer, add);
    }

    @Override
    public void broadcast(String msg, APPermission node) {
        Bukkit.broadcast(msg, node.node);
    }

    @Override
    public String getCommandAlias() {
        return "ap";
    }

    public TownyManager getTownyManager() {
        return getSqlManager().getTownyManager();
    }

}
