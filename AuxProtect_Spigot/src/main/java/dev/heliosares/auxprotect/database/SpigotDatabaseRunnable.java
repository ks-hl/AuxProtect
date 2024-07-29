package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpigotDatabaseRunnable extends DatabaseRunnable {
    private final ConcurrentLinkedQueue<JobsEntry> jobsentries = new ConcurrentLinkedQueue<>();
    private static final long jobsCacheTime = 10000;
    public SpigotDatabaseRunnable(@Nonnull IAuxProtect plugin, @Nonnull SQLManager sqlManager) {
        super(plugin, sqlManager);
    }

    @Override
    public void add(DbEntry entry) {
        if (entry instanceof JobsEntry) {
            this.addJobs((JobsEntry) entry);
            return;
        }
        super.add(entry);
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
}
