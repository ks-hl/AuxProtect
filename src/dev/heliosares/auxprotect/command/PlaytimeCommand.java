package dev.heliosares.auxprotect.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import dev.heliosares.auxprotect.AuxProtect;

public class PlaytimeCommand implements CommandExecutor {

	private AuxProtect plugin;

	public PlaytimeCommand(AuxProtect plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length != 2) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return true;
		}
		Bukkit.dispatchCommand(sender, String.format("ap lookup #pt user:%s time:2w action:session", args[1]));
		return true;
	}
}
