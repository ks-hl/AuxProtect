package dev.heliosares.auxprotect.command;

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

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLiteManager;
import dev.heliosares.auxprotect.database.SQLiteManager.TABLE;
import dev.heliosares.auxprotect.utils.MyPermission;

public class LookupCommandTab implements TabCompleter {
	// private final AuxProtect plugin;

	public LookupCommandTab(AuxProtect plugin) {
		// this.plugin = plugin;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		List<String> possible = new ArrayList<>();
		String currentArg = args[args.length - 1];

		possible.add("radius:");
		possible.add("time:");
		possible.add("target:");
		possible.add("action:");
		possible.add("world:");
		possible.add("user:");
		if (currentArg.startsWith("action:") || currentArg.startsWith("a:")) {
			String action = currentArg.split(":")[0] + ":";
			for (EntryAction eaction : EntryAction.values()) {
				if (eaction.isSpigot() && eaction.isEnabled()) {
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
			String split[] = currentArg.split(":");
			String user = split[0] + ":";
			for (Player player : Bukkit.getOnlinePlayers()) {
				possible.add(user + player.getName());
			}
			if (split.length > 1 && split[1].startsWith("#")) {
				for (EntityType et : EntityType.values()) {
					possible.add(user + "#" + et.toString().toLowerCase());
				}
			}
		}
		if (currentArg.startsWith("target:")) {
			for (Material material : Material.values()) {
				possible.add("target:" + material.toString().toLowerCase());
			}
		}
		if (MyPermission.ADMIN.hasPermission(sender)) {
			possible.add("db:");
			if (currentArg.startsWith("db:")) {
				for (TABLE table : SQLiteManager.TABLE.values()) {
					possible.add("db:" + table.toString());
				}
			}
		}
		if (currentArg.startsWith("time:") || currentArg.startsWith("t:")) {
			if (currentArg.matches("t(ime)?:\\d+")) {
				possible.add(currentArg + "s");
				possible.add(currentArg + "m");
				possible.add(currentArg + "h");
				possible.add(currentArg + "d");
				possible.add(currentArg + "w");
			}
		}
		if (currentArg.startsWith("world:")) {
			for (World world : Bukkit.getWorlds()) {
				possible.add("world:" + world.getName());
			}
		}
		if (currentArg.startsWith("b"))
			possible.add("before:");
		if (currentArg.startsWith("a"))
			possible.add("after:");

		for (int i = 1; i < args.length - 1; i++) {
			String arg = args[i];
			if (!arg.contains(":"))
				continue;
			arg = arg.substring(0, arg.indexOf(":") + 1);
			possible.remove(arg);
		}

		if (currentArg.startsWith("#")) {
			if (MyPermission.LOOKUP_XRAY.hasPermission(sender)) {
				possible.add("#xray");
			}
			if (MyPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
				possible.add("#pt");
			}
			if (MyPermission.LOOKUP_MONEY.hasPermission(sender)) {
				possible.add("#money");
			}
			possible.add("#bw");
			possible.add("#count");
		}

		List<String> output = new ArrayList<>();
		StringUtil.copyPartialMatches(currentArg, possible, output);
		return output;
	}
}
