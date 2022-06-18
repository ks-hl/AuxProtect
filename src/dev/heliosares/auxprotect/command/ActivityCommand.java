package dev.heliosares.auxprotect.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import dev.heliosares.auxprotect.AuxProtect;

public class ActivityCommand implements CommandExecutor {

	private AuxProtect plugin;

	public ActivityCommand(AuxProtect plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length != 2 && args.length != 3) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return true;
		}
		String cmd = String.format("ap lookup #activity user:%s action:activity time:", args[1]);
		if (args.length > 2) {
			cmd += args[2];
		} else {
			cmd += "2h";
		}
		Bukkit.dispatchCommand(sender, cmd);
		return true;
	}
}
