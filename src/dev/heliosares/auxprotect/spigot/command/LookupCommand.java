package dev.heliosares.auxprotect.spigot.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.MySender;
import dev.heliosares.auxprotect.database.ActivityResults;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.SQLManager.LookupException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.utils.MoneySolver;
import dev.heliosares.auxprotect.utils.PlayTimeSolver;
import dev.heliosares.auxprotect.utils.RetentionSolver;
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
		validParams.add("data");
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
							page = result.getNumPages(result.perpage);
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
				boolean count1 = false;
				boolean playtime = false;
				boolean xray = false;
				boolean bw = false;
				boolean money = false;
				boolean activity = false;
				boolean retention = false;
				long startTime = 0;
				long endTime = System.currentTimeMillis();
				for (int i = 1; i < args.length; i++) {
					if (args[i].equalsIgnoreCase("#count")) {
						count = true;
						continue;
					} else if (args[i].equalsIgnoreCase("#count1") && plugin.getAPConfig().isPrivate()) {
						count1 = true;
						continue;
					} else if (args[i].equalsIgnoreCase("#xray")) {
						xray = true;
						continue;
					} else if (args[i].equalsIgnoreCase("#bw")) {
						bw = true;
						continue;
					} else if (args[i].equalsIgnoreCase("#activity")) {
						if (!APPermission.LOOKUP_ACTIVITY.hasPermission(sender)) {
							sender.sendMessage(plugin.translate("no-permission-flag"));
							return;
						}
						activity = true;
						continue;
					} else if (args[i].equalsIgnoreCase("#pt")) {
						if (!APPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
							sender.sendMessage(plugin.translate("no-permission-flag"));
							return;
						}
						playtime = true;
						continue;
					} else if (args[i].equalsIgnoreCase("#money")) {
						if (!APPermission.LOOKUP_MONEY.hasPermission(sender)) {
							sender.sendMessage(plugin.translate("no-permission-flag"));
							return;
						}
						money = true;
						continue;
					} else if (args[i].equalsIgnoreCase("#retention")) {
						if (!APPermission.LOOKUP_RETENTION.hasPermission(sender)) {
							sender.sendMessage(plugin.translate("no-permission-flag"));
							return;
						}
						retention = true;
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
						if (!APPermission.ADMIN.hasPermission(sender)) {
							sender.sendMessage(plugin.translate("no-permission"));
							return;
						}
					}
					if (split.length != 2 || !validParams.contains(token)) {
						sender.sendMessage(String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
						return;
					}
					String param = split[1];
					if (token.equalsIgnoreCase("action")) {
						for (String actionStr : param.split(",")) {
							EntryAction action = EntryAction.getAction(actionStr);
							if (action == null) {
								continue;
							}
							APPermission actionNode = APPermission.LOOKUP_ACTION.dot(action.toString().toLowerCase());
							if (!actionNode.hasPermission(sender)) {
								sender.sendMessage(String.format(plugin.translate("lookup-action-perm") + " (%s)",
										param, actionNode.node));
								return;
							}
						}
					} else if (token.equalsIgnoreCase("time") || token.equalsIgnoreCase("before")
							|| token.equalsIgnoreCase("after")) {
						boolean plusminus = param.contains("+-");
						boolean minus = param.contains("-");
						if (minus) { // || plusminus unnecessary because they both have '-'
							String[] range = param.split("\\+?-");
							if (range.length != 2) {
								sender.sendMessage(
										String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
								return;
							}

							long time1;
							long time2;
							try {
								time1 = TimeUtil.stringToMillis(range[0]);
								time2 = TimeUtil.stringToMillis(range[1]);
							} catch (NumberFormatException e) {
								sender.sendMessage(
										String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
								return;
							}

							if (!range[0].endsWith("e")) {
								time1 = System.currentTimeMillis() - time1;
							}
							if (!range[1].endsWith("e")) {
								time2 = System.currentTimeMillis() - time2;
							}

							if (plusminus) {
								startTime = time1 - time2;
								endTime = time1 + time2;
							} else {
								startTime = Math.min(time1, time2);
								endTime = Math.max(time1, time2);
							}

							params.put("before", endTime + "");
							params.put("after", startTime + "");
							continue;
						}
						if (token.equalsIgnoreCase("time") && param.endsWith("e")) {
							params.put(token, param.toLowerCase());
							continue;
						}
						long time;
						try {
							time = TimeUtil.stringToMillis(param);
							if (time < 0) {
								sender.sendMessage(
										String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
								return;
							}
						} catch (NumberFormatException e) {
							sender.sendMessage(String.format(plugin.translate("lookup-invalid-parameter"), args[i]));
							return;
						}
						time = System.currentTimeMillis() - time;

						param = time + "";
						if (token.equalsIgnoreCase("time") || token.equalsIgnoreCase("after")) {
							startTime = time;
						} else {
							endTime = time;
						}
					}
					params.put(token, param.toLowerCase());
				}
				if (params.size() < 1) {
					sender.sendMessage(plugin.translate("lookup-invalid-notenough"));
					return;
				}
				if (!params.containsKey("action")) {
					for (EntryAction action : EntryAction.values()) {
						if (action.getTable() == Table.AUXPROTECT_MAIN && !APPermission.LOOKUP_ACTION
								.dot(action.toString().toLowerCase()).hasPermission(sender)) {
							sender.sendMessage(plugin.translate("lookup-action-none"));
							return;
						}
					}
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
				if (playtime || activity) {
					if (params.containsKey("user")) {
						if (params.get("user").split(",").length > 1) {
							sender.sendMessage(plugin.translate("lookup-playtime-toomanyusers"));
							return;
						}
					} else {
						sender.sendMessage(plugin.translate("lookup-playtime-nouser"));
						return;
					}
				}
				if (playtime) {
					if (params.containsKey("action")) {
						params.remove("action");
					}
					params.put("action", "session");
				}
				if (activity) {
					if (params.containsKey("action")) {
						params.remove("action");
					}
					params.put("action", "activity");
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
					double totalExp = 0;
					int dropcount = 0;
					int pickupcount = 0;
					for (DbEntry entry : rs) {
						if (entry.getAction().equals(EntryAction.SHOP)) {
							String[] parts = entry.getData().split(", ");
							if (parts.length >= 3) {
								try {
									String valueStr = parts[1];
									double value = -1;
									if (!valueStr.contains(" each")
											|| entry.getTime() < 1648304226492L) { /* Fix for malformed entries */
										valueStr = valueStr.replaceAll(" each", "");
										value = Double.parseDouble(valueStr.substring(1));
									} else {
										double each = Double.parseDouble(valueStr.split(" ")[0].substring(1));
										int qty = Integer.parseInt(parts[2].split(" ")[1]);
										value = each * qty;
									}
									if (value > 0) {
										if (entry.getState()) {
											value *= -1;
										}
										totalMoney += value;
									}
								} catch (Exception ignored) {
									if (plugin.getDebug() > 0) {
										plugin.print(ignored);
									}
								}
							}
						} else if (entry.getAction().equals(EntryAction.JOBS)) {
							char type = entry.getData().charAt(0);
							double value = Double.parseDouble(entry.getData().substring(1));
							if (type == 'x') {
								totalExp += value;
							} else if (type == '$') {
								totalMoney += value;
							}
						} else if (entry.getAction().equals(EntryAction.AHBUY)) {
							String[] parts = entry.getData().split(" ");
							try {
								double each = Double.parseDouble(parts[parts.length - 1].substring(1));
								totalMoney += each;
							} catch (Exception ignored) {
							}
						} else if (entry.getAction().equals(EntryAction.DROP)
								|| entry.getAction().equals(EntryAction.PICKUP)) {
							int quantity = -1;
							try {
								quantity = Integer.parseInt(entry.getData().substring(1));
							} catch (Exception ignored) {

							}
							if (quantity > 0) {
								if (entry.getAction().equals(EntryAction.DROP)) {
									dropcount += quantity;
								} else {
									pickupcount += quantity;
								}
							}
						}
					}
					if (totalMoney != 0 && plugin instanceof AuxProtectSpigot) {
						boolean negative = totalMoney < 0;
						totalMoney = Math.abs(totalMoney);
						sender.sendMessage("§fTotal Money: §9" + (negative ? "-" : "")
								+ ((AuxProtectSpigot) plugin).formatMoney(totalMoney));
					}
					if (totalExp != 0) {
						sender.sendMessage("§fTotal Experience: §9" + Math.round(totalExp * 100f) / 100f);
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
					return;
				} else if (count1) {
					sender.sendMessage(String.format(plugin.translate("lookup-count"), rs.size()));
					HashMap<String, Double> values = new HashMap<>();
					HashMap<String, Integer> qtys = new HashMap<>();
					for (DbEntry entry : rs) {
						if (entry.getAction().equals(EntryAction.SHOP) && !entry.getState()) {
							String[] parts = entry.getData().split(", ");
							if (parts.length >= 3) {
								try {
									String valueStr = parts[1];
									double value = -1;
									int qty = 1;
									if (!valueStr.contains(" each")
											|| entry.getTime() < 1648304226492L) { /* Fix for malformed entries */
										valueStr = valueStr.replaceAll(" each", "");
										value = Double.parseDouble(valueStr.substring(1));
									} else {
										double each = Double.parseDouble(valueStr.split(" ")[0].substring(1));
										qty = Integer.parseInt(parts[2].split(" ")[1]);
										value = each * qty;
									}
									if (value > 0) {
										double currentvalue = 0;
										if (values.containsKey(entry.getTarget())) {
											currentvalue = values.get(entry.getTarget());
										}
										values.put(entry.getTarget(), value + currentvalue);

										int currentqty = 0;
										if (qtys.containsKey(entry.getTarget())) {
											currentqty = qtys.get(entry.getTarget());
										}
										qtys.put(entry.getTarget(), qty + currentqty);
									}
								} catch (Exception ignored) {
									if (plugin.getDebug() > 0) {
										plugin.print(ignored);
									}
								}
							}
						}
					}
					Map<String, Double> sortedMap = values.entrySet().stream().sorted(Entry.comparingByValue()).collect(
							Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
					int limit = 10;
					Object[] objmap = sortedMap.entrySet().toArray();
					for (int i = sortedMap.size() - 1; i >= sortedMap.size() - 1 - limit; i--) {
						@SuppressWarnings("unchecked")
						Entry<String, Double> entry = (Entry<String, Double>) objmap[i];
						sender.sendMessage(entry.getKey() + ": $" + Math.round(entry.getValue() * 100) / 100.0 + " (x"
								+ qtys.get(entry.getKey()) + ")");
					}
					return;
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
					sender.sendMessage(PlayTimeSolver.solvePlaytime(rs, startTime,
							(int) Math.round((endTime - startTime) / (1000 * 3600)), users));
					return;
				} else if (activity) {
					String uuid = sender.getUniqueId().toString();
					Results result = new ActivityResults(plugin, rs, sender);
					result.showPage(result.getNumPages(4), 4);
					results.put(uuid, result);
					return;
				} else if (xray) {
					Set<Integer> users = new HashSet<>();
					for (DbEntry entry : rs) {
						users.add(entry.getUid());
					}
					if (users.size() > 1) {
						sender.sendMessage(XraySolver.solve(rs, plugin));
						return;
					}
					Iterator<DbEntry> it = rs.iterator();
					while (it.hasNext()) {
						XrayEntry entry = (XrayEntry) it.next();
						if (entry.getRating() < 0) {
							it.remove();
						}
					}
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
					}
					return;
				} else if (retention) {
					RetentionSolver.showRetention(plugin, sender, rs, startTime, endTime);
					return;
				}
				String uuid = sender.getUniqueId().toString();
				Results result = new Results(plugin, rs, sender);
				result.showPage(1, 4);
				results.put(uuid, result);

				if (xray) {
					sender.sendMessage(XraySolver.solve(rs, plugin));
				}
			}
		};
		plugin.runAsync(run);
	}
}
