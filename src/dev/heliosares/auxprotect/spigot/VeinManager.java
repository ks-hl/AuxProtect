package dev.heliosares.auxprotect.spigot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import dev.heliosares.auxprotect.database.XrayEntry;

public class VeinManager {
	private ArrayList<XrayEntry> entries = new ArrayList<>();
	private ArrayList<XrayEntry> ignoredentries = new ArrayList<>();
	private HashMap<UUID, ArrayList<Long>> skipped = new HashMap<>();

	/**
	 * @returns true if already part of a vein
	 */
	public boolean add(XrayEntry entry) {
		synchronized (entries) {
			for (XrayEntry other : entries) {
				if (other.add(entry)) {
					return true;
				}
			}
			for (XrayEntry other : ignoredentries) {
				if (other.add(entry)) {
					return true;
				}
			}
			if (entry.getRating() == -2) {
				ignoredentries.add(entry);
				return false;
			}
			entries.add(entry);
			entries.sort((o1, o2) -> o1.getUser().compareTo(o2.getUser()));
			entries.sort((o1, o2) -> o1.world.compareTo(o2.world));
		}
		return false;
	}

	public XrayEntry next(UUID uuid) {
		XrayEntry current = current(uuid);
		if (current != null) {
			remove(current);
		}
		synchronized (entries) {
			ArrayList<Long> skipped = this.skipped.get(uuid);
			for (XrayEntry entry : entries) {
				if (skipped != null && skipped.contains(entry.getTime())) {
					continue;
				}
				if (entry.viewer != null) {
					if (System.currentTimeMillis() - entry.viewingStarted < 10 * 60 * 1000L) {
						continue;
					}
				}
				entry.viewer = uuid;
				entry.viewingStarted = System.currentTimeMillis();
				return entry;
			}

			this.skipped.remove(uuid);
		}
		return null;
	}

	public boolean skip(UUID uuid, long time) {
		synchronized (entries) {
			ArrayList<Long> skipped = this.skipped.get(uuid);
			if (skipped == null) {
				skipped = new ArrayList<>();
			}
			skipped.add(time);
			this.skipped.put(uuid, skipped);
			for (XrayEntry entry : entries) {
				if (entry.getTime() == time) {
					release(entry);
					return true;
				}
			}
		}
		return false;
	}

	public void remove(XrayEntry entry) {
		synchronized (entries) {
			entries.remove(entry);
		}
	}

	public XrayEntry current(UUID uuid) {
		XrayEntry current = null;
		for (XrayEntry entry : entries) {
			if (uuid.equals(entry.viewer)) {
				if (current == null) {
					current = entry;
					continue;
				}
				release(current);
			}
		}
		return current;
	}

	public void release(XrayEntry entry) {
		entry.viewer = null;
		entry.viewingStarted = 0;
	}

	public int size() {
		return entries.size();
	}

	public static String getSeverityDescription(int severity) {
		switch (severity) {// TODO lang
		case -2:
			return "ignored";
		case -1:
			return "unrated";
		case 0:
			return "no concern";
		case 1:
			return "slightly suspicious";
		case 2:
			return "suspicious, not certain";
		case 3:
			return "almost certain or entirely certain";
		default:
			return "unknown severity";
		}
	}

	public static String getSeverityColor(int severity) {
		switch (severity) {
		case -2:
		case -1:
			return "§5";
		case 0:
			return "§a";
		case 1:
			return "§e";
		case 2:
			return "§c";
		case 3:
			return "§4";
		default:
			return "";
		}
	}
}
