package dev.heliosares.auxprotect;

import org.bukkit.configuration.file.FileConfiguration;

import dev.heliosares.auxprotect.database.EntryAction;

public class APConfig {

	private final FileConfiguration config;
	private boolean privateRelease = false;
	public boolean inventoryOnWorldChange;
	public boolean checkforupdates;
	protected long posInterval;
	protected long inventoryInterval;
	protected long moneyInterval;

	public boolean isPrivate() {
		return privateRelease;
	}

	public APConfig(FileConfiguration config) {
		this.config = config;
	}

	public void load() {
		privateRelease = config.getBoolean("private");
		checkforupdates = config.getBoolean("checkforupdates", true);
		inventoryOnWorldChange = config.getBoolean("Actions.inventory.WorldChange", false);
		posInterval = config.getLong("Actions.pos.Interval", 10000);
		inventoryInterval = config.getLong("Actions.inventory.Interval", 3600000);
		moneyInterval = config.getLong("Actions.money.Interval", 600000);
		for (EntryAction action : EntryAction.values()) {
			boolean enabled = config.getBoolean("Actions." + action.toString().toLowerCase() + ".Enabled", true);
			action.setEnabled(enabled);
		}
	}
}
