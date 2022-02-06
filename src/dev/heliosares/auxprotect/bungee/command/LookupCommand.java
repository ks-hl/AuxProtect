package dev.heliosares.auxprotect.bungee.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import dev.heliosares.auxprotect.bungee.AuxProtectBungee;
import dev.heliosares.auxprotect.bungee.Results;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLiteManager.LookupException;
import dev.heliosares.auxprotect.utils.TimeUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class LookupCommand {

	private AuxProtectBungee plugin;

	private ArrayList<String> validParams;

	public LookupCommand(AuxProtectBungee plugin) {
		this.plugin = plugin;
		results = new HashMap<>();
		validParams = new ArrayList<>();
		validParams.add("action");
		validParams.add("after");
		validParams.add("before");
		validParams.add("target");
		validParams.add("time");
		validParams.add("world");
		validParams.add("user");
		validParams.add("radius");
	}

	HashMap<String, Results> results;

	public boolean onCommand(final CommandSender sender, String[] args) {
		if (args.length < 2) {
			AuxProtectBungee.tell(sender, plugin.lang.translate("lookup-invalid-syntax"));
			return true;
		}
		if (args.length == 2) {
			int page = -1;
			int perpage = 4;
			if (args[1].contains(":")) {
				String[] split = args[1].split(":");

				try {
					page = Integer.parseInt(split[0]);
					perpage = Integer.parseInt(split[1]);
				} catch (NumberFormatException e) {

				}
			} else {
				try {
					page = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {

				}
			}
			if (page > 0) {
				if (perpage > 99)
					perpage = 99;
				String uuid = "nonplayer";
				if (sender instanceof ProxiedPlayer) {
					uuid = ((ProxiedPlayer) sender).getUniqueId().toString();
				}
				if (results.containsKey(uuid)) {
					results.get(uuid).showPage(page, perpage);
					return true;
				} else {
					AuxProtectBungee.tell(sender, plugin.lang.translate("lookup-no-results-selected"));
					return true;
				}
			}
		}
		HashMap<String, String> params = new HashMap<>();
		boolean count = false;
		boolean showTime = false;
		boolean bw = false;
		long startTime = 0;
		for (int i = 1; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("#count")) {
				count = true;
				continue;
			} else if (args[i].equalsIgnoreCase("#time")) {
				showTime = true;
				continue;
			} else if (args[i].equalsIgnoreCase("#bw")) {
				bw = true;
				continue;
			}
			String[] split = args[i].split(":");

			String token = split[0];
			switch (token.toLowerCase()) {
			case "a":
				token = "action";
				break;
			case "u":
				token = "user";
				break;
			case "t":
				token = "time";
				break;
			case "r":
				token = "radius";
				break;
			case "w":
				token = "world";
				break;
			}
			if (split.length != 2 || !validParams.contains(token)) {
				AuxProtectBungee.tell(sender,
						String.format(plugin.lang.translate("lookup-invalid-parameter"), args[i]));
				return true;
			}
			String param = split[1];
			if (token.equalsIgnoreCase("time") || token.equalsIgnoreCase("before") || token.equalsIgnoreCase("after")) {
				if (param.endsWith("e")) {
					long time = -1;
					try {
						time = Long.parseLong(param.substring(0, param.length() - 1));
					} catch (NumberFormatException e) {
					}
					if (time < 0) {
						AuxProtectBungee.tell(sender,
								String.format(plugin.lang.translate("lookup-invalid-parameter"), args[i]));
						return true;
					}
					param = time + "";
				} else {
					startTime = TimeUtil.convertTime(param);
					if (startTime < 0) {
						AuxProtectBungee.tell(sender,
								String.format(plugin.lang.translate("lookup-invalid-parameter"), args[i]));
						return true;
					}
					param = (System.currentTimeMillis() - startTime) + "";
				}
			}
			params.put(token, param.toLowerCase());
		}
		if (params.size() < 1) {
			AuxProtectBungee.tell(sender, plugin.lang.translate("purge-error-notenough"));
			return true;
		}
		if (bw) {
			String user = params.get("user");
			final String targetOld = params.get("target");
			String target = params.get("target");
			if (user == null) {
				user = "";
			}
			if (target == null) {
				target = "";
			}
			if (user.length() > 0) {
				if (targetOld != null && targetOld.length() > 0) {
					target += ",";
				}
				target += user;
			}
			if (targetOld != null && targetOld.length() > 0) {
				if (user.length() > 0) {
					user += ",";
				}
				user += targetOld;
			}
			if (user.length() > 0) {
				params.put("user", user);
			}
			if (target.length() > 0) {
				params.put("target", target);
			}
		}
		final boolean count_ = count;
		final boolean showTime_ = showTime;
		AuxProtectBungee.tell(sender, plugin.lang.translate("lookup-looking"));
		Runnable runnable = new Runnable() {

			@Override
			public void run() {
				// TODO: Sender compatibility
				ArrayList<DbEntry> results = null;
				try {
					results = plugin.getSqlManager().lookup(params, null, false);
				} catch (LookupException e) {
					AuxProtectBungee.tell(sender, e.errorMessage);
					return;
				}
				if (results == null || results.size() == 0) {
					AuxProtectBungee.tell(sender, plugin.lang.translate("lookup-noresults"));
					return;
				}
				if (count_) {
					AuxProtectBungee.tell(sender, String.format(plugin.lang.translate("lookup-count"), results.size()));
				} else {
					String uuid = "nonplayer";
					if (sender instanceof ProxiedPlayer) {
						uuid = ((ProxiedPlayer) sender).getUniqueId().toString();
					}
					Results result = new Results(plugin, results, sender, showTime_);
					result.showPage(1, 4);
					LookupCommand.this.results.put(uuid, result);
				}
			}
		};
		plugin.dbRunnable.scheduleLookup(runnable);
		return true;
	}

	public List<String> onTabComplete(CommandSender sender, String[] args) {
		List<String> possible = new ArrayList<>();
		String currentArg = args[args.length - 1];

		possible.add("radius:");
		possible.add("time:");
		possible.add("target:");
		possible.add("action:");
		possible.add("world:");
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
			/*
			 * if (MyPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
			 * possible.add("#pt"); }
			 */
			possible.add("#bw");
			possible.add("#time");
			possible.add("#count");
		}

		for (int i = 1; i < args.length - 1; i++) {
			String arg = args[i];
			if (!arg.contains(":"))
				continue;
			arg = arg.substring(0, arg.indexOf(":") + 1);
			possible.remove(arg);
		}

		List<String> output = new ArrayList<>();
		APCommand.copyPartialMatches(currentArg, possible, output);
		return output;
	}
}
