package dev.heliosares.auxprotect.spigot;

import java.util.ArrayList;
import java.util.HashMap;
import org.bukkit.entity.Player;

import dev.heliosares.auxprotect.database.XrayEntry;

public class VeinManager {
	private ArrayList<XrayEntry> entries = new ArrayList<>();
	private HashMap<Player, ArrayList<Long>> skipped = new HashMap<>();

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
			entries.add(entry);
			entries.sort((o1, o2) -> o1.world.compareTo(o2.world));
			entries.sort((o1, o2) -> o1.getUser().compareTo(o2.getUser()));
		}
		return false;
	}

	public XrayEntry next(Player player) {
		XrayEntry current = current(player);
		if (current != null) {
			AuxProtectSpigot.getInstance().warning("remove");
			remove(current);
		}
		synchronized (entries) {
			ArrayList<Long> skipped = this.skipped.get(player);
			for (XrayEntry entry : entries) {
				if (skipped != null && skipped.contains(entry.getTime())) {
					AuxProtectSpigot.getInstance().warning("skip");
					continue;
				}
				if (entry.viewer != null) {
					if (System.currentTimeMillis() - entry.viewingStarted < 10 * 60 * 1000L) {
						continue;
					}
				}
				entry.viewer = player;
				entry.viewingStarted = System.currentTimeMillis();
				return entry;
			}

			this.skipped.remove(player);
		}
		return null;
	}

	public boolean skip(Player player, long time) {
		synchronized (entries) {
			ArrayList<Long> skipped = this.skipped.get(player);
			if (skipped == null) {
				skipped = new ArrayList<>();
			}
			skipped.add(time);
			this.skipped.put(player, skipped);
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

	public XrayEntry current(Player player) {
		XrayEntry current = null;
		for (XrayEntry entry : entries) {
			if (player.equals(entry.viewer)) {
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
		switch (severity) {// TODO lang
		case -1:
			return "§7";
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
