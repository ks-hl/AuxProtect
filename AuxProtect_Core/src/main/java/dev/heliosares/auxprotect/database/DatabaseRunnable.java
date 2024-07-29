package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.exceptions.BusyException;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class DatabaseRunnable implements Runnable {
    private static final HashMap<Table, Long> lastTimes = new HashMap<>();
    private static final long pickupCacheTime = 1500;
    @Nonnull
    private final SQLManager sqlManager;
    @Nonnull
    private final IAuxProtect plugin;
    private final ConcurrentLinkedQueue<PickupEntry> pickups = new ConcurrentLinkedQueue<>();
    private long lastWarn = 0;
    private long lockedSince;

    public DatabaseRunnable(@Nonnull IAuxProtect plugin, @Nonnull SQLManager sqlManager) {
        this.sqlManager = sqlManager;
        this.plugin = plugin;
    }

    public static synchronized long getTime(Table table) {
        long time = System.currentTimeMillis();
        Long lastTime = lastTimes.get(table);
        if (lastTime != null && time <= lastTime) {
            time = lastTime + 1;
        }
        lastTimes.put(table, time);
        return time;
    }

    public void add(DbEntry entry) {
        if (!entry.getAction().isEnabled()) {
            return;
        }
        if (entry instanceof PickupEntry) {
            this.addPickup((PickupEntry) entry);
            return;
        }
        Table table = entry.getAction().getTable();
        if (table == null) {
            return;
        }
        table.queue.add(entry);
        synchronized (listeners) {
            listeners.forEach(c -> c.accept(entry));
        }
    }

    public int queueSize() {
        Optional<Integer> opt = Arrays.stream(Table.values()).map(t -> t.queue.size()).reduce(Integer::sum);
        return opt.orElse(0);
    }

    @Override
    public void run() {
        if (!plugin.isEnabled() || !sqlManager.isConnected()) {
            return;
        }
        if (lockedSince > 0) {
            long locked = System.currentTimeMillis() - lockedSince;
            if (locked > 120000 && System.currentTimeMillis() - lastWarn > 600000) {
                lastWarn = System.currentTimeMillis();
                plugin.warning("Overlapping logging windows by " + locked + " ms.");
            }
            plugin.debug("Overlapping logging windows by " + locked + " ms.", 1);
            if (locked < 300000) {
                return;
            } else {
                plugin.warning("Overlapping logging windows by 5 minutes, continuing.");
            }
        }
        run(false);
    }

    public synchronized void run(boolean force) {
        lockedSince = System.currentTimeMillis();
        try {
            checkCache(force);
            sqlManager.tick();
        } catch (Throwable e) {
            plugin.print(e);
        } finally {
            lockedSince = 0;
        }
    }

    protected void checkCache(boolean force) {
        synchronized (pickups) {
            Iterator<PickupEntry> itr = pickups.iterator();
            while (itr.hasNext()) {
                PickupEntry next = itr.next();
                if (force || next.getTime() < System.currentTimeMillis() - pickupCacheTime) {
                    Table.AUXPROTECT_INVENTORY.queue.add(next);
                    itr.remove();
                }
            }
        }
    }

    private void addPickup(PickupEntry entry) {
        synchronized (pickups) {
            for (PickupEntry next : pickups) {
                if (next.getTime() < System.currentTimeMillis() - pickupCacheTime) continue;
                if (next.getAction() != entry.getAction()) continue;
                try {
                    if (!next.getUserUUID().equals(entry.getUserUUID())) continue;
                    if (!next.getTargetUUID().equals(entry.getTargetUUID())) continue;
                } catch (SQLException | BusyException ignored) {
                    //Unlikely / N/A
                    continue;
                }
                if (!next.getWorld().equals(entry.getWorld())) continue;
                if (next.getDistance(entry) > 3) continue;
                next.add(entry);
                return;
            }
            pickups.add(entry);
        }
    }

    private final Set<Consumer<DbEntry>> listeners = new HashSet<>();

    public void addRemoveEntryListener(Consumer<DbEntry> consumer, boolean add) {
        synchronized (listeners) {
            if (add) listeners.add(consumer);
            else listeners.remove(consumer);
        }
    }

}
