package dev.heliosares.auxprotect.command;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.utils.MyPermission;
import dev.heliosares.auxprotect.utils.MySender;
import dev.heliosares.auxprotect.utils.TimeUtil;

public class APCommand implements CommandExecutor {

	private AuxProtect plugin;
	public LookupCommand lookupCommand;
	private TpCommand tpCommand;
	private InvCommand invCommand;
	private PlaytimeCommand playtimeCommand;
	private ActivityCommand activityCommand;
	private XrayCommand xrayCommand;
	private MoneyCommand moneyCommand;
	private RetentionCommand retentionCommand;

	public APCommand(AuxProtect plugin) {
		this.plugin = plugin;
		lookupCommand = new LookupCommand(plugin);
		tpCommand = new TpCommand(plugin);
		invCommand = new InvCommand(plugin, this);
		playtimeCommand = new PlaytimeCommand(plugin);
		activityCommand = new ActivityCommand(plugin);
		xrayCommand = new XrayCommand(plugin);
		moneyCommand = new MoneyCommand(plugin);
		retentionCommand = new RetentionCommand(plugin);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("lookup") || args[0].equalsIgnoreCase("l")) {
				if (!MyPermission.LOOKUP.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return lookupCommand.onCommand(sender, args);
			} else if (args[0].equalsIgnoreCase("purge")) {
				if (!MyPermission.PURGE.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				PurgeCommand.purge(plugin, new MySender(sender), args);
				return true;
			} else if (args[0].equalsIgnoreCase("pt") || args[0].equalsIgnoreCase("playtime")) {
				if (!MyPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return playtimeCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("activity") && plugin.config.isPrivate()) {
				if (!MyPermission.LOOKUP_ACTIVITY.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return activityCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("money")) {
				if (!MyPermission.LOOKUP_MONEY.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return moneyCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("retention") && plugin.config.isPrivate()) {
				if (!MyPermission.LOOKUP_RETENTION.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return retentionCommand.onCommand(sender, command, label, args);
			} else if ((args[0].equalsIgnoreCase("x") || args[0].equalsIgnoreCase("xray"))
					&& plugin.config.isPrivate()) {
				if (!MyPermission.LOOKUP_XRAY.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return xrayCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("tp")) {
				if (!MyPermission.TP.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return tpCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("inv")) {
				if (!MyPermission.INV.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return invCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("debug")) {
				if (!MyPermission.ADMIN.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				int verbosity = -1;
				if (args.length == 2) {
					try {
						verbosity = Integer.parseInt(args[1]);
					} catch (NumberFormatException e) {
					}
					if (verbosity < 0 || verbosity > 5) {
						sender.sendMessage("§cInvalid verbosity level. /ap debug [0-5]");
						return true;
					}
				} else {
					if (plugin.debug > 0) {
						verbosity = 0;
					} else {
						verbosity = 1;
					}
				}
				plugin.debug = verbosity;
				plugin.getConfig().set("debug", plugin.debug);
				plugin.saveConfig();
				sender.sendMessage("Debug " + (verbosity > 0 ? "§aenabled. §7Level: " + verbosity : "§cdisabled."));
				return true;
			} else if (args[0].equalsIgnoreCase("help")) {
				if (!MyPermission.HELP.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				if (args.length < 2) {
					sendHelpMessage(sender, null);
				} else {
					sendHelpMessage(sender, args[1]);
				}
				return true;
			} else if (args[0].equalsIgnoreCase("info")) {
				sendInfo(sender);
				return true;
			} else if (args[0].equalsIgnoreCase("t") || args[0].equalsIgnoreCase("time")) {
				if (!MyPermission.LOOKUP.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMMYY HH:mm.ss");
				if (args.length == 1) {
					sender.sendMessage("§9Server time:");
					sender.sendMessage("§7" + LocalDateTime.now().format(formatter));
					return true;
				} else if (args.length == 2) {
					if (args[1].startsWith("+") || args[1].startsWith("-")) {
						boolean add = args[1].startsWith("+");
						long time = TimeUtil.convertTime(args[1].substring(1));
						if (time <= 0) {
							sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
							return true;
						}
						sender.sendMessage(
								"§9Server time " + (add ? "plus" : "minus") + " " + args[1].substring(1) + ":");
						sender.sendMessage("§7"
								+ LocalDateTime.now().plusSeconds((add ? 1 : -1) * (time / 1000)).format(formatter));
						return true;
					}
				}
				sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
				return true;
			} else if (args[0].equalsIgnoreCase("reload")) {
				if (!MyPermission.ADMIN.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				// plugin.config.save();
				// plugin.saveConfig();
				plugin.reloadConfig();
				sender.sendMessage("§aConfig reloaded");
				return true;
			} else if (args[0].equalsIgnoreCase("stats")) {
				if (!MyPermission.ADMIN.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				sender.sendMessage(String.format("§7Rows: §9%d §7DB Version: §9%d §7Original Version: §9%d",
						plugin.getSqlManager().getCount(), plugin.getSqlManager().getVersion(),
						plugin.getSqlManager().getOriginalVersion()));
				sender.sendMessage("§7Average lookup time: §9"
						+ Math.round(plugin.getSqlManager().lookupTime.getMean() / 1000.0) / 1000.0 + "§7ms");
				sender.sendMessage("§7Average record time per entry: §9"
						+ Math.round(plugin.getSqlManager().putTimePerEntry.getMean() / 1000.0) / 1000.0 + "§7ms");
				sender.sendMessage("§7Average record time per execution: §9"
						+ Math.round(plugin.getSqlManager().putTimePerExec.getMean() / 1000.0) / 1000.0 + "§7ms");
				sender.sendMessage("§7Queued Rows: §9" + plugin.queueSize());
				return true;
			} else if (args[0].equalsIgnoreCase("sql") || args[0].equalsIgnoreCase("sqlu")) {
				if (!MyPermission.SQL.hasPermission(sender) || !sender.equals(Bukkit.getConsoleSender())) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				String msg = "";
				for (int i = 1; i < args.length; i++) {
					msg += args[i] + " ";
				}
				try {
					if (args[0].equalsIgnoreCase("sql")) {
						plugin.getSqlManager().execute(msg.trim());
					} else {
						List<List<String>> results = plugin.getSqlManager().executeUpdate(msg.trim());
						if (results != null) {
							for (List<String> result : results) {
								String line = "";
								for (String part : result) {
									line += part + ", ";
								}
								sender.sendMessage(line);
							}
						}
					}
				} catch (SQLException e) {
					sender.sendMessage("§cAn error occured.");
					plugin.print(e);
					return true;
				}
				sender.sendMessage("§aSQL statement executed successfully.");
				return true;
			} else {
				sender.sendMessage(plugin.translate("unknown-subcommand"));
				return true;
			}
		}
		sendInfo(sender);
		if (MyPermission.HELP.hasPermission(sender)) {
			sender.sendMessage("§7Do §9/ap help§7 for more info.");
		}
		return true;
	}

	private void sendInfo(CommandSender sender) {
		sender.sendMessage("§9AuxProtect"
				+ (MyPermission.ADMIN.hasPermission(sender) ? (" §7v" + plugin.getDescription().getVersion()) : ""));
		sender.sendMessage("§7Developed by §9Heliosares");
		if (MyPermission.ADMIN.hasPermission(sender)) {
			sender.sendMessage("§7§ohttps://www.spigotmc.org/resources/auxprotect.99147/");
		}
	}

	private void sendHelpMessage(CommandSender sender, String subcommand) {
		sender.sendMessage(plugin.translate("command-help-header"));
		if (subcommand == null || subcommand.length() == 0) {
			sender.sendMessage(plugin.translate("command-help-1"));
			sender.sendMessage(plugin.translate("command-help-2"));
			sender.sendMessage(plugin.translate("command-help-3"));
			sender.sendMessage(plugin.translate("command-help-4"));
		} else if (subcommand.equalsIgnoreCase("lookup")) {
			sender.sendMessage(plugin.translate("command-help-lookup-1"));
			sender.sendMessage(plugin.translate("command-help-lookup-2"));
			sender.sendMessage(plugin.translate("command-help-lookup-3"));
			sender.sendMessage(plugin.translate("command-help-lookup-4"));
			sender.sendMessage(plugin.translate("command-help-lookup-5"));
			sender.sendMessage(plugin.translate("command-help-lookup-6"));
			sender.sendMessage(plugin.translate("command-help-lookup-7"));
		} else if (subcommand.equalsIgnoreCase("purge")) {
			sender.sendMessage(plugin.translate("command-help-purge-1"));
			sender.sendMessage(plugin.translate("command-help-purge-2"));
		} else {
			sender.sendMessage(plugin.translate("command-help-unknown-subcommand"));
		}
	}
}
