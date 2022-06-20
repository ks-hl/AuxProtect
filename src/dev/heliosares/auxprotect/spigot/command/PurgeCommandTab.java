package dev.heliosares.auxprotect.spigot.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class PurgeCommandTab implements TabCompleter {
	// private final AuxProtect plugin;

	public PurgeCommandTab(AuxProtectSpigot plugin) {
		// this.plugin = plugin;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		List<String> possible = new ArrayList<>();
		String currentArg = args[args.length - 1];

		if (args.length == 2) {
			for (Table table : Table.values()) {
				possible.add(table.toString());
			}
			possible.add("all");
		}
		if (args.length == 3) {
			possible.add("<time>");
		}

		List<String> output = new ArrayList<>();
		StringUtil.copyPartialMatches(currentArg, possible, output);
		return output;
	}
}
