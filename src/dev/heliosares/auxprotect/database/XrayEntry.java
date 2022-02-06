package dev.heliosares.auxprotect.database;

public class XrayEntry extends DbEntry {

	public XrayEntry(long time, String userUuid, EntryAction action, boolean state, String world, int x, int y, int z,
			String target, String data) {
		super(time, userUuid, action, state, world, x, y, z, target, data);
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
