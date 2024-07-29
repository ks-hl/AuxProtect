package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.exceptions.BusyException;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpigotDatabaseRunnable extends DatabaseRunnable {
    private final ConcurrentLinkedQueue<JobsEntry> jobsentries = new ConcurrentLinkedQueue<>();
    private static final long jobsCacheTime = 10000;
    private final ConcurrentLinkedQueue<PickupEntry> pickups = new ConcurrentLinkedQueue<>();
    private static final long pickupCacheTime = 1500;

    public SpigotDatabaseRunnable(@Nonnull IAuxProtect plugin, @Nonnull SQLManager sqlManager) {
        super(plugin, sqlManager);
    }

    @Override
    protected void checkCache(boolean force) {
        synchronized (jobsentries) {
            Iterator<JobsEntry> itr = jobsentries.iterator();
            while (itr.hasNext()) {
                JobsEntry next = itr.next();
                if (force || next.getTime() < System.currentTimeMillis() - jobsCacheTime) {
                    EntryAction.JOBS.getTable().queue.add(next);
                    itr.remove();
                }
            }
        }
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

    @Override
    public void add(DbEntry entry) {
        if (!entry.getAction().isEnabled()) {
            return;
        }
        if (entry instanceof PickupEntry) {
            this.addPickup((PickupEntry) entry);
            return;
        }
        if (entry instanceof JobsEntry) {
            this.addJobs((JobsEntry) entry);
            return;
        }
        super.add(entry);
    }

    private void addJobs(JobsEntry entry) {
        synchronized (jobsentries) {
            for (JobsEntry next : jobsentries) {
                if (next.getTime() < System.currentTimeMillis() - jobsCacheTime) continue;
                if (next.add(entry)) return;
            }
            jobsentries.add(entry);
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
}
