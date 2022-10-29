package dev.heliosares.auxprotect.core.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.core.Parameters.Flag;
import dev.heliosares.auxprotect.database.ActivityResults;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.LookupManager;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.MoneySolver;
import dev.heliosares.auxprotect.utils.PlayTimeSolver;
import dev.heliosares.auxprotect.utils.RetentionSolver;
import dev.heliosares.auxprotect.utils.XraySolver;

public class LookupCommand extends Command {

	public LookupCommand(IAuxProtect plugin) {
		super(plugin, "lookup", APPermission.LOOKUP, "l");
	}

	static final HashMap<String, Results> results = new HashMap<>();

	public void onCommand(SenderAdapter sender, String label, String[] args) {
		if (args.length < 2) {
			sender.sendLang("lookup-invalid-syntax");
			return;
		}
		if (!plugin.getSqlManager().isConnected()) {
			sender.sendLang("database-busy");
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
							sender.sendLang("lookup-no-results-selected");
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

				Parameters params = null;
				try {
					params = Parameters.parse(sender, args);
				} catch (Exception e) {
					sender.sendMessageRaw(e.getMessage());
					return;
				}

				sender.sendLang("lookup-looking");

				int count = 0;
				try {
					count = plugin.getSqlManager().getLookupManager().count(params);
				} catch (LookupManager.LookupException e) {
					sender.sendMessageRaw(e.getMessage());
					return;
				}
				if (params.getFlags().contains(Flag.COUNT_ONLY)) {
					sender.sendLang("lookup-count", count);
					return;
				}
				if (count == 0) {
					sender.sendLang("lookup-noresults");
					return;
				}
				if (count > SQLManager.MAX_LOOKUP_SIZE) {
					sender.sendLang("lookup-toomany", count, SQLManager.MAX_LOOKUP_SIZE);
					return;
				}

				ArrayList<DbEntry> rs = null;
				try {
					rs = plugin.getSqlManager().getLookupManager().lookup(params);
				} catch (LookupManager.LookupException e) {
					sender.sendMessageRaw(e.getMessage());
					return;
				}
				if (rs == null || rs.size() == 0) {
					sender.sendLang("lookup-noresults");
					return;
				}
				if (params.getFlags().contains(Flag.COUNT)) {
					sender.sendLang("lookup-count", rs.size());
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
						} else if (entry.getAction().equals(EntryAction.AUCTIONBUY)) {
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
						sender.sendMessageRaw("�fTotal Money: �9" + (negative ? "-" : "")
								+ ((AuxProtectSpigot) plugin).formatMoney(totalMoney));
					}
					if (totalExp != 0) {
						sender.sendMessageRaw("�fTotal Experience: �9" + Math.round(totalExp * 100f) / 100f);
					}
					String msg = "";
					if (pickupcount > 0) {
						msg += "�fPicked up: �9" + pickupcount + "�7, ";
					}
					if (dropcount > 0) {
						msg += "�fDropped: �9" + dropcount + "�7, ";
					}
					if (pickupcount > 0 && dropcount > 0) {
						msg += "�fNet: �9" + (pickupcount - dropcount);
					}
					if (msg.length() > 0) {
						sender.sendMessageRaw(msg);
					}
					return;
				}
//				else if (params.getFlags().contains(Flag.COUNT1)) {
//					sender.sendMessage(String.format(plugin.translate("lookup-count"), rs.size()));
//					HashMap<String, Double> values = new HashMap<>();
//					HashMap<String, Integer> qtys = new HashMap<>();
//					for (DbEntry entry : rs) {
//						if (entry.getAction().equals(EntryAction.SHOP) && !entry.getState()) {
//							String[] parts = entry.getData().split(", ");
//							if (parts.length >= 3) {
//								try {
//									String valueStr = parts[1];
//									double value = -1;
//									int qty = 1;
//									if (!valueStr.contains(" each")
//											|| entry.getTime() < 1648304226492L) { /* Fix for malformed entries */
//										valueStr = valueStr.replaceAll(" each", "");
//										value = Double.parseDouble(valueStr.substring(1));
//									} else {
//										double each = Double.parseDouble(valueStr.split(" ")[0].substring(1));
//										qty = Integer.parseInt(parts[2].split(" ")[1]);
//										value = each * qty;
//									}
//									if (value > 0) {
//										double currentvalue = 0;
//										if (values.containsKey(entry.getTarget())) {
//											currentvalue = values.get(entry.getTarget());
//										}
//										values.put(entry.getTarget(), value + currentvalue);
//
//										int currentqty = 0;
//										if (qtys.containsKey(entry.getTarget())) {
//											currentqty = qtys.get(entry.getTarget());
//										}
//										qtys.put(entry.getTarget(), qty + currentqty);
//									}
//								} catch (Exception ignored) {
//									if (plugin.getDebug() > 0) {
//										plugin.print(ignored);
//									}
//								}
//							}
//						}
//					}
//					Map<String, Double> sortedMap = values.entrySet().stream().sorted(Entry.comparingByValue()).collect(
//							Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
//					int limit = 10;
//					Object[] objmap = sortedMap.entrySet().toArray();
//					for (int i = sortedMap.size() - 1; i >= sortedMap.size() - 1 - limit; i--) {
//						@SuppressWarnings("unchecked")
//						Entry<String, Double> entry = (Entry<String, Double>) objmap[i];
//						sender.sendMessage(entry.getKey() + ": $" + Math.round(entry.getValue() * 100) / 100.0 + " (x"
//								+ qtys.get(entry.getKey()) + ")");
//					}
//					return;
//				}
				else if (params.getFlags().contains(Flag.PT)) {
					List<String> users = params.getUsers();
					if (users.size() == 0) {
						sender.sendLang("playtime-nouser");
						return;
					}
					if (users.size() > 1) {
						sender.sendLang("playtime-toomanyusers");
						return;
					}
					sender.sendMessage(PlayTimeSolver.solvePlaytime(rs, params.getAfter(),
							(int) Math.round((params.time_created - params.getAfter()) / (1000 * 3600)), users.get(0)));
					return;
				} else if (params.getFlags().contains(Flag.ACTIVITY)) {
					String uuid = sender.getUniqueId().toString();
					Results result = new ActivityResults(plugin, rs, sender, params);
					result.showPage(result.getNumPages(4), 4);
					results.put(uuid, result);
					return;
				} else if (params.getFlags().contains(Flag.XRAY)) {
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
				} else if (params.getFlags().contains(Flag.MONEY) && sender.getPlatform() == PlatformType.SPIGOT) {
					List<String> users = params.getUsers();
					if (users.size() == 0) {
						sender.sendLang("playtime-nouser");
						return;
					}
					if (users.size() > 1) {
						sender.sendLang("playtime-toomanyusers");
						return;
					}
					if (sender.getSender() instanceof org.bukkit.entity.Player player) {
						MoneySolver.showMoney(plugin, player, rs, (int) Math.round(params.getAfter() / (1000 * 3600)),
								users.get(0));
					}
					return;
				} else if (params.getFlags().contains(Flag.RETENTION)) {
					RetentionSolver.showRetention(plugin, sender, rs, params.getAfter(), params.getBefore());
					return;
				}
				String uuid = sender.getUniqueId().toString();
				Results result = new Results(plugin, rs, sender, params);
				result.showPage(1, 4);
				results.put(uuid, result);

				if (params.getFlags().contains(Flag.XRAY)) {
					sender.sendMessage(XraySolver.solve(rs, plugin));
				}
			}
		};
		plugin.runAsync(run);
	}

	@Override
	public boolean exists() {
		return true;
	}

	@Override
	public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
		return onTabCompleteStatic(plugin, sender, label, args);
	}

	public static List<String> onTabCompleteStatic(IAuxProtect plugin, SenderAdapter sender, String label,
			String[] args) {
		List<String> possible = new ArrayList<>();
		String currentArg = args[args.length - 1];
		boolean lookup = args[0].equalsIgnoreCase("lookup") || args[0].equalsIgnoreCase("l");
		boolean watch = args[0].equalsIgnoreCase("watch") || args[0].equalsIgnoreCase("w");
		if (lookup && !APPermission.LOOKUP.hasPermission(sender)) {
			return null;
		}
		if (watch && !APPermission.WATCH.hasPermission(sender)) {
			return null;
		}
		if (args.length == 2) {
			if (lookup) {
				possible.add("next");
				possible.add("prev");
				possible.add("first");
				possible.add("last");
			}
			if (watch) {
				possible.add("remove");
				possible.add("clear");
				possible.add("list");
			}
		}

		possible.add("radius:");
		possible.add("time:");
		possible.add("target:");
		possible.add("action:");
		possible.add("world:");
		possible.add("user:");
		possible.add("data:");
		possible.add("before:");
		possible.add("after:");

		if (currentArg.startsWith("action:") || currentArg.startsWith("a:")) {
			String action = currentArg.split(":")[0] + ":";
			for (EntryAction eaction : EntryAction.values()) {
				if (eaction.exists() && eaction.isEnabled() && eaction.hasPermission(sender)) {
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
			int cutIndex = 0;
			if (currentArg.contains(",")) {
				cutIndex = currentArg.lastIndexOf(",");
			} else {
				cutIndex = currentArg.indexOf(":");

			}
			String user = currentArg.substring(0, cutIndex + 1);
			for (Player player : Bukkit.getOnlinePlayers()) {
				possible.add(user + player.getName());
			}
			for (String username : plugin.getSqlManager().getCachedUsernames()) {
				possible.add(user + username);
			}
			for (EntityType et : EntityType.values()) {
				possible.add(user + "#" + et.toString().toLowerCase());
			}
			possible.add(user + "#env");
		}
		if (currentArg.startsWith("target:")) {
			for (Material material : Material.values()) {
				possible.add("target:" + material.toString().toLowerCase());
			}
		}
		if (APPermission.ADMIN.hasPermission(sender)) {
			possible.add("db:");
			if (currentArg.startsWith("db:")) {
				for (Table table : Table.values()) {
					possible.add("db:" + table.toString());
				}
			}
		}
		if (currentArg.matches("(t(ime)?|before|after):\\d+m?")) {
			possible.add(currentArg + "ms");
			possible.add(currentArg + "s");
			possible.add(currentArg + "m");
			possible.add(currentArg + "h");
			possible.add(currentArg + "d");
			possible.add(currentArg + "w");
		}
		if (currentArg.startsWith("world:")) {
			for (World world : Bukkit.getWorlds()) {
				possible.add("world:" + world.getName());
			}
		}
		if (currentArg.startsWith("rat")) {
			possible.add("rating:");
			if (currentArg.matches("rating:-?")) {
				for (int i = -2; i <= 3; i++) {
					possible.add("rating:" + i);
				}
			}
		}

		for (int i = 1; i < args.length - 1; i++) {
			String arg = args[i];
			if (!arg.contains(":"))
				continue;
			arg = arg.substring(0, arg.indexOf(":") + 1);
			possible.remove(arg);
		}

		if (currentArg.startsWith("#")) {
			for (Flag flag : Flag.values()) {
				if (flag.hasPermission(sender)) {
					possible.add("#" + flag.toString().toLowerCase());
				}
			}
		}

		return possible;
	}
}