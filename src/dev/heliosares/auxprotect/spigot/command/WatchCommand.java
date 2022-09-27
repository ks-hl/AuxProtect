package dev.heliosares.auxprotect.spigot.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.MySender;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.core.Parameters.Flag;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.Results;

public class WatchCommand {

	private static ConcurrentLinkedQueue<DbEntry> queue = new ConcurrentLinkedQueue<>();
	private final IAuxProtect plugin;

	static final List<WatchRecord> records;

	private static int nextid = 0;

	private static class WatchRecord {
		public final int id;
		public final MySender sender;
		public final Parameters params;
		public final String originalCommand;

		public WatchRecord(MySender sender, Parameters params, String originalCommand) {
			this.id = nextid++;
			this.sender = sender;
			this.params = params;
			this.originalCommand = originalCommand;
		}
	}

	static {
		records = new ArrayList<>();
	}

	public WatchCommand(IAuxProtect plugin) {
		this.plugin = plugin;
	}

	public boolean onCommand(org.bukkit.command.CommandSender sender1, String[] args) {
		MySender sender = new MySender(sender1);
		onCommand(plugin, sender, args);
		return true;
	}

	public static void onCommand(IAuxProtect plugin, MySender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return;
		}
		Runnable run = new Runnable() {

			@Override
			public void run() {
				if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
					if (args.length != 3) {
						sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
						return;
					}
					int id = -1;
					try {
						id = Integer.parseInt(args[2]);
					} catch (NumberFormatException ignored) {

					}
					if (id < 0) {
						sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
						return;
					}
					Iterator<WatchRecord> it = records.iterator();
					int count = 0;
					while (it.hasNext()) {
						WatchRecord record = it.next();
						if (sender.getUniqueId().equals(record.sender.getUniqueId()) && record.id == id) {
							count++;
							it.remove();
						}
					}
					if (count == 0) {
						sender.sendMessage(plugin.translate("watch-none"));
					} else {
						sender.sendMessage(String.format(plugin.translate("watch-removed"), count));
					}
					return;
				} else if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
					Iterator<WatchRecord> it = records.iterator();
					int count = 0;
					while (it.hasNext()) {
						WatchRecord record = it.next();
						if (sender.getUniqueId().equals(record.sender.getUniqueId())) {
							count++;
							it.remove();
						}
					}
					if (count == 0) {
						sender.sendMessage(plugin.translate("watch-none"));
					} else {
						sender.sendMessage(String.format(plugin.translate("watch-removed"), count));
					}
					return;
				} else if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
					List<String> lines = new ArrayList<>();
					for (WatchRecord record : records) {
						if (sender.getUniqueId().equals(record.sender.getUniqueId())) {
							lines.add(record.id + ": " + record.originalCommand);
						}
					}
					if (lines.isEmpty()) {
						sender.sendMessage(plugin.translate("watch-none"));
					} else {
						sender.sendMessage(plugin.translate("watch-ing"));
						for (String line : lines) {
							sender.sendMessage(line);
						}
					}
					return;
				}

				Parameters params = null;
				try {
					params = Parameters.parse(plugin, sender, args);
				} catch (Exception e) {
					sender.sendMessage(e.getMessage());
					return;
				}
				String command = "/ap";
				for (String arg : args) {
					command += " " + arg;
				}
				WatchRecord record = new WatchRecord(sender, params, command);
				WatchCommand.records.add(record);
				sender.sendMessage(String.format(plugin.translate("watch-now"), record.id + ": " + command));
			}
		};
		plugin.runAsync(run);
	}

	public static void notify(DbEntry entry) {
		if (records.size() == 0) {
			return;
		}
		queue.add(entry);
	}

	public static void tick(IAuxProtect plugin) {
		if (queue.size() == 0) {
			return;
		}
		DbEntry entry = null;
		while ((entry = queue.poll()) != null) {
			HashSet<UUID> informed = new HashSet<>();
			for (WatchRecord record : records) {
				if (record.params.matches(entry)) {
					if (informed.add(record.sender.getUniqueId())) {
						Results.sendEntry(plugin, record.sender, entry, 0, true,
								!record.params.getFlags().contains(Flag.HIDE_COORDS));
					}
				}
			}
		}
	}
}
