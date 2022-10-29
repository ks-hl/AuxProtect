package dev.heliosares.auxprotect.core;

import java.io.File;
import java.util.Scanner;

import dev.heliosares.auxprotect.adapters.ConfigAdapter;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.utils.KeyUtil;

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
	private ConfigAdapter config;

	public void load(IAuxProtect plugin, ConfigAdapter config) {
		loadKey(plugin);
		this.config = config;
		checkforupdates = config.getBoolean("checkforupdates", true);
		if (config.getPlatform() == PlatformType.SPIGOT) {
			skipV6Migration = config.getBoolean("skipv6migration");
			overrideCommands = config.getBoolean("OverrideCommands");
			inventoryOnWorldChange = config.getBoolean("Actions.inventory.WorldChange", false);
			posInterval = config.getLong("Actions.pos.Interval", 10000);
			inventoryInterval = config.getLong("Actions.inventory.Interval", 3600000);
			inventoryDiffInterval = config.getLong("Actions.inventory.Diff-Interval", 1000);
			moneyInterval = config.getLong("Actions.money.Interval", 600000);
		}
		for (EntryAction action : EntryAction.values()) {
			if (!action.exists()) {
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
	}

	private void loadKey(IAuxProtect plugin) {
		String key = null;
		try (Scanner sc = new Scanner(new File(plugin.getRootDirectory(), "donorkey.txt"))) {
			key = sc.nextLine();
		} catch (Exception e) {
		}
		if (key != null) {
			this.key = new KeyUtil(key);
		}

		if (this.key != null) {
			if (this.key.isMalformed()) {
				plugin.info("Invalid donor key");
				return;
			}
			if (isPrivate()) {
				plugin.info("Private key!");
				return;
			}
			if (isDonor()) {
				plugin.info("Valid donor key!");
				return;
			}
		}
		plugin.info("No donor key");
	}

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

	public String getKeyHolder() {
		return key.getKeyHolder();
	}

	public boolean isOverrideCommands() {
		return overrideCommands;
	}

	public boolean doSkipV6Migration() {
		return skipV6Migration;
	}

	public ConfigAdapter getConfig() {
		return config;
	}
}
