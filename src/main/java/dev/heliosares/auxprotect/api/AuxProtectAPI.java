package dev.heliosares.auxprotect.api;

import dev.heliosares.auxprotect.bungee.AuxProtectBungee;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.LookupManager;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.exceptions.AlreadyExistsException;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.SQLConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.sql.SQLException;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class AuxProtectAPI {

    private static IAuxProtect instance;

    /**
     * Returns the instance of {@link IAuxProtect} for the given platform.
     *
     * @return The IAuxProtect instance, can be {@link AuxProtectSpigot} or {@link AuxProtectBungee}
     */
    @Nonnull
    public static IAuxProtect getInstance() {
        if (instance != null) return instance;

        throw new RuntimeException("AuxProtect not initialized");
    }

    public static void setInstance(IAuxProtect plugin) {
        if (instance != null) {
            throw new IllegalStateException("Instance already set");
        }
        if(!plugin.getClass().getPackageName().startsWith("dev.heliosares.auxprotect.")) {
            throw new IllegalArgumentException("Can not set instance of plugin with non-AuxProtect instance");
        }
        instance = plugin;
    }

    /**
     * Add an entry to the queue to be logged.
     *
     * @param entry The entry to be logged.
     */
    public static void add(DbEntry entry) {
        getInstance().add(entry);
    }

    /**
     * Gets the {@link SQLManager}. This can be used to do various SQL operations. Use {@link SQLManager#execute(SQLConsumer, long)} or similar method.
     * {@link SQLConsumer} is a {@link FunctionalInterface}
     */
    public static SQLManager getSQLManager() {
        return getInstance().getSqlManager();
    }

    /**
     * Gets the {@link LookupManager} which can be used to conduct lookups similarly to the `/ap lookup` command.
     */
    public static LookupManager getLookupManager() {
        return getSQLManager().getLookupManager();
    }

    /**
     * Adds an event listener that will be fired for every entry being entered into the database.
     *
     * @param consumer The consumer which will accept the entries as they are entered.
     */
    public static void addEntryListener(Consumer<DbEntry> consumer) {
        getInstance().addRemoveEntryListener(consumer, true);
    }

    /**
     * Removes listeners entered by {@link AuxProtectAPI#addEntryListener(Consumer)}
     *
     * @param consumer The consumer to remove
     */
    public static void removeEntryListener(Consumer<DbEntry> consumer) {
        getInstance().addRemoveEntryListener(consumer, false);
    }

    public static String formatMoney(double d) {
        if (!Double.isFinite(d) || Double.isNaN(d)) {
            return "$NaN";
        }
        if (Math.abs(d) <= 1E-6) {
            return "$0";
        }

        return "$" + (Math.round(d * 100) / 100.0);
    }

    public static void info(String msg) {
        try {
            getInstance().info(msg);
            return;
        } catch (RuntimeException ignored) {
        }
        System.out.println(msg);
    }

    public static void debug(String msg) {
        try {
            getInstance().debug(msg);
            return;
        } catch (RuntimeException ignored) {
        }
        System.out.println(msg);
    }

    public static void debug(String msg, int verb) {
        try {
            getInstance().debug(msg, verb);
            return;
        } catch (RuntimeException ignored) {
        }
        System.out.println("[DEBUG " + verb + "] " + msg);
    }

    public static void warning(String msg) {
        try {
            getInstance().warning(msg);
            return;
        } catch (RuntimeException ignored) {
        }
        System.err.println(msg);
    }

    public static void print(Throwable throwable) {
        try {
            getInstance().print(throwable);
            return;
        } catch (RuntimeException ignored) {
        }
        //noinspection CallToPrintStackTrace
        throwable.printStackTrace();
    }


    /**
     * Creates a new action and stores it in the database for future use. Use
     * {@link EntryAction#getAction(String)} to determine if an action already exists, or to
     * recall it later. Actions only need to be created once, after which they are
     * automatically loaded on startup. You cannot delete actions. Be careful what
     * you create.
     *
     * @param plugin The name of the plugin creating the action. This is only used
     *               to identify the owner of an action for debug purposes.
     * @param key    The key which will be used to refer to this action in lookups.
     * @param ntext  The text which will be displayed for this action in looks for
     *               negative actions.
     * @param ptext  The text which will be displayed for this action in looks for
     *               positive actions (e.g. +session), or null for singular actions.
     * @return The newly created EntryAction.
     * @throws AlreadyExistsException   if the action you are attempting to create
     *                                  already exists or the name is taken.
     * @throws SQLException             if there is a problem connecting to the
     *                                  database.
     * @throws NullPointerException     if the plugin, key, or ntext is null or zero length.
     * @throws IllegalArgumentException if the plugin, key, or ntext is null or zero length.
     * @throws BusyException            if the database is busy
     */

    public static synchronized EntryAction createAction(@Nonnull String plugin, @Nonnull String key, @Nonnull String ntext, @Nullable String ptext) throws AlreadyExistsException, SQLException, BusyException {
        return getSQLManager().createAction(plugin, key, ntext, ptext);
    }
}
