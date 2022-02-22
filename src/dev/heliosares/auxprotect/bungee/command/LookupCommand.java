package dev.heliosares.auxprotect.bungee.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.entity.Player;

import dev.heliosares.auxprotect.bungee.AuxProtectBungee;
import dev.heliosares.auxprotect.bungee.Results;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLManager.LookupException;
import dev.heliosares.auxprotect.utils.MyPermission;
import dev.heliosares.auxprotect.utils.PlayTimeSolver;
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
		Player player_ = null;
		if (sender instanceof Player) {
			player_ = (Player) sender;
		}
		final Player player = player_;
		if (args.length == 2) {
			int page = -1;
			int perpage = -1;
			boolean isPageLookup = false;
			boolean next = args[1].equalsIgnoreCase("next");
			boolean prev = args[1].equalsIgnoreCase("prev");
			boolean first = args[1].equalsIgnoreCase("first");
			boolean last = args[1].equalsIgnoreCase("last");
			if (!next && !prev && !first && !last) {
				if (args[1].contains(":")) {
					String[] split = args[1].split(":");
					try {
						page = Integer.parseInt(split[0]);
						perpage = Integer.parseInt(split[1]);
						isPageLookup = true;
					} catch (NumberFormatException e) {
					}
				} else {
					try {
						page = Integer.parseInt(args[1]);
						isPageLookup = true;
					} catch (NumberFormatException e) {
					}
				}
			}
			if (isPageLookup || first || last || next || prev) {
				Results result = null;
				String uuid = "nonplayer";
				if (sender instanceof Player) {
					uuid = ((Player) sender).getUniqueId().toString();
				}
				if (results.containsKey(uuid)) {
					result = results.get(uuid);
				}
				if (result == null) {
					AuxProtectBungee.tell(sender, plugin.lang.translate("lookup-no-results-selected"));
					return true;
				}
				if (perpage == -1) {
					perpage = result.perpage;
				}
				if (first) {
					page = 1;
				} else if (last) {
					page = result.getLastPage(result.perpage);
				} else if (next) {
					page = result.prevpage + 1;
				} else if (prev) {
					page = result.prevpage - 1;
				}
				if (perpage > 0) {
					if (perpage > 100) {
						perpage = 100;
					}
					result.showPage(page, perpage);
					return true;
				}
			}
		}

		HashMap<String, String> params = new HashMap<>();
		boolean count = false;
		boolean playtime = false;
		boolean bw = false;
		long startTime = 0;
		for (int i = 1; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("#count")) {
				count = true;
				continue;
			} else if (args[i].equalsIgnoreCase("#bw")) {
				bw = true;
				continue;
			} else if (args[i].equalsIgnoreCase("#pt")) {
				if (!MyPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
					AuxProtectBungee.tell(sender, plugin.translate("no-permission-flag"));
					return true;
				}
				playtime = true;
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
			if (token.equalsIgnoreCase("db")) {
				if (!MyPermission.ADMIN.hasPermission(sender)) {
					AuxProtectBungee.tell(sender, plugin.translate("no-permission"));
					return true;
				}
			}
			if (split.length != 2 || !validParams.contains(token)) {
				AuxProtectBungee.tell(sender, String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
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
								String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
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
		if (playtime) {
			if (params.containsKey("user")) {
				if (params.get("user").split(",").length > 1) {
					AuxProtectBungee.tell(sender, plugin.translate("lookup-playtime-toomanyusers"));
					return true;
				}
			} else {
				AuxProtectBungee.tell(sender, plugin.translate("lookup-playtime-nouser"));
				return true;
			}
			if (params.containsKey("action")) {
				params.remove("action");
				params.put("action", "session");
			}
		}
		final boolean count_ = count;
		final boolean playtime_ = playtime;
		final long startTime_ = startTime;
		AuxProtectBungee.tell(sender, plugin.translate("lookup-looking"));
		Runnable runnable = new Runnable() {

			@Override
			public void run() {
				ArrayList<DbEntry> results = null;
				try {
					results = plugin.getSqlManager().lookup(params, player != null ? player.getLocation() : null,
							false);
				} catch (LookupException e) {
					AuxProtectBungee.tell(sender, e.errorMessage);
					return;
				}
				if (results == null || results.size() == 0) {
					AuxProtectBungee.tell(sender, plugin.translate("lookup-noresults"));
					return;
				}
				if (count_) {
					AuxProtectBungee.tell(sender, String.format(plugin.translate("lookup-count"), results.size()));
				} else if (playtime_) {
					String users = params.get("user");
					if (users == null) {
						AuxProtectBungee.tell(sender, plugin.translate("playtime-nouser"));
						return;
					}
					if (users.contains(",")) {
						AuxProtectBungee.tell(sender, plugin.translate("playtime-toomanyusers"));
						return;
					}
					sender.sendMessage(
							PlayTimeSolver.solvePlaytime(results, (int) Math.round(startTime_ / (1000 * 3600)), users));
				} else {
					String uuid = "nonplayer";
					if (player != null) {
						uuid = player.getUniqueId().toString();
					}
					Results result = new Results(plugin, results, sender);
					result.showPage(1, 4);
					LookupCommand.this.results.put(uuid, result);
				}
			}
		};
		// plugin.dbRunnable.scheduleLookup(runnable);
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
