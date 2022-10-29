package dev.heliosares.auxprotect.core.commands;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.StringUtil;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.CommandException;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.utils.TimeUtil;

public class PurgeCommand extends Command {

	public PurgeCommand(IAuxProtect plugin) {
		super(plugin, "purge", APPermission.PURGE);
	}

	public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
		if (args.length != 3) {
			sender.sendLang("lookup-invalid-syntax");
			return;
		}
		if (!plugin.getSqlManager().isConnected()) {
			sender.sendLang("database-busy");
			return;
		}

		Table table_ = null;
		if (!args[1].equalsIgnoreCase("all")) {
			try {
				table_ = Table.valueOf(args[1].toUpperCase());
			} catch (IllegalArgumentException e) {
			}
			if (table_ == null || !table_.exists(plugin)) {
				sender.sendLang("purge-table");
				return;
			}
		}
		if (table_ == Table.AUXPROTECT_LONGTERM) {
			sender.sendLang("purge-table");
			return;
		}
		final Table table = table_;
		long time_ = 0;
		try {
			time_ = TimeUtil.stringToMillis(args[2]);
		} catch (NumberFormatException e) {
			sender.sendLang("lookup-invalid-syntax");
			return;
		}

		if (time_ < 1000 * 3600 * 24 * 14) {
			sender.sendLang("purge-time");
			return;
		}

		final long time = time_;
		sender.sendLang("purge-purging", table == null ? "all" : table.toString());
		plugin.runAsync(new Runnable() {

			@Override
			public void run() {
				try {
					plugin.getSqlManager().purge(sender, table, time);
					sender.sendLang("purge-uids");
					plugin.getSqlManager().purgeUIDs();

					if (!plugin.getSqlManager().isMySQL()) {
						sender.sendLang("purge-vacuum");
						plugin.getSqlManager().vacuum();
					}
				} catch (SQLException e) {
					plugin.print(e);
					sender.sendLang("purge-error");
					return;
				}
				sender.sendLang("purge-complete");
			}
		});
	}

	@Override
	public boolean exists() {
		return true;
	}

	@Override
	public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
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
