package dev.heliosares.auxprotect.spigot.command;

import java.sql.SQLException;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.MySender;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.utils.TimeUtil;

public class PurgeCommand {

	public static void purge(IAuxProtect plugin, MySender sender, String[] args) {
		if (args.length != 3) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return;
		}

		Table table_ = null;
		if (!args[1].equalsIgnoreCase("all")) {
			try {
				table_ = Table.valueOf(args[1].toUpperCase());
			} catch (IllegalArgumentException e) {
			}
			if (table_ == null || (sender.isBungee() && !table_.isOnBungee())) {
				sender.sendMessage(plugin.translate("purge-table"));
				return;
			}
		}
		if (table_ == Table.AUXPROTECT_LONGTERM) {
			sender.sendMessage(plugin.translate("purge-table"));
			return;
		}
		final Table table = table_;
		long time_ = 0;
		try {
			time_ = TimeUtil.stringToMillis(args[2]);
		} catch (NumberFormatException e) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return;
		}

		if (time_ < 1000 * 3600 * 24 * 14) {
			sender.sendMessage(plugin.translate("purge-time"));
			return;
		}

		final long time = time_;
		sender.sendMessage(String.format(plugin.translate("purge-purging"), table == null ? "all" : table.toString()));
		plugin.runAsync(new Runnable() {

			@Override
			public void run() {
				try {
					plugin.getSqlManager().purge(sender, table, time);
					sender.sendMessage(plugin.translate("purge-uids"));
					plugin.getSqlManager().purgeUIDs();

					if (!plugin.getSqlManager().isMySQL()) {
						sender.sendMessage(plugin.translate("purge-vacuum"));
						plugin.getSqlManager().vacuum();
					}
				} catch (SQLException e) {
					plugin.print(e);
					sender.sendMessage(plugin.translate("purge-error"));
					return;
				}
				sender.sendMessage(plugin.translate("purge-complete"));
			}
		});
	}
}
