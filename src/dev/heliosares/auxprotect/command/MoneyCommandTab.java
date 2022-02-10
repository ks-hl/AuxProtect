package dev.heliosares.auxprotect.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import dev.heliosares.auxprotect.AuxProtect;

public class MoneyCommandTab implements TabCompleter {
	// private final AuxProtect plugin;

	public MoneyCommandTab(AuxProtect plugin) {
		// this.plugin = plugin;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		List<String> possible = new ArrayList<>();
		String currentArg = args[args.length - 1];

		for (Player player : Bukkit.getOnlinePlayers()) {
			possible.add(player.getName());
		}

		List<String> output = new ArrayList<>();
		StringUtil.copyPartialMatches(currentArg, possible, output);
		return output;
	}
}
