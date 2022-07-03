package dev.heliosares.auxprotect.database;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class XrayEntry extends DbEntry {

	private short rating;
	public Player viewer;
	public long viewingStarted;

	public XrayEntry(String user, Location location, String block) {
		super(user, EntryAction.VEIN, false, location, block, "");
		this.rating = -1;
	}

	public XrayEntry(long time, int uid, String world, int x, int y, int z, int target_id, short rating, String data) {
		super(time, uid, EntryAction.VEIN, false, world, x, y, z, 0, 0, null, target_id, data);
		this.rating = rating;
	}

	private ArrayList<XrayEntry> children = new ArrayList<>();

	public boolean add(XrayEntry entry) {
		return this.add(entry, new ArrayList<>());
	}

	private boolean add(XrayEntry other, ArrayList<XrayEntry> visited) {
		if (!visited.add(this)) {
			return false;
		}

		if (Math.abs(other.getTime() - this.getTime()) < 3600000L && Math.abs(other.x - this.x) <= 2
				&& Math.abs(other.y - this.y) <= 2 && Math.abs(other.z - this.z) <= 2 && other.world.equals(this.world)
				&& this.getTarget().equals(other.getTarget())) {
			children.add(other);
			return true;
		}
		for (XrayEntry child : children) {
			if (child.add(other, visited)) {
				return true;
			}
		}
		return false;
	}

	public short getRating() {
		return rating;
	}

	public void setRating(short rating) {
		this.rating = rating;
	}
}
