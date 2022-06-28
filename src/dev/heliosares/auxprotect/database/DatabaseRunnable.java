package dev.heliosares.auxprotect.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;

import dev.heliosares.auxprotect.core.IAuxProtect;

public class DatabaseRunnable implements Runnable {
	private ConcurrentLinkedQueue<DbEntry> queue;
	private final SQLManager sqlManager;
	private final IAuxProtect plugin;

	public DatabaseRunnable(IAuxProtect plugin, SQLManager sqlManager) {
		queue = new ConcurrentLinkedQueue<>();
		this.sqlManager = sqlManager;
		this.plugin = plugin;
	}

	public void add(DbEntry entry) {
		if (!entry.getAction().isEnabled()) {
			return;
		}
		if (entry instanceof PickupEntry) {
			this.addPickup((PickupEntry) entry);
			return;
		}
		queue.add(entry);
	}

	private static HashMap<Table, Long> lastTimes = new HashMap<>();

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
		return queue.size();
	}

	private long running = 0;
	private long lastWarn = 0;

	private long lastPolled = 0;

	@Override
	public void run() {
		try {
			if (!sqlManager.isConnected()) {
				return;
			}
			if (running > 0) {
				if (System.currentTimeMillis() - running > 20000) {
					if (System.currentTimeMillis() - lastWarn > 60000) {
						lastWarn = System.currentTimeMillis();
						plugin.warning("Overlapping logging windows > 20 seconds by "
								+ (System.currentTimeMillis() - running) + " ms.");
					}
				}
				plugin.debug("Overlapping logging windows by " + (System.currentTimeMillis() - running) + " ms.", 1);
				if (plugin.getSqlManager().holdingConnectionSince > 0)
					plugin.debug("Held by " + plugin.getSqlManager().holdingConnection + " for "
							+ (System.currentTimeMillis() - plugin.getSqlManager().holdingConnectionSince) + " ms.", 1);
				return;
			}
			running = System.currentTimeMillis();

			checkPickupsForNew();

			if (System.currentTimeMillis() - lastPolled > 3000 || queue.size() > 50) {
				DbEntry entry;
				long start = System.nanoTime();

				HashMap<Table, ArrayList<DbEntry>> entriesHash = new HashMap<>();

				lastPolled = System.currentTimeMillis();
				while ((entry = queue.poll()) != null) {
					if (plugin.getDebug() >= 2) {
						String debug = String.format("§9%s §f%s§7(%d) §9%s §7", entry.getUser(),
								entry.getAction().getText(plugin, entry.getState()),
								entry.getAction().getId(entry.getState()), entry.getTarget());
						if (entry.getData() != null && entry.getData().length() > 0) {
							String data = entry.getData();
							if (data.length() > 64) {
								data = data.substring(0, 64) + "...";
							}
							debug += "(" + data + ")";

						}
						plugin.debug(debug, 2);
					}

					Table table = entry.getAction().getTable();

					if (table == null) {
						continue;
					}

					ArrayList<DbEntry> entriesList = entriesHash.get(table);
					if (entriesList == null) {
						entriesList = new ArrayList<>();
					}
					entriesList.add(entry);
					entriesHash.put(table, entriesList);
				}

				for (Entry<Table, ArrayList<DbEntry>> entryPair : entriesHash.entrySet()) {
					try {
						int size = entryPair.getValue().size();
						if (size == 0) {
							continue;
						}
						sqlManager.put(entryPair.getKey(), entryPair.getValue());
						plugin.debug(debugLogStatement(start, size, entryPair.getKey()), 1);
						start = System.nanoTime();
					} catch (Exception e) {
						plugin.print(e);
					}
				}
			}
		} catch (Exception e) {
			plugin.print(e);
		}
		sqlManager.cleanup();

		running = 0;
	}

	private String debugLogStatement(long start, int count, Table table) {
		double elapsed = (System.nanoTime() - start) / 1000000.0;
		return table + ": Logged " + count + " entrie(s) in " + (Math.round(elapsed * 10.0) / 10.0) + "ms. ("
				+ (Math.round(elapsed / count * 10.0) / 10.0) + "ms each)";
	}

	private ConcurrentLinkedQueue<PickupEntry> pickups = new ConcurrentLinkedQueue<>();

	private void checkPickupsForNew() {
		synchronized (pickups) {
			Iterator<PickupEntry> itr = pickups.iterator();
			while (itr.hasNext()) {
				PickupEntry next = itr.next();
				if (next.getTime() < System.currentTimeMillis() - 1500) {
					queue.add(next);
					pickups.remove(next);
				}
			}
		}
	}

	private void addPickup(PickupEntry entry) {
		if (!entry.getAction().isEnabled()) {
			return;
		}

		synchronized (pickups) {
			Iterator<PickupEntry> itr = pickups.iterator();
			while (itr.hasNext()) {
				PickupEntry next = itr.next();
				if (next.getTime() < System.currentTimeMillis() - 1500) {
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
}
