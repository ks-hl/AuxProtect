package dev.heliosares.auxprotect.spigot;

import dev.heliosares.auxprotect.adapters.config.SpigotConfigAdapter;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.adapters.sender.SpigotSenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.*;
import dev.heliosares.auxprotect.core.commands.CSLogsCommand;
import dev.heliosares.auxprotect.core.commands.ClaimInvCommand;
import dev.heliosares.auxprotect.database.*;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.listeners.*;
import dev.heliosares.auxprotect.towny.TownyListener;
import dev.heliosares.auxprotect.utils.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import space.arim.morepaperlib.MorePaperLib;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class AuxProtectSpigot extends JavaPlugin implements IAuxProtect {
    public static final char LEFT_ARROW = 9668;
    public static final char RIGHT_ARROW = 9658;
    public static final char BLOCK = 9608;
    private static final DateTimeFormatter ERROR_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static AuxProtectSpigot instance;
    private static SQLManager sqlManager;
    private static MorePaperLib morePaperLib;
    private final APConfig config = new APConfig();
    private final Set<String> hooks = new HashSet<>();
    private final HashMap<UUID, APPlayerSpigot> apPlayers = new HashMap<>();
    final Set<Integer> stackHashHistory = new HashSet<>();
    public String update;
    private DatabaseRunnable dbRunnable;
    long lastCheckedForUpdate;
    private Economy econ;
    private VeinManager veinManager;
    private ClaimInvCommand claiminvcommand;
    private APSCommand apcommand;
    private int SERVER_VERSION;
    private boolean isShuttingDown;
    private boolean activityMonitorRunning;
    private boolean inventoryDiffRunning;
    private int lastLoggedActivityMinute = -1;
    private String stackLog = "";

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

    @Override
    public void onEnable() {
        AuxProtectAPI.setInstance(instance = this);
        morePaperLib = new MorePaperLib(this);
        this.saveDefaultConfig();
        super.reloadConfig();
        this.getConfig().options().copyDefaults(true);

        try {
            config.load(this, new SpigotConfigAdapter(this.getRootDirectory(), "config.yml", this.getConfig(),
                    this::getResource, false));
        } catch (IOException e1) {
            warning("Failed to load config");
            print(e1);
        }

        try {
            Language.load(this, () -> new SpigotConfigAdapter(this.getRootDirectory(),
                    "lang/" + config.getConfig().getString("lang") + ".yml", null, this::getResource, false), () -> new SpigotConfigAdapter(getResource("lang/en-us.yml")));
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
        debug("Compatability version: " + SERVER_VERSION, 1);

        File sqliteFile = null;
        String uri;
        if (getAPConfig().isMySQL()) {
            uri = String.format("jdbc:mysql://%s:%s/%s", getAPConfig().getHost(), getAPConfig().getPort(),
                    getAPConfig().getDatabase());
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
            sqlManager = new SQLManager(this, uri, getAPConfig().getTablePrefix(), sqliteFile, mysql, user, pass);
        } catch (ClassNotFoundException e) {
            warning("No driver for SQL found. Disabling");
            setEnabled(false);
            throw new RuntimeException(e);
        }
        veinManager = new VeinManager();

        AuxProtectSpigot.getMorePaperLib().scheduling().asyncScheduler().run(() -> {
            try {
                sqlManager.connect();
                if (!config.isSkipRowCount()) sqlManager.count();
            } catch (Exception e) {
                print(e);
                getLogger().severe("Failed to connect to SQL database. Disabling.");
                setEnabled(false);
                return;
            }
            if (EntryAction.VEIN.isEnabled()) {
                try {
                    ArrayList<DbEntry> veins = sqlManager
                            .getAllUnratedXrayRecords(System.currentTimeMillis() - (3600000L * 24L * 7L));
                    if (veins != null) {
                        for (DbEntry vein : veins) {
                            veinManager.add((XrayEntry) vein);
                        }
                    }
                } catch (Exception e) {
                    print(e);
                    return;
                }
            }
            long lastloaded = 0;
            try {
                lastloaded = sqlManager.getLast(SQLManager.LastKeys.TELEMETRY);
            } catch (SQLException | BusyException ignored) {
            }
            long delay = 15 * 20;
            if (System.currentTimeMillis() - lastloaded > 1000 * 60 * 60) {
                debug("Initializing telemetry. THIS MESSAGE WILL DISPLAY REGARDLESS OF WHETHER BSTATS CONFIG IS ENABLED. THIS DOES NOT INHERENTLY MEAN ITS ENABLED",
                        3);
            } else {
                debug("Delaying telemetry initialization to avoid rate-limiting. THIS MESSAGE WILL DISPLAY REGARDLESS OF WHETHER BSTATS CONFIG IS ENABLED. THIS DOES NOT INHERENTLY MEAN ITS ENABLED",
                        3);
                delay = (1000 * 60 * 60 - (System.currentTimeMillis() - lastloaded)) / 50;
            }

            AuxProtectSpigot.getMorePaperLib().scheduling().globalRegionalScheduler().runDelayed(() -> Telemetry.init(AuxProtectSpigot.this, 14232), delay);
        });

        dbRunnable = new DatabaseRunnable(this, sqlManager);

        AuxProtectSpigot.getMorePaperLib().scheduling().asyncScheduler().runAtFixedRate(dbRunnable, Duration.ofSeconds(3), Duration.ofMillis(300));
        getServer().getPluginManager().registerEvents(new ProjectileListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new PaneListener(), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(new VeinListener(this), this);

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
        if (config.isPrivate()) Objects.requireNonNull(this.getCommand("cslogs")).setExecutor(new CSLogsCommand(this));
        Objects.requireNonNull(this.getCommand("auxprotect")).setExecutor((apcommand = new APSCommand(this)));
        Objects.requireNonNull(this.getCommand("auxprotect")).setTabCompleter(apcommand);

        AuxProtectSpigot.getMorePaperLib().scheduling().globalRegionalScheduler().runDelayed(() -> {
            checkcommand("auxprotect");
            checkcommand(getCommandAlias());
            checkcommand("claiminv");
        }, 60L);

        if (!config.isPrivate()) {
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

        AuxProtectSpigot.getMorePaperLib().scheduling().asyncScheduler().runAtFixedRate(() -> {
            if (!isEnabled() || sqlManager == null || !sqlManager.isConnected() || activityMonitorRunning) {
                return;
            }
            activityMonitorRunning = true;
            try {
                List<APPlayerSpigot> players;
                // Make a new list to not tie up other calls to apPlayers
                synchronized (apPlayers) {
                    players = new ArrayList<>(apPlayers.values());
                }
                Calendar calendar = Calendar.getInstance();
                int minute = calendar.get(Calendar.MINUTE);
                int second = calendar.get(Calendar.SECOND);

                boolean logActivity = lastLoggedActivityMinute != minute && second >= 30;
                // Put in the middle of the minute to make parsing it later easier
                if (logActivity) {
                    lastLoggedActivityMinute = minute;
                }
                for (APPlayerSpigot apPlayer : players) {
                    if (!apPlayer.getPlayer().isOnline()) {
                        continue;
                    }

                    if (config.getInventoryInterval() > 0) {
                        if (System.currentTimeMillis() - apPlayer.lastLoggedInventory >= config
                                .getInventoryInterval()) {
                            apPlayer.logInventory("periodic");
                        }
                    }

                    if (config.getMoneyInterval() > 0) {
                        if (System.currentTimeMillis() - apPlayer.lastLoggedMoney >= config.getMoneyInterval()) {
                            PlayerListener.logMoney(AuxProtectSpigot.this, apPlayer.getPlayer(), "periodic");
                        }
                    }

                    if (config.getPosInterval() > 0) {
                        if (apPlayer.lastMoved > apPlayer.lastLoggedPos
                                && System.currentTimeMillis() - apPlayer.lastLoggedPos >= config.getPosInterval()) {
                            apPlayer.logPos(apPlayer.getPlayer().getLocation());
                        } else if (config.doLogIncrementalPosition()) {
                            apPlayer.tickDiffPos();
                        }
                    }

                    if (getSqlManager().getTownyManager() != null) {
                        getSqlManager().getTownyManager().run();
                    }

                    if (System.currentTimeMillis() - apPlayer.lastCheckedMovement >= 1000) {
                        apPlayer.lastCheckedMovement = System.currentTimeMillis();
                        apPlayer.move();
                    }

                    if (logActivity && config.isPrivate()) {
                        if (Set.of("flat", "void").contains(apPlayer.getPlayer().getWorld().getName()) && config.isPrivate()) {
                            apPlayer.addActivity(Activity.IN_SPAWN);
                        }

                        add(new DbEntry(AuxProtectSpigot.getLabel(apPlayer.getPlayer()), EntryAction.ACTIVITY, false, apPlayer.getPlayer().getLocation(), "", apPlayer.concludeActivityForMinute()));

                        int tallied = 0;
                        int inactive = 0;
                        for (ActivityRecord record : apPlayer.getActivityStack()) {
                            if (record == null || record.activities().isEmpty()) {
                                continue;
                            }
                            tallied++;
                            if (record.countScore() < 10) {
                                inactive++;
                            }
                        }
                        if (tallied >= 15 && (double) inactive / (double) tallied > 0.75
                                && !APPermission.BYPASS_INACTIVE.hasPermission(apPlayer.getPlayer())) {
                            if (System.currentTimeMillis() - apPlayer.lastNotifyInactive > 600000L) {
                                apPlayer.lastNotifyInactive = System.currentTimeMillis();
                                String msg = Language.translate(Language.L.INACTIVE_ALERT, apPlayer.getPlayer().getName(),
                                        inactive, tallied);
                                for (Player player : Bukkit.getOnlinePlayers()) {
                                    if (APPermission.NOTIFY_INACTIVE.hasPermission(player)) {
                                        player.sendMessage(msg);
                                    }
                                }
                                info(msg);
                                add(new DbEntry(AuxProtectSpigot.getLabel(apPlayer.getPlayer()), EntryAction.ALERT, false,
                                        apPlayer.getPlayer().getLocation(), "inactive", inactive + "/" + tallied));
                            }
                        }
                    }
                }
            } finally {
                activityMonitorRunning = false;
            }
        }, Duration.ofSeconds(2), Duration.ofMillis(200));

        AuxProtectSpigot.getMorePaperLib().scheduling().asyncScheduler().runAtFixedRate(() -> {
            if (!isEnabled() || sqlManager == null) return;

            String migrationStatus = sqlManager.getMigrationStatus();
            if (migrationStatus != null) info(migrationStatus);

            if (inventoryDiffRunning || !sqlManager.isConnected()) return;
            inventoryDiffRunning = true;
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
                inventoryDiffRunning = false;
            }
        }, Duration.ofSeconds(2), Duration.ofSeconds(1));

        AuxProtectSpigot.getMorePaperLib().scheduling().asyncScheduler().runAtFixedRate(() -> {
            if (config.shouldCheckForUpdates()
                    && System.currentTimeMillis() - lastCheckedForUpdate > 1000 * 60 * 60) {
                lastCheckedForUpdate = System.currentTimeMillis();
                debug("Checking for updates...", 1);
                String newVersion;
                try {
                    newVersion = UpdateChecker.getVersion(99147);
                } catch (IOException e) {
                    print(e);
                    return;
                }
                debug("New Version: " + newVersion + " Current Version: "
                        + AuxProtectSpigot.this.getDescription().getVersion(), 1);
                if (newVersion != null) {
                    int compare = UpdateChecker.compareVersions(AuxProtectSpigot.this.getDescription().getVersion(),
                            newVersion);
                    if (compare <= 0) {
                        update = null;
                    } else {
                        boolean newUpdate = update == null;
                        update = newVersion;
                        if (newUpdate) {
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (APPermission.ADMIN.hasPermission(player)) {
                                    AuxProtectSpigot.this.tellAboutUpdate(player);
                                }
                            }
                            AuxProtectSpigot.this.tellAboutUpdate(Bukkit.getConsoleSender());
                        }
                    }
                }
            }
        }, Duration.ofSeconds(1), Duration.ofSeconds(10));

        dbRunnable.add(new DbEntry("#console", EntryAction.PLUGINLOAD, true, "AuxProtect", ""));
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

    public APPlayerSpigot getAPPlayer(Player player) {
        synchronized (apPlayers) {
            if (apPlayers.containsKey(player.getUniqueId())) {
                return apPlayers.get(player.getUniqueId());
            }
            APPlayerSpigot apPlayer = new APPlayerSpigot(this, player);
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
        new SpigotSenderAdapter(this, sender).sendLang(Language.L.UPDATE,
                AuxProtectSpigot.this.getDescription().getVersion(), update);
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
        PlaybackSolver.shutdown();
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

    public SQLManager getSqlManager() {
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
        // This is only async because veinManager performs SQL lookups
        runAsync(() -> {
            if (entry instanceof XrayEntry) {
                if (veinManager.add((XrayEntry) entry)) {
                    return;
                }
            }
            dbRunnable.add(entry);
        });
    }

    public VeinManager getVeinManager() {
        return veinManager;
    }

    @Override
    public void runAsync(Runnable run) {
        AuxProtectSpigot.getMorePaperLib().scheduling().asyncScheduler().run((run));
    }

    @Override
    public void runSync(Runnable run) {
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
    public SenderAdapter getSenderAdapter(String name) {
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
    public APPlayerSpigot getAPPlayer(SenderAdapter sender) {
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

    public static @NotNull MorePaperLib getMorePaperLib() {
        return morePaperLib;
    }
}
