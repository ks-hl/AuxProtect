package dev.heliosares.auxprotect.command;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.SQLManager.LookupException;
import dev.heliosares.auxprotect.utils.MoneySolver;
import dev.heliosares.auxprotect.utils.MyPermission;
import dev.heliosares.auxprotect.utils.MySender;
import dev.heliosares.auxprotect.utils.PlayTimeSolver;
import dev.heliosares.auxprotect.utils.TimeUtil;
import dev.heliosares.auxprotect.utils.XraySolver;

public class LookupCommand {

	private final IAuxProtect plugin;

	private static final ArrayList<String> validParams;
	static final HashMap<String, Results> results;

	static {
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
		results = new HashMap<>();
	}

	public LookupCommand(IAuxProtect plugin) {
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
						String uuid = sender.getUniqueId().toString();
						if (results.containsKey(uuid)) {
							result = results.get(uuid);
						}
						if (result == null) {
							sender.sendMessage(plugin.translate("lookup-no-results-selected"));
							return;
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
							return;
						} else {
							result.showPage(page);
							return;
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
							return;
						}
						playtime = true;
						continue;
					} else if (args[i].equalsIgnoreCase("#money")) {
						if (!MyPermission.LOOKUP_MONEY.hasPermission(sender)) {
							sender.sendMessage(plugin.translate("no-permission-flag"));
							return;
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
							return;
						}
					}
					if (split.length != 2 || !validParams.contains(token)) {
						sender.sendMessage(String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
						return;
					}
					String param = split[1];
					if (token.equalsIgnoreCase("time") || token.equalsIgnoreCase("before")
							|| token.equalsIgnoreCase("after")) {
						if (param.endsWith("e")) {
							long time = -1;
							try {
								time = Long.parseLong(param.substring(0, param.length() - 1));
							} catch (NumberFormatException e) {
							}
							if (time < 0) {
								sender.sendMessage(
										String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
								return;
							}
							param = time + "";
						} else {
							startTime = TimeUtil.convertTime(param);
							if (startTime < 0) {
								sender.sendMessage(
										String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
								return;
							}
							param = (System.currentTimeMillis() - startTime) + "";
						}
					}
					params.put(token, param.toLowerCase());
				}
				if (params.size() < 1) {
					sender.sendMessage(plugin.translate("purge-error-notenough"));
					return;
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
							return;
						}
					} else {
						sender.sendMessage(plugin.translate("lookup-playtime-nouser"));
						return;
					}
					if (params.containsKey("action")) {
						params.remove("action");
						params.put("action", "session");
					}
				}
				sender.sendMessage(plugin.translate("lookup-looking"));
				ArrayList<DbEntry> rs = null;
				try {
					if (sender.isBungee()) {
						rs = plugin.getSqlManager().lookup(params, null, false);
					} else {
						Location location = null;
						if (sender.getSender() instanceof Player) {
							location = ((Player) sender.getSender()).getLocation();
						}
						rs = plugin.getSqlManager().lookup(params, location, false);
					}
				} catch (LookupException e) {
					sender.sendMessage(e.errorMessage);
					return;
				}
				if (rs == null || rs.size() == 0) {
					sender.sendMessage(plugin.translate("lookup-noresults"));
					return;
				}
				if (count) {
					sender.sendMessage(String.format(plugin.translate("lookup-count"), rs.size()));
					double totalMoney = 0;
					int dropcount = 0;
					int pickupcount = 0;
					for (DbEntry entry : rs) {
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
								}
							}
						}
						if (entry.getAction() == EntryAction.AHBUY) {
							String[] parts = entry.getData().split(" ");
							try {
								double each = Double.parseDouble(parts[parts.length - 1].substring(1));
								totalMoney += each;
							} catch (Exception ignored) {
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
					if (totalMoney != 0 && plugin instanceof AuxProtect) {
						sender.sendMessage("§9" + ((AuxProtect) plugin).formatMoney(totalMoney));
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
				} else if (playtime) {
					String users = params.get("user");
					if (users == null) {
						sender.sendMessage(plugin.translate("playtime-nouser"));
						return;
					}
					if (users.contains(",")) {
						sender.sendMessage(plugin.translate("playtime-toomanyusers"));
						return;
					}
					sender.sendMessage(
							PlayTimeSolver.solvePlaytime(rs, (int) Math.round(startTime / (1000 * 3600)), users));
				} else if (xray) {
					sender.sendMessage(XraySolver.solvePlaytime(rs, plugin));
				} else if (money && !sender.isBungee()) {
					String users = params.get("user");
					if (users == null) {
						sender.sendMessage(plugin.translate("playtime-nouser"));
						return;
					}
					if (users.contains(",")) {
						sender.sendMessage(plugin.translate("playtime-toomanyusers"));
						return;
					}
					if (sender.getSender() instanceof Player) {
						MoneySolver.showMoney(plugin, (Player) sender.getSender(), rs,
								(int) Math.round(startTime / (1000 * 3600)), users);
					} else {
						return;
					}

				} else {
					String uuid = sender.getUniqueId().toString();
					Results result = new Results(plugin, rs, sender, sender.isBungee() ? "apb" : "ap");
					result.showPage(1, 4);
					results.put(uuid, result);
				}
			}
		};
		plugin.runAsync(run);
	}
}
