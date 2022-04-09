package dev.heliosares.auxprotect.command;

import java.sql.SQLException;
import dev.heliosares.auxprotect.IAuxProtect;
import dev.heliosares.auxprotect.database.SQLManager.TABLE;
import dev.heliosares.auxprotect.utils.MySender;
import dev.heliosares.auxprotect.utils.TimeUtil;

public class PurgeCommand {

	public static void purge(IAuxProtect plugin, MySender sender, String[] args) {
		if (args.length != 3) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return;
		}

		TABLE table = null;
		try {
			table = TABLE.valueOf(args[1].toUpperCase());
		} catch (IllegalArgumentException e) {
			sender.sendMessage(plugin.translate("purge-table"));
			return;
		}
		if (sender.isBungee() && !table.isOnBungee()) {
			sender.sendMessage(plugin.translate("purge-table"));
			return;
		}
		long time = TimeUtil.convertTime(args[2]);

		if (time < 1000 * 3600 * 24 * 14) {
			sender.sendMessage(plugin.translate("purge-time"));
			return;
		}

		sender.sendMessage(String.format(plugin.translate("purge-purging"), table.toString()));
		boolean success = false;
		try {
			success = plugin.getSqlManager().purge(sender, table, time);
		} catch (SQLException e) {
			plugin.print(e);
		}
		if (success) {
			sender.sendMessage(plugin.translate("purge-complete"));
		} else {
			sender.sendMessage(plugin.translate("purge-error"));
		}
	}
}
