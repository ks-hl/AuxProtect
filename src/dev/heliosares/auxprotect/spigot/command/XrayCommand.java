package dev.heliosares.auxprotect.spigot.command;

import java.lang.reflect.Method;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import dev.heliosares.auxprotect.core.MyPermission;
import dev.heliosares.auxprotect.core.MySender;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.database.XrayResults;
import dev.heliosares.auxprotect.database.SQLManager.LookupException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.TimeUtil;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI.ParseResult;
import net.coreprotect.database.Database;
import net.coreprotect.database.Lookup;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class XrayCommand implements CommandExecutor {

	private AuxProtectSpigot plugin;

	private ArrayList<String> validParams;

	public XrayCommand(AuxProtectSpigot plugin) {
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

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (plugin.getServer().getPluginManager().getPlugin("CoreProtect") == null) {
			sender.sendMessage("§cCoreProtect required for this command.");
			return true;
		}
		if (args.length < 2) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return true;
		}
		Player player_ = null;
		if (sender instanceof Player) {
			player_ = (Player) sender;
		}
		final Player player = player_;
		if (args.length >= 2) {
			if (args[1].equalsIgnoreCase("page")) {
				int page = -1;
				int perpage = 4;
				try {
					page = Integer.parseInt(args[2]);
				} catch (NumberFormatException e) {

				}
				if (page > 0) {
					if (perpage > 99)
						perpage = 99;
					String uuid = "nonplayer";
					if (sender instanceof Player) {
						uuid = ((Player) sender).getUniqueId().toString();
					}
					if (results.containsKey(uuid)) {
						results.get(uuid).showPage(page, perpage);
						return true;
					} else {
						sender.sendMessage(plugin.translate("lookup-no-results-selected"));
						return true;
					}
				}
			} else if (args[1].equalsIgnoreCase("rate")) {
				String uuid = "nonplayer";
				if (player != null) {
					uuid = player.getUniqueId().toString();
				}
				XrayResults results_ = (XrayResults) results.get(uuid);
				if (args.length < 3) {
					sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
					return true;
				}
				int entryIndex = -1;
				int rating = -1;
				if (results_ == null) {
					sender.sendMessage(plugin.translate("lookup-no-results-selected"));
					return true;
				}
				entryIndex = results_.getPage() - 1;
				try {
					if (args.length >= 4) {
						entryIndex = Integer.parseInt(args[3]);
					}
				} catch (NumberFormatException e) {
				}
				try {
					rating = Integer.parseInt(args[2]);
				} catch (NumberFormatException e) {
				}
				if (rating < 0) {
					sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
					return true;
				}
				final int entryIndex_ = entryIndex;
				final int rating_ = rating;
				new BukkitRunnable() {
					@Override
					public void run() {

						HashMap<String, String> params = new HashMap<>();
						params.put("action", EntryAction.XRAYCHECK.toString().toLowerCase());
						params.put("radius", "4");
						DbEntry en = results_.getEntry(entryIndex_);
						if (en == null) {
							sender.sendMessage(plugin.translate("lookup-no-results-selected"));
							return;
						}

						ArrayList<DbEntry> localHits = null;

						World world = Bukkit.getWorld(en.world);
						if (world == null) {
							return;
						}

						// boolean quick = args.length >= 5 && args[4].equalsIgnoreCase("quick");
						// if (!quick) {
						try {
							localHits = plugin.getSqlManager().lookup(params, new Location(world, en.x, en.y, en.z),
									true);
						} catch (LookupException e) {
							sender.sendMessage(e.errorMessage);
							return;
						}
						// }

						if (localHits != null/* || quick */) {
							if (/* !quick && */ localHits.size() > 0) {
								boolean skipWarning = false;
								if (args.length >= 4) {
									if (args[args.length - 1].equalsIgnoreCase("-o")) {
										if (!MyPermission.LOOKUP_XRAY_OVERWRITE.hasPermission(sender)) {
											sender.sendMessage(plugin.translate("no-permission"));
											return;
										}
										sender.sendMessage(plugin.translate("xray-rate-overwrite"));
										skipWarning = true;
										for (DbEntry warn : localHits) {
											plugin.getSqlManager().removeEntry(warn);
										}
									} else if (args[args.length - 1].equalsIgnoreCase("-i")) {
										sender.sendMessage(plugin.translate("xray-rate-ignore"));
										skipWarning = true;
									} else if (args[args.length - 1].equalsIgnoreCase("-c")) {
										sender.sendMessage(plugin.translate("xray-rate-cancelled"));
										results_.showPage(entryIndex_ + 2, 1);
										return;
									}
								}
								if (!skipWarning) {
									sender.sendMessage(plugin.translate("xray-rate-conflic"));
									boolean validWarning = false;
									for (DbEntry warn : localHits) {
										if (warn.getUserUUID().equals(en.getUserUUID())) {
											Results.sendEntry(plugin, new MySender(sender), warn, 0);
											validWarning = true;
										}
									}
									if (validWarning) {
										String originalCmd = "/" + label;
										for (String arg : args) {
											originalCmd += " " + arg;
										}
										ComponentBuilder message = new ComponentBuilder();
										message.append("                ").event((ClickEvent) null)
												.event((HoverEvent) null);
										if (MyPermission.LOOKUP_XRAY_OVERWRITE.hasPermission(sender)) {
											message.append("§4§l[Overwrite]")
													.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
															originalCmd + " -o"))
													.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
															new Text("§4Overwrite the existing entries. Be careful!")));
											message.append("    ").event((ClickEvent) null).event((HoverEvent) null);
										}
										message.append("§c§l[Ignore]")
												.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
														originalCmd + " -i"))
												.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
														new Text("§cIgnore the existing entries and add a duplicate")));
										message.append("    ").event((ClickEvent) null).event((HoverEvent) null);
										message.append("§a§l[Cancel]")
												.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
														originalCmd + " -c"))
												.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
														new Text("§aCancel making an entry and go to the next hit.")));
										player.spigot().sendMessage(message.create());
										return;
									}
								}
							}
							plugin.add(DbEntry.createXrayRecord(en, rating_, "Rated by " + sender.getName() + " on "
									+ LocalDateTime.now().format(Results.dateFormatter)));
							sender.sendMessage(plugin.translate("xray-rate-written"));
							if (results_ != null) {
								results_.showPage(entryIndex_ + 2, 1);
							}
						}
						if (results_.getSize() <= entryIndex_ + 1) {
							sender.sendMessage(plugin.translate("xray-done"));
							return;
						}
						DbEntry newEnt = results_.getEntry(entryIndex_ + 1);
						if (newEnt != null) {
							new BukkitRunnable() {
								@Override
								public void run() {
									Bukkit.getServer().dispatchCommand(sender,
											(String.format(plugin.getCommandPrefix() + " tp %d %d %d %s %d %d",
													newEnt.x, newEnt.y, newEnt.z, newEnt.world, 45, 0)));
								}
							}.runTask(plugin);
						}
					}
				}.runTaskAsynchronously(plugin);
				return true;
			} else if (args[1].equalsIgnoreCase("lookup")) {
				long time = TimeUtil.convertTime(args[2]);
				sender.sendMessage(plugin.translate("lookup-looking"));
				new BukkitRunnable() {

					@SuppressWarnings("unchecked")
					@Override
					public void run() {
						ArrayList<DbEntry> entries = new ArrayList<>();
						ArrayList<XrayEntry> wholeList = new ArrayList<>();
						List<Integer> actions = new ArrayList<>();
						actions.add(0);
						List<Object> blocks = new ArrayList<>();
						blocks.add(Material.DIAMOND_ORE);
						if (plugin.getCompatabilityVersion() >= 17) {
							blocks.add(Material.DEEPSLATE_DIAMOND_ORE);
						}
						blocks.add(Material.ANCIENT_DEBRIS);

						/*
						 * List<String[]> lookup = Lookup.performLookup(statement,
						 * Bukkit.getConsoleSender(), new ArrayList<String>(), users, blocks, new
						 * ArrayList<>(), new ArrayList<String>(), actions, player.getLocation(), null,
						 * System.currentTimeMillis() - time, false, true);
						 */
						List<String[]> lookup = null;
						try {
							Statement statement = Database.getConnection(true).createStatement();
							/*
							 * lookup = Lookup.performLookup(statement, Bukkit.getConsoleSender(), new
							 * ArrayList<String>(), new ArrayList<String>(), blocks, new
							 * ArrayList<Object>(), new ArrayList<String>(), actions, null, null,
							 * ((System.currentTimeMillis() - time) / 1000), false, true);
							 */
							Method method = null;
							for (Method method_ : Lookup.class.getMethods()) {
								if (method_.getName().equals("performLookup")) {
									method = method_;
									break;
								}
							}

							lookup = (List<String[]>) method.invoke(null, statement, // Statement statement,
									Bukkit.getConsoleSender(), // CommandSender user,
									new ArrayList<String>(), // List<String> checkUuids,
									new ArrayList<String>(), // List<String> checkUsers,
									blocks, // List<Object> restrictList,
									new HashMap<Object, Boolean>(), // Map<Object, Boolean> excludeList,
									new ArrayList<String>(), // List<String> excludeUserList,
									actions, // List<Integer> actionList,
									null, // Location location,
									null, // Integer[] radius,
									(System.currentTimeMillis() - time) / 1000, // long startTime,
									System.currentTimeMillis() / 1000, // long endTime,
									false, // boolean restrictWorld,
									true);// boolean lookup

						} catch (Throwable e) {
							plugin.print(e);
							sender.sendMessage("§cAn error occured.");
							return;
						}

						HashMap<String, String> params = new HashMap<>();
						params.put("action", EntryAction.XRAYCHECK.toString().toLowerCase());
						params.put("time", (System.currentTimeMillis() - time) + "");
						if (lookup == null) {
							sender.sendMessage("§cAn error occured.");
							return;
						}
						ArrayList<DbEntry> preLogged;
						try {
							preLogged = plugin.getSqlManager().lookup(params, null, false);
						} catch (LookupException e) {
							sender.sendMessage(e.errorMessage);
							return;
						}
						for (String[] resultRaw : lookup) {
							ParseResult res = CoreProtect.getInstance().getAPI().parseResult(resultRaw);
							XrayEntry entry = new XrayEntry((res.getTimestamp()), res.getPlayer(), EntryAction.VEIN,
									false, res.worldName(), res.getX(), res.getY(), res.getZ(),
									res.getType().toString().toLowerCase(), "");

							if (res.getType() == Material.DIAMOND_ORE || (plugin.getCompatabilityVersion() >= 17
									&& res.getType() == Material.DEEPSLATE_DIAMOND_ORE)) {
								if (res.getY() > 18) {
									continue;
								}
							}

							boolean add = true;
							for (XrayEntry other : wholeList) {
								double distance = entry.getBoxDistance((DbEntry) other);
								if (distance <= 4 && distance >= 0) {
									add = false;
									entry.setParent(other.getParent());
									break;
								}
							}
							if (add) {
								for (DbEntry prelog : preLogged) {
									double distance = entry.getBoxDistance(prelog);
									if (distance <= 4 && distance >= 0) {
										add = false;
										break;
									}
								}
							}
							if (add) {
								entries.add(entry);
							}
							wholeList.add(entry);
						}

						String uuid = "nonplayer";
						if (player != null) {
							uuid = player.getUniqueId().toString();
						}
						entries.sort((o1, o2) -> o1.world.compareTo(o2.world));
						entries.sort((o1, o2) -> o1.getUser().compareTo(o2.getUser()));
						XrayResults result = new XrayResults(plugin, entries, sender);
						result.showPage(1, 1);
						XrayCommand.this.results.put(uuid, result);

					}
				}.runTaskAsynchronously(plugin);
				return true;
			}
		}
		sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
		return true;
	}
}
