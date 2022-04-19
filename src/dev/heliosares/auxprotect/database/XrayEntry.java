package dev.heliosares.auxprotect.database;

public class XrayEntry extends DbEntry {

	public XrayEntry(long time, String userLabel, EntryAction action, boolean state, String world, int x, int y, int z,
			String target, String data) {
		super(time, 0, action, state, world, x, y, z, 0, 0, target, -1, data);
		super.userLabel = userLabel;
		parent = null;
	}

	private XrayEntry parent;

	public void setParent(XrayEntry parent) {
		this.parent = parent;
	}

	public XrayEntry getParent() {
		if (parent == null)
			return this;
		return parent;
	}

	public boolean hasParent() {
		return parent != null;
	}

}
