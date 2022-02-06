package dev.heliosares.auxprotect.bungee.command;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import dev.heliosares.auxprotect.bungee.AuxProtectBungee;
import dev.heliosares.auxprotect.utils.MyPermission;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

public class APCommand extends Command implements TabExecutor {

	private AuxProtectBungee plugin;
	private LookupCommand lookupCommand;

	public APCommand(AuxProtectBungee plugin) {
		super("apb");
		this.plugin = plugin;
		lookupCommand = new LookupCommand(plugin);
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
				lookupCommand.onCommand(sender, args);
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
					e.printStackTrace();
					return;
				}
				AuxProtectBungee.tell(sender, "§aSQL statement executed successfully.");
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
				possible.add("playtime");
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
				possible.addAll(lookupCommand.onTabComplete(sender, args));
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
