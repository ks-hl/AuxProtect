package dev.heliosares.auxprotect.spigot.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class RetentionCommand implements CommandExecutor {

	private AuxProtectSpigot plugin;

	public RetentionCommand(AuxProtectSpigot plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length != 2) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return true;
		}
		Bukkit.dispatchCommand(sender, String.format("ap lookup #retention time:%s action:username", args[1]));
		return true;
	}
}
