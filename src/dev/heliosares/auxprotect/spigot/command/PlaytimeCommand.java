package dev.heliosares.auxprotect.spigot.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class PlaytimeCommand implements CommandExecutor {

	private AuxProtectSpigot plugin;

	public PlaytimeCommand(AuxProtectSpigot plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length != 2 && args.length != 3) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return true;
		}
		String cmd = String.format("ap lookup #pt user:%s time:2w action:session", args[1]);
		if (args.length > 2) {
			cmd += args[2];
		} else {
			cmd += "2w";
		}
		Bukkit.dispatchCommand(sender, cmd);
		return true;
	}
}
