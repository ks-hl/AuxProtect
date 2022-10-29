package dev.heliosares.auxprotect.database;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.commands.WatchCommand;
import dev.heliosares.auxprotect.spigot.listeners.JobsListener.JobsEntry;

public class DatabaseRunnable implements Runnable {
	private final SQLManager sqlManager;
	private final IAuxProtect plugin;
	private static HashMap<Table, Long> lastTimes = new HashMap<>();
	private long running = 0;
	private long lastWarn = 0;
	private ConcurrentLinkedQueue<PickupEntry> pickups = new ConcurrentLinkedQueue<>();
	private static final long pickupCacheTime = 1500;
	private ConcurrentLinkedQueue<JobsEntry> jobsentries = new ConcurrentLinkedQueue<>();
	private static final long jobsCacheTime = 10000;

	public DatabaseRunnable(IAuxProtect plugin, SQLManager sqlManager) {
		this.sqlManager = sqlManager;
		this.plugin = plugin;
	}

	public void add(DbEntry entry) {
		WatchCommand.notify(entry);
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
		Table table = entry.getAction().getTable();
		if (table == null || table.queue == null) {
			return;
		}
		table.queue.add(entry);
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

	public int queueSize() {
		return Arrays.asList(Table.values()).stream().filter(t -> t.queue != null).map(t -> t.queue.size())
				.reduce((a, b) -> a + b).get();
	}

	@Override
	public void run() {
		try {
			if (!sqlManager.isConnected()) {
				return;
			}
			// TODO was this necessary?
			if (running > 0) {
				if (System.currentTimeMillis() - running > 20000) {
					if (System.currentTimeMillis() - lastWarn > 60000) {
						lastWarn = System.currentTimeMillis();
						plugin.warning("Overlapping logging windows > 20 seconds by "
								+ (System.currentTimeMillis() - running) + " ms.");
					}
				}
				plugin.debug("Overlapping logging windows by " + (System.currentTimeMillis() - running) + " ms.", 1);
				if (System.currentTimeMillis() - running < 300000) {
					return;
				} else {
					plugin.warning("Overlapping logging windows by 5 minutes, continuing.");
				}
			}
			running = System.currentTimeMillis();

			checkCache();

			Arrays.asList(Table.values()).forEach(t -> {
				try {
					sqlManager.put(t);
				} catch (SQLException e) {
					plugin.print(e);
				}
			});
			sqlManager.cleanup();
		} catch (Throwable e) {
			plugin.print(e);
		} finally {
			running = 0;
		}
	}

	private void checkCache() {
		synchronized (pickups) {
			Iterator<PickupEntry> itr = pickups.iterator();
			while (itr.hasNext()) {
				PickupEntry next = itr.next();
				if (next.getTime() < System.currentTimeMillis() - pickupCacheTime) {
					Table.AUXPROTECT_INVENTORY.queue.add(next);
					pickups.remove(next);
				}
			}
		}
		synchronized (jobsentries) {
			Iterator<JobsEntry> itr = jobsentries.iterator();
			while (itr.hasNext()) {
				JobsEntry next = itr.next();
				if (next.getTime() < System.currentTimeMillis() - jobsCacheTime) {
					EntryAction.JOBS.getTable().queue.add(next);
					jobsentries.remove(next);
				}
			}
		}
	}

	private void addPickup(PickupEntry entry) {
		synchronized (pickups) {
			Iterator<PickupEntry> itr = pickups.iterator();
			while (itr.hasNext()) {
				PickupEntry next = itr.next();
				if (next.getTime() < System.currentTimeMillis() - pickupCacheTime) {
					continue;
				}
				if (next.getAction() != entry.getAction()) {
					continue;
				}
				if (next.getUserUUID().equals(entry.getUserUUID())) {
					if (next.getTargetUUID().equals(entry.getTargetUUID())) {
						if (next.world.equals(entry.world)) {
							if (next.getDistance(entry) <= 3) {
								next.add(entry);
								return;
							}
						}
					}
				}
			}
			pickups.add(entry);
		}
	}

	private void addJobs(JobsEntry entry) {
		synchronized (jobsentries) {
			Iterator<JobsEntry> itr = jobsentries.iterator();
			while (itr.hasNext()) {
				JobsEntry next = itr.next();
				if (next.getTime() < System.currentTimeMillis() - jobsCacheTime) {
					continue;
				}
				if (next.add(entry)) {
					return;
				}
			}
			jobsentries.add(entry);
		}
	}
}
