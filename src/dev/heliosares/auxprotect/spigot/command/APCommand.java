package dev.heliosares.auxprotect.spigot.command;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.MySender;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.TimeUtil;

public class APCommand implements CommandExecutor {

	private AuxProtectSpigot plugin;
	public LookupCommand lookupCommand;
	private TpCommand tpCommand;
	private InvCommand invCommand;
	private InventoryCommand inventoryCommand;
	private PlaytimeCommand playtimeCommand;
	private ActivityCommand activityCommand;
	private XrayCommand xrayCommand;
	private MoneyCommand moneyCommand;
	private RetentionCommand retentionCommand;
	private WatchCommand watchCommand;

	public APCommand(AuxProtectSpigot plugin) {
		this.plugin = plugin;
		lookupCommand = new LookupCommand(plugin);
		tpCommand = new TpCommand(plugin);
		invCommand = new InvCommand(plugin, this);
		inventoryCommand = new InventoryCommand(plugin, this);
		playtimeCommand = new PlaytimeCommand(plugin);
		activityCommand = new ActivityCommand(plugin);
		xrayCommand = new XrayCommand(plugin);
		moneyCommand = new MoneyCommand(plugin);
		retentionCommand = new RetentionCommand(plugin);
		watchCommand = new WatchCommand(plugin);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("lookup") || args[0].equalsIgnoreCase("l")) {
				if (!APPermission.LOOKUP.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return lookupCommand.onCommand(sender, args);
			} else if (args[0].equalsIgnoreCase("watch") || args[0].equalsIgnoreCase("w")) {
				if (!APPermission.WATCH.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return watchCommand.onCommand(sender, args);
			} else if (args[0].equalsIgnoreCase("purge")) {
				if (!APPermission.PURGE.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				PurgeCommand.purge(plugin, new MySender(sender), args);
				return true;
			} else if (args[0].equalsIgnoreCase("pt") || args[0].equalsIgnoreCase("playtime")) {
				if (!APPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return playtimeCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("activity") && plugin.getAPConfig().isPrivate()) {
				if (!APPermission.LOOKUP_ACTIVITY.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return activityCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("money")) {
				if (!APPermission.LOOKUP_MONEY.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return moneyCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("retention") && plugin.getAPConfig().isPrivate()) {
				if (!APPermission.LOOKUP_RETENTION.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return retentionCommand.onCommand(sender, command, label, args);
			} else if ((args[0].equalsIgnoreCase("x") || args[0].equalsIgnoreCase("xray"))
					&& plugin.getAPConfig().isPrivate()) {
				if (!APPermission.LOOKUP_XRAY.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return xrayCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("tp")) {
				if (!APPermission.TP.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return tpCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("inv")) {
				if (!APPermission.INV.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return invCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("inventory")) {
				if (!APPermission.INV.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				return inventoryCommand.onCommand(sender, command, label, args);
			} else if (args[0].equalsIgnoreCase("saveinv")) {
				if (!APPermission.INV_SAVE.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				if (args.length != 2) {
					sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
					return true;
				}
				Player target = Bukkit.getPlayer(args[1]);
				APPlayer apTarget = null;
				if (target != null) {
					apTarget = plugin.getAPPlayer(target);
				}
				if (apTarget == null) {
					sender.sendMessage(String.format(plugin.translate("lookup-playernotfound"), args[1]));
					return true;
				}
				if (!APPermission.ADMIN.hasPermission(sender)
						&& System.currentTimeMillis() - apTarget.lastLoggedInventory < 10000L) {
					sender.sendMessage(plugin.translate("inv-toosoon"));
					return true;
				}
				long time = apTarget.logInventory("manual");
				sender.sendMessage(String.format(plugin.translate("inv-manual-success"), target.getName(),
						target.getName().endsWith("s") ? "" : "s", time + "e"));
				return true;
			} else if (args[0].equalsIgnoreCase("debug")) {
				if (!APPermission.ADMIN.hasPermission(sender)) {
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
				if (!APPermission.HELP.hasPermission(sender)) {
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
				if (!APPermission.LOOKUP.hasPermission(sender)) {
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
						long time;
						try {
							time = TimeUtil.stringToMillis(args[1].substring(1));
						} catch (NumberFormatException e) {
							sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
							return true;
						}
						sender.sendMessage(
								"§9Server time " + (add ? "plus" : "minus") + " " + args[1].substring(1) + ":");
						sender.sendMessage("§7"
								+ LocalDateTime.now().plusSeconds((add ? 1 : -1) * (time / 1000)).format(formatter));
						sender.sendMessage(
								String.format("§7%s %s", TimeUtil.millisToString(time), add ? "from now" : "ago"));
						sender.sendMessage(String.format("§7%s %s", TimeUtil.millisToStringExtended(time),
								add ? "from now" : "ago"));

						return true;
					}
				}
				sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
				return true;
			} else if (args[0].equalsIgnoreCase("reload")) {
				if (!APPermission.ADMIN.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				// plugin.config.save();
				// plugin.saveConfig();
				plugin.reloadConfig();
				sender.sendMessage("§aConfig reloaded");
				return true;
			} else if (args[0].equalsIgnoreCase("stats")) {
				if (!APPermission.ADMIN.hasPermission(sender)) {
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
			} else if (args[0].equalsIgnoreCase("backup")) {
				if (!APPermission.SQL.hasPermission(sender) || !sender.equals(Bukkit.getConsoleSender())) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				plugin.runAsync(new Runnable() {

					@Override
					public void run() {
						String backup = null;
						try {
							backup = plugin.getSqlManager().backup();
						} catch (IOException e) {
							plugin.print(e);
							return;
						}
						sender.sendMessage("Backup created: " + backup);
					}
				});
				return true;
			} else if (args[0].equalsIgnoreCase("sql") || args[0].equalsIgnoreCase("sqlu")) {
				if (!APPermission.SQL.hasPermission(sender) || !sender.equals(Bukkit.getConsoleSender())) {
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
				String msg = "";
				for (int i = 1; i < args.length; i++) {
					msg += args[i] + " ";
				}
				final String stmt = msg.trim();
				plugin.runAsync(new Runnable() {

					@Override
					public void run() {
						sender.sendMessage("§aRunning...");
						try {
							if (args[0].equalsIgnoreCase("sql")) {
								plugin.getSqlManager().execute(stmt);
							} else {
								List<List<String>> results = plugin.getSqlManager().executeUpdate(stmt);
								if (results != null) {
									for (List<String> result : results) {
										String line = "";
										for (String part : result) {
											if (line.length() > 0) {
												line += ", ";
											}
											line += part;
										}
										sender.sendMessage(line);
									}
								}
							}
						} catch (SQLException e) {
							sender.sendMessage("§cAn error occured.");
							plugin.print(e);
							return;
						}
						sender.sendMessage("§aSQL statement executed successfully.");
					}
				});

				return true;
			} else {
				sender.sendMessage(plugin.translate("unknown-subcommand"));
				return true;
			}
		}
		sendInfo(sender);
		if (APPermission.HELP.hasPermission(sender)) {
			sender.sendMessage("§7Do §9/ap help§7 for more info.");
		}
		return true;
	}

	private void sendInfo(CommandSender sender) {
		sender.sendMessage("§9AuxProtect"
				+ (APPermission.ADMIN.hasPermission(sender) ? (" §7v" + plugin.getDescription().getVersion()) : ""));
		sender.sendMessage("§7Developed by §9Heliosares");
		if (APPermission.ADMIN.hasPermission(sender)) {
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
