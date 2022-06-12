package dev.heliosares.auxprotect.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.utils.MyPermission;

public class APCommandTab implements TabCompleter {
	private final AuxProtect plugin;
	private final LookupCommandTab lookupCommandTab;
	private final PurgeCommandTab purgeCommandTab;
	private final PlaytimeCommandTab playtimeCommandTab;
	private final MoneyCommandTab moneyCommandTab;

	public APCommandTab(AuxProtect plugin) {
		this.plugin = plugin;
		this.lookupCommandTab = new LookupCommandTab(plugin);
		this.purgeCommandTab = new PurgeCommandTab(plugin);
		this.playtimeCommandTab = new PlaytimeCommandTab(plugin);
		this.moneyCommandTab = new MoneyCommandTab(plugin);
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		List<String> possible = new ArrayList<>();
		String currentArg = args[args.length - 1];

		if (args.length == 1) {
			if (MyPermission.LOOKUP.hasPermission(sender)) {
				possible.add("lookup");
				if (MyPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
					possible.add("playtime");
				}
				if (MyPermission.LOOKUP_ACTIVITY.hasPermission(sender) && plugin.config.isPrivate()) {
					possible.add("activity");
				}
				if (MyPermission.LOOKUP_RETENTION.hasPermission(sender) && plugin.config.isPrivate()) {
					possible.add("retention");
				}
				if (MyPermission.LOOKUP_MONEY.hasPermission(sender)) {
					possible.add("money");
				}
			}
			if (MyPermission.ADMIN.hasPermission(sender)) {
				possible.add("debug");
				possible.add("stats");
			}
			if (MyPermission.HELP.hasPermission(sender)) {
				possible.add("help");
			}
			if (MyPermission.PURGE.hasPermission(sender)) {
				possible.add("purge");
			}
			possible.add("info");
		}
		if (args.length >= 2) {
			if ((args[0].equalsIgnoreCase("lookup") || args[0].equalsIgnoreCase("l"))
					&& MyPermission.LOOKUP.hasPermission(sender)) {
				possible.addAll(lookupCommandTab.onTabComplete(sender, cmd, label, args));
			} else if (args[0].equalsIgnoreCase("money") && MyPermission.LOOKUP_MONEY.hasPermission(sender)) {
				possible.addAll(moneyCommandTab.onTabComplete(sender, cmd, label, args));
			} else if ((args[0].equalsIgnoreCase("playtime") || args[0].equalsIgnoreCase("pt"))
					&& MyPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
				possible.addAll(playtimeCommandTab.onTabComplete(sender, cmd, label, args));
			} else if (args[0].equalsIgnoreCase("activity") && MyPermission.LOOKUP_ACTIVITY.hasPermission(sender)) {
				possible.addAll(playtimeCommandTab.onTabComplete(sender, cmd, label, args));
			} else if ((args[0].equalsIgnoreCase("purge")) && MyPermission.PURGE.hasPermission(sender)) {
				possible.addAll(purgeCommandTab.onTabComplete(sender, cmd, label, args));
			} else if ((args[0].equalsIgnoreCase("help")) && MyPermission.HELP.hasPermission(sender)) {
				possible.add("lookup");
				possible.add("purge");
			}
		}

		List<String> output = new ArrayList<>();
		StringUtil.copyPartialMatches(currentArg, possible, output);
		return output;
	}
}
