package dev.heliosares.auxprotect.core.commands;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.CommandException;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.ConnectionPool;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.utils.HasteBinAPI;
import dev.heliosares.auxprotect.utils.StackUtil;

public class DumpCommand extends Command {

	public DumpCommand(IAuxProtect plugin) {
		super(plugin, "dump", APPermission.ADMIN, "stats");
	}

	@Override
	public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
		sender.sendMessageRaw("§aBuilding trace...");
		plugin.runAsync(() -> {
			boolean verbose = false;
			boolean chat = false;
			boolean file = false;
			boolean config = false;
			boolean stats = args[0].equalsIgnoreCase("stats");
			if (!stats) {
				for (int i = 1; i < args.length; i++) {
					switch (args[i].toLowerCase()) {
					case "chat":
						chat = true;
						break;
					case "verbose":
						verbose = true;
						break;
					case "file":
						file = true;
						break;
					case "config":
						config = true;
						break;
					}
				}
			}
			try {
				sender.sendMessageRaw("§a" + dump(plugin, verbose, chat, file, config, stats));
			} catch (Exception e) {
				plugin.print(e);
				sender.sendLang("error");
			}
			if (config) {
				sender.sendMessageRaw(
						"§cWARNING! §eThis contains the contents of config.yml. Please ensure all §cMySQL passwords §ewere properly removed before sharing.");
			}
		});
	}

	@Override
	public boolean exists() {
		return true;
	}

	public static String dump(IAuxProtect plugin, boolean verbose, boolean chat, boolean file, boolean config,
			boolean stats) throws Exception {
		String trace = "";
		if (!stats) {
			trace += "Generated: " + LocalDateTime.now().format(Results.dateFormatter) + " ("
					+ System.currentTimeMillis() + ")\n";
		}
		trace += "Plugin version: " + plugin.getPluginVersion() + "\n";
		trace += "Key: ";
		if (plugin.getAPConfig().isPrivate()) {
			trace += "private." + plugin.getAPConfig().getKeyHolder();
		} else if (plugin.getAPConfig().isDonor()) {
			trace += "donor." + plugin.getAPConfig().getKeyHolder();
		} else {
			trace += "none";
		}
		trace += "\n";
		trace += "DB version: " + plugin.getSqlManager().getVersion() + "\n";
		trace += "Original DB version: " + plugin.getSqlManager().getOriginalVersion() + "\n";
		if (!stats) {
			trace += "Server Version: " + plugin.getPlatformVersion() + "\n";
			trace += "Java: " + Runtime.version().toString() + "\n";
		}
		trace += "Queued: " + plugin.queueSize() + "\n";
		if (!stats) {
			trace += "Pool:\n";
			trace += "  Size: " + plugin.getSqlManager().getConnectionPoolSize() + "\n";
			trace += "  Alive: " + ConnectionPool.getNumAlive() + "\n";
			trace += "  Born: " + ConnectionPool.getNumBorn() + "\n";
		}
		long read[] = ConnectionPool.calculateReadTimes();
		if (read != null) {
			trace += "Read:\n";
			trace += "  Average Time: " + Math.round((double) read[1] / read[2] * 100.0) / 100.0 + "ms\n";
			trace += "  Duty: " + Math.round((double) read[1] / read[0] * 10000.0) / 100.0 + "%\n";
			if (verbose) {
				trace += "  Across: " + read[0] + "ms\n";
				trace += "  Count: " + read[2] + "\n";
			}
		}
		long write[] = ConnectionPool.calculateWriteTimes();
		if (write != null) {
			trace += "Write:\n";
			trace += "  Average Time: " + Math.round((double) write[1] / write[2] * 100.0) / 100.0 + "ms\n";
			trace += "  Duty: " + Math.round((double) write[1] / write[0] * 10000.0) / 100.0 + "%\n";
			if (verbose) {
				trace += "  Across: " + write[0] + "ms\n";
				trace += "  Count: " + write[2] + "\n";
			}
		}
		if (!stats) {
			trace += "Database type: " + (plugin.getSqlManager().isMySQL() ? "mysql" : "sqlite") + "\n";
			if (!plugin.getSqlManager().isMySQL()) {
				boolean size = false;
				try {
					File sqlitefile = new File(plugin.getDataFolder(), "database/auxprotect.db");
					if (sqlitefile.exists()) {
						trace += "File size: " + (Files.size(sqlitefile.toPath()) / 1024 / 1024) + "MB\n";
						size = true;
					}
				} catch (Exception ignored) {
				}
				if (!size) {
					trace += "*No file\n";
				}
			}
		}
		trace += "Row counts: " + plugin.getSqlManager().getCount() + " total\n";
		if (verbose) {
			ArrayList<String[]> counts = new ArrayList<>();
			int widest = 0;
			for (Table table : Table.values()) {
				String[] arr = null;
				try {
					arr = (new String[] { table.toString(), String.valueOf(plugin.getSqlManager().count(table)) });
				} catch (SQLException e) {
					arr = (new String[] { table.toString(), "ERROR" });
				}
				int width = arr[0].length() + arr[1].length();
				if (width > widest) {
					widest = width;
				}
				counts.add(arr);
			}
			for (String[] arr : counts) {
				String pad = "";
				int width = arr[0].length() + arr[1].length();
				for (int i = 0; i < widest - width + 3; i++) {
					pad += ".";
				}
				trace += "  " + arr[0] + pad + arr[1] + "\n";
			}
		}
		if ((chat && !verbose) || stats) {
			return trace;
		}

		trace += "\n";

		if (config) {
			try {
				trace += "config.yml:";
				trace += dumpContents(plugin);
			} catch (Exception e) {
				trace += "Error reading config.yml\n";
			}
		} else {

		}

		trace += "\n";

		trace += "Error Log:\n" + plugin.getStackLog() + "\n\n";

		if (verbose) {
			trace += "Thread Trace:\n";
			trace += StackUtil.dumpThreadStack();
			trace += "\n\n";
		}

		if (!file) {
			try {
				return HasteBinAPI.post(trace);
			} catch (Exception e) {
				plugin.warning("Failed to upload trace, writing to file...");
				plugin.print(e);
			}
		}
		File dumpdir = new File(plugin.getDataFolder(), "dump");
		File dump = new File(dumpdir, "dump-" + System.currentTimeMillis() + ".txt");
		dumpdir.mkdirs();
		BufferedWriter writer = new BufferedWriter(new FileWriter(dump));
		writer.write(trace);
		writer.close();
		return dump.getAbsolutePath();
	}

	private static String dumpContents(IAuxProtect plugin) {
		return dumpContents(plugin, "", 0);
	}

	private static String dumpContents(IAuxProtect plugin, String parent, int depth) {
		String build = "";
		if (parent.length() > 0) {
			for (int i = 0; i < depth; i++) {
				build += " ";
			}
			build += parent.substring(parent.lastIndexOf(".") + 1) + ": ";
		}
		if (parent.toLowerCase().contains("mysql") || parent.toLowerCase().contains("passw")) {
			build += "REDACTED\n";
			return build;
		}
		if (plugin.getAPConfig().getConfig().isSection(parent)) {
			build += "\n";
			for (String key : plugin.getAPConfig().getConfig().getKeys(parent, false)) {
				if (key.length() == 0) {
					continue;
				}
				build += dumpContents(plugin, parent + (parent.length() > 0 ? "." : "") + key, depth + 2);
			}
		} else {
			build += plugin.getAPConfig().getConfig().get(parent) + "\n";
		}
		return build;
	}

	@Override
	public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
		List<String> out = new ArrayList<>();
		out.add("verbose");
		out.add("chat");
		out.add("file");
		out.add("config");
		return out;
	}
}
