package dev.heliosares.auxprotect.spigot.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class LookupCommandTab implements TabCompleter {
	private final AuxProtectSpigot plugin;

	public LookupCommandTab(AuxProtectSpigot plugin) {
		this.plugin = plugin;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		List<String> possible = new ArrayList<>();
		String currentArg = args[args.length - 1];
		boolean lookup = args[0].equalsIgnoreCase("lookup") || args[0].equalsIgnoreCase("l");
		boolean watch = args[0].equalsIgnoreCase("watch") || args[0].equalsIgnoreCase("w");
		if (lookup && !APPermission.LOOKUP.hasPermission(sender)) {
			return null;
		}
		if (watch && !APPermission.WATCH.hasPermission(sender)) {
			return null;
		}
		if (args.length == 2) {
			if (lookup) {
				possible.add("next");
				possible.add("prev");
				possible.add("first");
				possible.add("last");
			}
			if (watch) {
				possible.add("remove");
				possible.add("clear");
				possible.add("list");
			}
		}

		possible.add("radius:");
		possible.add("time:");
		possible.add("target:");
		possible.add("action:");
		possible.add("world:");
		possible.add("user:");
		possible.add("data:");
		possible.add("before:");
		possible.add("after:");

		if (currentArg.startsWith("action:") || currentArg.startsWith("a:")) {
			String action = currentArg.split(":")[0] + ":";
			for (EntryAction eaction : EntryAction.values()) {
				if (eaction.exists(plugin) && eaction.isEnabled()) {
					String actString = eaction.toString().toLowerCase();
					if (eaction.hasDual) {
						possible.add(action + "+" + actString);
						possible.add(action + "-" + actString);
					}
					possible.add(action + actString);
				}
			}
		}
		if (currentArg.startsWith("user:") || currentArg.startsWith("u:") || currentArg.startsWith("target:")) {
			int cutIndex = 0;
			if (currentArg.contains(",")) {
				cutIndex = currentArg.lastIndexOf(",");
			} else {
				cutIndex = currentArg.indexOf(":");

			}
			String user = currentArg.substring(0, cutIndex + 1);
			for (Player player : Bukkit.getOnlinePlayers()) {
				possible.add(user + player.getName());
			}
			for (String username : plugin.getSqlManager().getCachedUsernames()) {
				possible.add(user + username);
			}
			for (EntityType et : EntityType.values()) {
				possible.add(user + "#" + et.toString().toLowerCase());
			}
			possible.add(user + "#env");
		}
		if (currentArg.startsWith("target:")) {
			for (Material material : Material.values()) {
				possible.add("target:" + material.toString().toLowerCase());
			}
		}
		if (APPermission.ADMIN.hasPermission(sender)) {
			possible.add("db:");
			if (currentArg.startsWith("db:")) {
				for (Table table : Table.values()) {
					possible.add("db:" + table.toString());
				}
			}
		}
		if (currentArg.matches("(t(ime)?|before|after):\\d+m?")) {
			possible.add(currentArg + "ms");
			possible.add(currentArg + "s");
			possible.add(currentArg + "m");
			possible.add(currentArg + "h");
			possible.add(currentArg + "d");
			possible.add(currentArg + "w");
		}
		if (currentArg.startsWith("world:")) {
			for (World world : Bukkit.getWorlds()) {
				possible.add("world:" + world.getName());
			}
		}
		if (currentArg.startsWith("rat")) {
			possible.add("rating:");
			if (currentArg.matches("rating:-?")) {
				for (int i = -2; i <= 3; i++) {
					possible.add("rating:" + i);
				}
			}
		}

		for (int i = 1; i < args.length - 1; i++) {
			String arg = args[i];
			if (!arg.contains(":"))
				continue;
			arg = arg.substring(0, arg.indexOf(":") + 1);
			possible.remove(arg);
		}

		if (currentArg.startsWith("#")) {
			if (APPermission.LOOKUP_XRAY.hasPermission(sender) && plugin.getAPConfig().isPrivate()) {
				possible.add("#xray");
			}
			if (APPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
				possible.add("#pt");
			}
			if (APPermission.LOOKUP_MONEY.hasPermission(sender)) {
				possible.add("#money");
			}
			possible.add("#bw");
			possible.add("#count");
			if (currentArg.startsWith("#cou"))
				possible.add("#count-only");
			if (currentArg.startsWith("#hide"))
				possible.add("#hide-coords");
			if (APPermission.LOOKUP_ACTIVITY.hasPermission(sender) && plugin.getAPConfig().isPrivate()) {
				possible.add("#activity");
			}
			if (APPermission.LOOKUP_RETENTION.hasPermission(sender) && plugin.getAPConfig().isPrivate()) {
				possible.add("#retention");
			}
		}

		List<String> output = new ArrayList<>();
		StringUtil.copyPartialMatches(currentArg, possible, output);
		return output;
	}
}
