package dev.heliosares.auxprotect.bungee.command;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import dev.heliosares.auxprotect.bungee.AuxProtectBungee;
import dev.heliosares.auxprotect.core.MyPermission;
import dev.heliosares.auxprotect.core.MySender;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.command.LookupCommand;
import dev.heliosares.auxprotect.spigot.command.PurgeCommand;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

public class APBCommand extends Command implements TabExecutor {

	private AuxProtectBungee plugin;

	public APBCommand(AuxProtectBungee plugin) {
		super("apb");
		this.plugin = plugin;
	}

	private void sendHelpMessage(CommandSender sender, String subcommand) {
		AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-header"));
		if (subcommand == null || subcommand.length() == 0) {
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-1"));
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-2"));
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-3"));
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-4"));
		} else if (subcommand.equalsIgnoreCase("lookup")) {
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-lookup-1"));
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-lookup-2"));
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-lookup-3"));
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-lookup-4"));
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-lookup-5"));
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-lookup-6"));
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-lookup-7"));
		} else if (subcommand.equalsIgnoreCase("purge")) {
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-purge-1"));
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-purge-2"));
		} else {
			AuxProtectBungee.tell(sender, plugin.lang.translate("command-help-unknown-subcommand"));
		}
	}

	@Override
	public void execute(CommandSender sender, String[] args) {

		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("lookup") || args[0].equalsIgnoreCase("l")) {
				if (!MyPermission.LOOKUP.hasPermission(sender)) {
					AuxProtectBungee.tell(sender, plugin.lang.translate("no-permission"));
					return;
				}
				LookupCommand.onCommand(plugin, new MySender(sender), args);
				return;
			} else if (args[0].equalsIgnoreCase("help")) {
				if (!MyPermission.HELP.hasPermission(sender)) {
					AuxProtectBungee.tell(sender, plugin.lang.translate("no-permission"));
					return;
				}
				if (args.length < 2) {
					sendHelpMessage(sender, null);
				} else {
					sendHelpMessage(sender, args[1]);
				}
				return;
			} else if (args[0].equalsIgnoreCase("info")) {
				AuxProtectBungee.tell(sender,
						"§9AuxProtect" + (MyPermission.ADMIN.hasPermission(sender)
								? (" §7v" + plugin.getDescription().getVersion())
								: ""));

				AuxProtectBungee.tell(sender, "§7Developed by §9Heliosares");
				return;
			} else if (args[0].equalsIgnoreCase("debug")) {
				if (!MyPermission.ADMIN.hasPermission(sender)) {
					AuxProtectBungee.tell(sender, plugin.lang.translate("no-permission"));
					return;
				}
				int verbosity = -1;
				if (args.length == 2) {
					try {
						verbosity = Integer.parseInt(args[1]);
					} catch (NumberFormatException e) {
					}
					if (verbosity < 0 || verbosity > 5) {
						AuxProtectBungee.tell(sender, "§cInvalid verbosity level. /ap debug [0-5]");
						return;
					}
				} else {
					if (plugin.debug > 0) {
						verbosity = 0;
					} else {
						verbosity = 1;
					}
				}
				plugin.debug = verbosity;
				AuxProtectBungee.tell(sender,
						"Debug " + (verbosity > 0 ? "§aenabled. §7Level: " + verbosity : "§cdisabled."));
				return;
			} else if (args[0].equalsIgnoreCase("sql")) {
				if (!MyPermission.SQL.hasPermission(sender)) {
					AuxProtectBungee.tell(sender, plugin.lang.translate("no-permission"));
					return;
				}
				if (plugin.debug == 0) {
					AuxProtectBungee.tell(sender, "§cDebug mode must be enabled to use this command.");
					return;
				}
				String msg = "";
				for (int i = 1; i < args.length; i++) {
					msg += args[i] + " ";
				}
				try {
					plugin.getSqlManager().execute(msg.trim());
				} catch (SQLException e) {
					AuxProtectBungee.tell(sender, "§cAn error occured.");
					plugin.print(e);
					return;
				}
				AuxProtectBungee.tell(sender, "§aSQL statement executed successfully.");
				return;
			} else if (args[0].equalsIgnoreCase("purge")) {
				if (!MyPermission.PURGE.hasPermission(sender)) {
					AuxProtectBungee.tell(sender, plugin.lang.translate("no-permission"));
					return;
				}
				PurgeCommand.purge(plugin, new MySender(sender), args);
				return;
			} else {
				AuxProtectBungee.tell(sender, plugin.lang.translate("unknown-subcommand"));
				return;
			}
		}
		return;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, String[] args) {
		List<String> possible = new ArrayList<>();
		String currentArg = args[args.length - 1];

		if (args.length == 1) {
			if (MyPermission.LOOKUP.hasPermission(sender)) {
				possible.add("lookup");
			}
			if (MyPermission.ADMIN.hasPermission(sender)) {
				possible.add("debug");
			}
			if (MyPermission.HELP.hasPermission(sender)) {
				possible.add("help");
			}
			if (MyPermission.PURGE.hasPermission(sender)) {
				possible.add("purge");
			}
			possible.add("info");
		}
		if (args.length >= 2) {
			if ((args[0].equalsIgnoreCase("lookup") || args[0].equalsIgnoreCase("l"))
					&& MyPermission.LOOKUP.hasPermission(sender)) {
				possible.add("time:");
				possible.add("target:");
				possible.add("action:");
				possible.add("user:");
				if (currentArg.startsWith("action:") || currentArg.startsWith("a:")) {
					String action = currentArg.split(":")[0] + ":";
					for (EntryAction eaction : EntryAction.values()) {
						if (eaction.isBungee() && eaction.isEnabled()) {
							String actString = eaction.toString().toLowerCase();
							if (eaction.hasDual) {
								possible.add(action + "+" + actString);
								possible.add(action + "-" + actString);

							}
							possible.add(action + actString);
						}
					}
				}
				if (currentArg.startsWith("user:") || currentArg.startsWith("u:") || currentArg.startsWith("target:")) {
					String user = currentArg.split(":")[0] + ":";
					for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
						possible.add(user + player.getName());
					}
				}
				if (currentArg.startsWith("time:") || currentArg.startsWith("t:")) {
					if (currentArg.matches("t(ime)?:\\d+")) {
						possible.add(currentArg + "s");
						possible.add(currentArg + "m");
						possible.add(currentArg + "h");
						possible.add(currentArg + "d");
						possible.add(currentArg + "w");
					}
				}
				if (currentArg.startsWith("b"))
					possible.add("before:");
				if (currentArg.startsWith("a"))
					possible.add("after:");

				if (currentArg.startsWith("#")) {
					possible.add("#bw");
					possible.add("#count");
				}

				for (int i = 1; i < args.length - 1; i++) {
					String arg = args[i];
					if (!arg.contains(":"))
						continue;
					arg = arg.substring(0, arg.indexOf(":") + 1);
					possible.remove(arg);
				}
			} else if ((args[0].equalsIgnoreCase("help")) && MyPermission.HELP.hasPermission(sender)) {
				possible.add("lookup");
				possible.add("purge");
			}
		}

		List<String> output = new ArrayList<>();
		copyPartialMatches(currentArg, possible, output);
		return output;
	}

	public static void copyPartialMatches(String arg, List<String> possible, List<String> output) {
		arg = arg.toLowerCase();
		for (String poss : possible) {
			if (poss.toLowerCase().startsWith(arg)) {
				output.add(poss);
			}
		}
	}
}
