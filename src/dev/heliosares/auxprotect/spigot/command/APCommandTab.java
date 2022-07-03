package dev.heliosares.auxprotect.spigot.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class APCommandTab implements TabCompleter {
	private final AuxProtectSpigot plugin;
	private final LookupCommandTab lookupCommandTab;
	private final PurgeCommandTab purgeCommandTab;
	private final PlaytimeCommandTab playtimeCommandTab;
	private final MoneyCommandTab moneyCommandTab;

	public APCommandTab(AuxProtectSpigot plugin) {
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
			if (APPermission.LOOKUP.hasPermission(sender)) {
				possible.add("lookup");
				if (APPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
					possible.add("playtime");
				}
				if (APPermission.LOOKUP_ACTIVITY.hasPermission(sender) && plugin.getAPConfig().isPrivate()) {
					possible.add("activity");
				}
				if (APPermission.LOOKUP_RETENTION.hasPermission(sender) && plugin.getAPConfig().isPrivate()) {
					possible.add("retention");
				}
				if (APPermission.LOOKUP_MONEY.hasPermission(sender)) {
					possible.add("money");
				}
			}
			if (APPermission.ADMIN.hasPermission(sender)) {
				possible.add("debug");
				possible.add("stats");
			}
			if (APPermission.HELP.hasPermission(sender)) {
				possible.add("help");
			}
			if (APPermission.PURGE.hasPermission(sender)) {
				possible.add("purge");
			}
			possible.add("info");
		}
		if (args.length >= 2) {
			if ((args[0].equalsIgnoreCase("lookup") || args[0].equalsIgnoreCase("l"))
					&& APPermission.LOOKUP.hasPermission(sender)) {
				possible.addAll(lookupCommandTab.onTabComplete(sender, cmd, label, args));
			} else if (args[0].equalsIgnoreCase("money") && APPermission.LOOKUP_MONEY.hasPermission(sender)) {
				possible.addAll(moneyCommandTab.onTabComplete(sender, cmd, label, args));
			} else if ((args[0].equalsIgnoreCase("playtime") || args[0].equalsIgnoreCase("pt"))
					&& APPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
				possible.addAll(playtimeCommandTab.onTabComplete(sender, cmd, label, args));
			} else if (args[0].equalsIgnoreCase("activity") && APPermission.LOOKUP_ACTIVITY.hasPermission(sender)) {
				possible.addAll(playtimeCommandTab.onTabComplete(sender, cmd, label, args));
			} else if ((args[0].equalsIgnoreCase("purge")) && APPermission.PURGE.hasPermission(sender)) {
				possible.addAll(purgeCommandTab.onTabComplete(sender, cmd, label, args));
			} else if ((args[0].equalsIgnoreCase("help")) && APPermission.HELP.hasPermission(sender)) {
				possible.add("lookup");
				possible.add("purge");
			}
		}

		List<String> output = new ArrayList<>();
		StringUtil.copyPartialMatches(currentArg, possible, output);
		return output;
	}
}
