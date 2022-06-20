package dev.heliosares.auxprotect.spigot.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class MoneyCommand implements CommandExecutor {

	private AuxProtectSpigot plugin;

	public MoneyCommand(AuxProtectSpigot plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length != 2 && args.length != 3) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return true;
		}
		String time = "2w";
		if (args.length == 3) {
			time = args[2];
		}
		Bukkit.dispatchCommand(sender, String.format("ap lookup #money user:%s time:%s action:money", args[1], time));
		return true;
	}
}
