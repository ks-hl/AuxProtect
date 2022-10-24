package dev.heliosares.auxprotect.core;

import org.bukkit.configuration.file.FileConfiguration;

import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.utils.KeyUtil;
import net.md_5.bungee.config.Configuration;

public class APConfig {

	private boolean inventoryOnWorldChange;
	private boolean checkforupdates;
	private long posInterval;
	private long inventoryInterval;
	private long inventoryDiffInterval;
	private long moneyInterval;
	private boolean overrideCommands;
	private boolean skipV6Migration;
	private KeyUtil key;

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

	public long getInventoryDiffInterval() {
		return inventoryDiffInterval;
	}

	public long getMoneyInterval() {
		return moneyInterval;
	}

	public boolean isPrivate() {
		if (key == null)
			return false;
		return key.isPrivate();
	}

	public boolean isDonor() {
		if (key == null)
			return false;
		return key.isValid();
	}

	public boolean isOverrideCommands() {
		return overrideCommands;
	}

	public APConfig(IAuxProtect plugin, FileConfiguration config) {
		String keystr = config.getString("donorkey");
		if (keystr != null) {
			key = new KeyUtil(keystr);
		}
		checkforupdates = config.getBoolean("checkforupdates", true);
		inventoryOnWorldChange = config.getBoolean("Actions.inventory.WorldChange", false);
		posInterval = config.getLong("Actions.pos.Interval", 10000);
		inventoryInterval = config.getLong("Actions.inventory.Interval", 3600000);
		inventoryDiffInterval = config.getLong("Actions.inventory.Diff-Interval", 1000);
		moneyInterval = config.getLong("Actions.money.Interval", 600000);
		skipV6Migration = config.getBoolean("skipv6migration");
		for (EntryAction action : EntryAction.values()) {
			if (!action.exists(plugin)) {
				action.setEnabled(false);
				continue;
			}
			if (action == EntryAction.USERNAME) {
				action.setEnabled(true);
				continue;
			}
			boolean enabled = config.getBoolean("Actions." + action.toString().toLowerCase() + ".Enabled", true);
			boolean priority = config.getBoolean("Actions." + action.toString().toLowerCase() + ".LowestPriority",
					false);
			action.setEnabled(enabled);
			action.setLowestpriority(priority);
			config.set("Actions." + action.toString().toLowerCase() + ".Enabled", enabled);
		}
		overrideCommands = config.getBoolean("OverrideCommands");
	}

	public APConfig(IAuxProtect plugin, Configuration config) {
		String keystr = config.getString("donorkey");
		if (keystr != null) {
			key = new KeyUtil(keystr);
		}
		checkforupdates = config.getBoolean("checkforupdates", true);
		for (EntryAction action : EntryAction.values()) {
			if (!action.exists(plugin)) {
				action.setEnabled(false);
				continue;
			}
			if (action == EntryAction.USERNAME) {
				action.setEnabled(true);
				continue;
			}
			boolean enabled = config.getBoolean("Actions." + action.toString().toLowerCase() + ".Enabled", true);
			config.set("Actions." + action.toString().toLowerCase() + ".Enabled", enabled);
			action.setEnabled(enabled);
		}
	}

	public boolean doSkipV6Migration() {
		return skipV6Migration;
	}
}
