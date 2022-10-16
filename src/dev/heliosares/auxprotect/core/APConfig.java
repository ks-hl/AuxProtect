package dev.heliosares.auxprotect.core;

import org.bukkit.configuration.file.FileConfiguration;

import dev.heliosares.auxprotect.database.EntryAction;
import net.md_5.bungee.config.Configuration;

public class APConfig {

	private boolean privateRelease = false;
	private boolean inventoryOnWorldChange;
	private boolean checkforupdates;
	private long posInterval;
	private long inventoryInterval;
	private long moneyInterval;
	private boolean overrideCommands;
	private boolean skipV6Migration;

	public boolean isInventoryOnWorldChange() {
		return inventoryOnWorldChange;
	}

	public boolean shouldCheckForUpdates() {
		return checkforupdates;
	}

	public long getPosInterval() {
		return posInterval;
	}

	public long getInventoryInterval() {
		return inventoryInterval;
	}

	public long getMoneyInterval() {
		return moneyInterval;
	}

	public boolean isPrivate() {
		return privateRelease;
	}

	public boolean isOverrideCommands() {
		return overrideCommands;
	}

	public APConfig(FileConfiguration config) {
		privateRelease = config.getBoolean("private");
		checkforupdates = config.getBoolean("checkforupdates", true);
		inventoryOnWorldChange = config.getBoolean("Actions.inventory.WorldChange", false);
		posInterval = config.getLong("Actions.pos.Interval", 10000);
		inventoryInterval = config.getLong("Actions.inventory.Interval", 3600000);
		moneyInterval = config.getLong("Actions.money.Interval", 600000);
		skipV6Migration = config.getBoolean("skipv6migration");
		for (EntryAction action : EntryAction.values()) {
			boolean enabled = config.getBoolean("Actions." + action.toString().toLowerCase() + ".Enabled", true);
			action.setEnabled(enabled);
		}
		overrideCommands = config.getBoolean("OverrideCommands");
	}

	public APConfig(Configuration config) {
		privateRelease = config.getBoolean("private");
		checkforupdates = config.getBoolean("checkforupdates", true);
		skipV6Migration = config.getBoolean("skipv6migration");
		for (EntryAction action : EntryAction.values()) {
			if (!action.isBungee()) {
				continue;
			}
			boolean enabled = config.getBoolean("Actions." + action.toString().toLowerCase() + ".Enabled", true);
			action.setEnabled(enabled);
		}
	}

	public boolean doSkipV6Migration() {
		return skipV6Migration;
	}
}
