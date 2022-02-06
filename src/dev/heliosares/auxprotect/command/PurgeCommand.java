package dev.heliosares.auxprotect.command;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.SQLiteManager.TABLE;
import dev.heliosares.auxprotect.utils.TimeUtil;

public class PurgeCommand implements CommandExecutor {

	private AuxProtect plugin;

	private ArrayList<String> validParams;

	public PurgeCommand(AuxProtect plugin) {
		this.plugin = plugin;
		results = new HashMap<>();
		validParams = new ArrayList<>();
		validParams.add("time");
		validParams.add("world");
		validParams.add("action");
	}

	HashMap<String, Results> results;

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
			@Override
			public void run() {
				purge(sender, args);
			}
		});
		return true;
	}

	public void purge(CommandSender sender, String[] args) {
		if (args.length != 3) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return;
		}

		TABLE table = null;
		try {
			table = TABLE.valueOf(args[1].toUpperCase());
		} catch (IllegalArgumentException e) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return;
		}
		long time = TimeUtil.convertTime(args[2]);

		if (time < 1000 * 3600 * 24 * 14) {
			sender.sendMessage(plugin.translate("purge-time"));
			return;
		}

		sender.sendMessage(plugin.translate("purge-purging"));
		boolean success = false;
		try {
			success = plugin.getSqlManager().purge(sender, table, time);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		if (success) {
			sender.sendMessage(plugin.translate("purge-complete"));
		} else {
			sender.sendMessage(plugin.translate("purge-error"));
		}
	}
}
