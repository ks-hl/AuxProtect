package dev.heliosares.auxprotect.core.commands;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.ConnectionPool.BusyException;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.utils.TimeUtil;

import dev.heliosares.auxprotect.core.Language;

public class PurgeCommand extends Command {

	public PurgeCommand(IAuxProtect plugin) {
		super(plugin, "purge", APPermission.PURGE);
	}

	public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
		if (args.length != 3) {
			sender.sendLang(Language.L.INVALID_SYNTAX);
			return;
		}
		if (!plugin.getSqlManager().isConnected()) {
			sender.sendLang(Language.L.DATABASE_BUSY);
			return;
		}

		Table table_ = null;
		if (!args[1].equalsIgnoreCase("all")) {
			try {
				table_ = Table.valueOf(args[1].toUpperCase());
			} catch (IllegalArgumentException e) {
			}
			if (table_ == null || !table_.exists(plugin)) {
				sender.sendLang(Language.L.PURGE_TABLE);
				return;
			}
		}
		if (table_ == Table.AUXPROTECT_LONGTERM) {
			sender.sendLang(Language.L.PURGE_TABLE);
			return;
		}
		final Table table = table_;
		long time_ = 0;
		try {
			time_ = TimeUtil.stringToMillis(args[2]);
		} catch (NumberFormatException e) {
			sender.sendLang(Language.L.INVALID_SYNTAX);
			return;
		}

		if (time_ < 1000 * 3600 * 24 * 14) {
			sender.sendLang(Language.L.PURGE_TIME);
			return;
		}

		final long time = time_;
		sender.sendLang(Language.L.PURGE_PURGING, table == null ? "all" : table.toString());
		plugin.runAsync(new Runnable() {

			@Override
			public void run() {
				try {
					plugin.getSqlManager().purge(sender, table, time);
					sender.sendLang(Language.L.PURGE_UIDS);
					plugin.getSqlManager().purgeUIDs();

					if (!plugin.getSqlManager().isMySQL()) {
						sender.sendLang(Language.L.PURGE_VACUUM);
						plugin.getSqlManager().vacuum();
					}
				} catch (SQLException | BusyException e) {
					plugin.print(e);
					sender.sendLang(Language.L.PURGE_ERROR);
					return;
				}
				sender.sendLang(Language.L.PURGE_COMPLETE);
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

		if (args.length == 2) {
			for (Table table : Table.values()) {
				possible.add(table.toString());
			}
			possible.add("all");
		}
		if (args.length == 3) {
			possible.add("<time>");
		}
		
		return possible;
	}
}
