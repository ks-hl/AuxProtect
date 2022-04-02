package dev.heliosares.auxprotect.command;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.SQLManager.LookupException;
import dev.heliosares.auxprotect.utils.MoneySolver;
import dev.heliosares.auxprotect.utils.MyPermission;
import dev.heliosares.auxprotect.utils.PlayTimeSolver;
import dev.heliosares.auxprotect.utils.TimeUtil;
import dev.heliosares.auxprotect.utils.XraySolver;

public class LookupCommand implements CommandExecutor {

	private AuxProtect plugin;

	private ArrayList<String> validParams;

	public LookupCommand(AuxProtect plugin) {
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
		validParams.add("db");
	}

	HashMap<String, Results> results;

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
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
					sender.sendMessage(plugin.translate("lookup-no-results-selected"));
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
				} else {
					result.showPage(page);
					return true;
				}
			}
		}

		HashMap<String, String> params = new HashMap<>();
		boolean count = false;
		boolean playtime = false;
		boolean xray = false;
		boolean bw = false;
		boolean money = false;
		long startTime = 0;
		for (int i = 1; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("#count")) {
				count = true;
				continue;
			} else if (args[i].equalsIgnoreCase("#xray")) {
				xray = true;
				continue;
			} else if (args[i].equalsIgnoreCase("#bw")) {
				bw = true;
				continue;
			} else if (args[i].equalsIgnoreCase("#pt")) {
				if (!MyPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission-flag"));
					return true;
				}
				playtime = true;
				continue;
			} else if (args[i].equalsIgnoreCase("#money")) {
				if (!MyPermission.LOOKUP_MONEY.hasPermission(sender)) {
					sender.sendMessage(plugin.translate("no-permission-flag"));
					return true;
				}
				money = true;
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
					sender.sendMessage(plugin.translate("no-permission"));
					return true;
				}
			}
			if (split.length != 2 || !validParams.contains(token)) {
				sender.sendMessage(String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
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
						sender.sendMessage(String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
						return true;
					}
					param = time + "";
				} else {
					startTime = TimeUtil.convertTime(param);
					if (startTime < 0) {
						sender.sendMessage(String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
						return true;
					}
					param = (System.currentTimeMillis() - startTime) + "";
				}
			}
			params.put(token, param.toLowerCase());
		}
		if (params.size() < 1) {
			sender.sendMessage(plugin.translate("purge-error-notenough"));
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
					sender.sendMessage(plugin.translate("lookup-playtime-toomanyusers"));
					return true;
				}
			} else {
				sender.sendMessage(plugin.translate("lookup-playtime-nouser"));
				return true;
			}
			if (params.containsKey("action")) {
				params.remove("action");
				params.put("action", "session");
			}
		}
		final boolean count_ = count;
		final boolean playtime_ = playtime;
		final boolean xray_ = xray;
		final long startTime_ = startTime;
		final boolean money_ = money;
		sender.sendMessage(plugin.translate("lookup-looking"));
		BukkitRunnable runnable = new BukkitRunnable() {

			@Override
			public void run() {
				ArrayList<DbEntry> results = null;
				try {
					results = plugin.getSqlManager().lookup(params, player != null ? player.getLocation() : null,
							false);
				} catch (LookupException e) {
					sender.sendMessage(e.errorMessage);
					return;
				}
				if (results == null || results.size() == 0) {
					sender.sendMessage(plugin.translate("lookup-noresults"));
					return;
				}
				if (count_) {
					sender.sendMessage(String.format(plugin.translate("lookup-count"), results.size()));
					double totalMoney = 0;
					int dropcount = 0;
					int pickupcount = 0;
					for (DbEntry entry : results) {
						if (entry.getAction() == EntryAction.SHOP) {
							String[] parts = entry.getData().split(", ");
							if (parts.length >= 3) {
								try {
									double each = Double.parseDouble(parts[1].split(" ")[0].substring(1));
									int qty = Integer.parseInt(parts[2].split(" ")[1]);
									if (qty > 0) {
										if (entry.getState()) {
											each *= -1;
										}
										totalMoney += each * qty;
									}
								} catch (Exception ignored) {
									if (plugin.debug >= 3) {
										ignored.printStackTrace();
									}
								}
							}
						}
						if (entry.getAction() == EntryAction.AHBUY) {
							String[] parts = entry.getData().split(" ");
							try {
								double each = Double.parseDouble(parts[parts.length - 1].substring(1));
								totalMoney += each;
							} catch (Exception ignored) {
								if (plugin.debug >= 3) {
									ignored.printStackTrace();
								}
							}
						}
						if (entry.getAction() == EntryAction.DROP || entry.getAction() == EntryAction.PICKUP) {
							int quantity = -1;
							try {
								quantity = Integer.parseInt(entry.getData().substring(1));
							} catch (Exception ignored) {

							}
							if (quantity > 0) {
								if (entry.getAction() == EntryAction.DROP) {
									dropcount += quantity;
								} else {
									pickupcount += quantity;
								}
							}
						}
					}
					if (totalMoney != 0) {
						sender.sendMessage("§9" + plugin.formatMoney(totalMoney));
					}
					String msg = "";
					if (pickupcount > 0) {
						msg += "§fPicked up: §9" + pickupcount + "§7, ";
					}
					if (dropcount > 0) {
						msg += "§fDropped: §9" + dropcount + "§7, ";
					}
					if (pickupcount > 0 && dropcount > 0) {
						msg += "§fNet: §9" + (pickupcount - dropcount);
					}
					if (msg.length() > 0) {
						sender.sendMessage(msg);
					}
				} else if (playtime_) {
					String users = params.get("user");
					if (users == null) {
						sender.sendMessage(plugin.translate("playtime-nouser"));
						return;
					}
					if (users.contains(",")) {
						sender.sendMessage(plugin.translate("playtime-toomanyusers"));
						return;
					}
					sender.spigot().sendMessage(
							PlayTimeSolver.solvePlaytime(results, (int) Math.round(startTime_ / (1000 * 3600)), users));
				} else if (xray_) {
					sender.spigot().sendMessage(XraySolver.solvePlaytime(results, plugin));
				} else if (money_) {
					String users = params.get("user");
					if (users == null) {
						sender.sendMessage(plugin.translate("playtime-nouser"));
						return;
					}
					if (users.contains(",")) {
						sender.sendMessage(plugin.translate("playtime-toomanyusers"));
						return;
					}
					@SuppressWarnings("deprecation")
					OfflinePlayer targetUser = Bukkit.getOfflinePlayer(users);
					if (targetUser == null) {
						sender.sendMessage(plugin.translate("playtime-nouser"));
						return;
					}

					MoneySolver.showMoney(plugin, player, results, (int) Math.round(startTime_ / (1000 * 3600)),
							targetUser.getName());
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
		runnable.runTaskAsynchronously(plugin);
		return true;
	}
}
