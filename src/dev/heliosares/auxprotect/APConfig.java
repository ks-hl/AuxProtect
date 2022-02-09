package dev.heliosares.auxprotect;

import org.bukkit.configuration.file.FileConfiguration;

import dev.heliosares.auxprotect.database.EntryAction;

public class APConfig {

	private final FileConfiguration config;
	private boolean privateRelease = false;
	public boolean inventoryOnWorldChange;

	public boolean isPrivate() {
		return privateRelease;
	}

	public APConfig(FileConfiguration config) {
		this.config = config;
	}

	public void load() {
		privateRelease = config.getBoolean("private");
		inventoryOnWorldChange = config.getBoolean("Actions.inventory.WorldChange", false);
		for (EntryAction action : EntryAction.values()) {
			boolean enabled = config.getBoolean("Actions." + action.toString().toLowerCase() + ".Enabled", false);
			action.setEnabled(enabled);
		}
	}
}
