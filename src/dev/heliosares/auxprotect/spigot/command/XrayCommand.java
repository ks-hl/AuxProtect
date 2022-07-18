package dev.heliosares.auxprotect.spigot.command;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.SQLManager.LookupException;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.database.XrayResults;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class XrayCommand implements CommandExecutor {

	private AuxProtectSpigot plugin;

	public XrayCommand(AuxProtectSpigot plugin) {
		this.plugin = plugin;
		results = new HashMap<>();
	}

	public static final DateTimeFormatter ratedByDateFormatter = DateTimeFormatter.ofPattern("ddMMMYY HHmm");

	HashMap<String, Results> results;

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			return true;
		}

		final Player player = (Player) sender;
		if (args.length > 1) {
			boolean auto_ = false;
			boolean override_ = false;
			for (int i = 2; i < args.length; i++) {
				if (args[i].equalsIgnoreCase("-auto")) {
					auto_ = true;
				} else if (args[i].equalsIgnoreCase("-i")) {
					override_ = true;
				}
			}

			final boolean auto = auto_;
			final boolean override = override_;

			boolean skip = args[1].equalsIgnoreCase("skip");
			if (args[1].equalsIgnoreCase("rate") || skip) {
				if (args.length < 2) {
					sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
					sender.sendMessage("§c/ap xray rate <time-of-entry> <rating -1 - 3>");// TODO lang
					return true;
				}

				long time_ = 0;
				if (!args[2].equalsIgnoreCase("current") || skip) {
					String timestr = args[2];
					if (timestr.endsWith("e")) {
						timestr = timestr.substring(0, timestr.length() - 1);
					}
					try {
						time_ = Long.parseLong(timestr);
					} catch (NumberFormatException e) {
						sender.sendMessage(plugin.translate("lookup-invalid-syntax"));// TODO specificity
						return true;
					}
					if (time_ < 0 || time_ > System.currentTimeMillis()) {
						sender.sendMessage(plugin.translate("lookup-invalid-syntax"));// TODO specificity
						return true;
					}

					if (skip) {
						if (plugin.getVeinManager().skip(player, time_)) {
							nextEntry(player, auto);
							return true;
						}
						sender.sendMessage(plugin.translate("xray-notfound"));
						return true;
					}
				}

				final long time = time_;

				new BukkitRunnable() {

					@Override
					public void run() {
						XrayEntry entry = null;
						if (time > 0) {
							ArrayList<DbEntry> entries;
							try {
								entries = plugin.getSqlManager().lookup(Table.AUXPROTECT_XRAY,
										"SELECT * FROM " + Table.AUXPROTECT_XRAY + " WHERE time = " + time, null);
							} catch (LookupException e) {
								plugin.print(e);
								sender.sendMessage(e.errorMessage);
								return;
							}
							if (entries.size() > 1 && !override) {
								sender.sendMessage(plugin.translate("xray-toomany"));
								return;
							}
							if (entries.size() == 0 && !override) {
								sender.sendMessage(plugin.translate("xray-notfound"));
								return;
							}

							entry = (XrayEntry) entries.get(0);
						} else {
							entry = plugin.getVeinManager().current(player);
						}

						if (args.length >= 4) {
							short rating;
							try {
								rating = Short.parseShort(args[3]);
							} catch (NumberFormatException e) {
								sender.sendMessage(plugin.translate("lookup-invalid-syntax"));// TODO specificity
								return;
							}
							if (rating < -1 || rating > 3) {
								sender.sendMessage(plugin.translate("lookup-invalid-syntax"));// TODO specificity
								return;
							}
							if (entry == null) {
								executeCommandSync(plugin, player, "ap xray");
								return;
							}
							if (entry.getRating() >= 0 && !override) {
								sender.sendMessage(plugin.translate("xray-already-rated"));
								ComponentBuilder message = new ComponentBuilder();
								message.append("§c§l[Overwrite]");
								String thiscmd = "/" + label;
								for (String arg : args) {
									thiscmd += " " + arg;
								}
								message.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, thiscmd + " -i"));
								message.event(
										new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to overwrite")));
								sender.spigot().sendMessage(message.create());
								sender.sendMessage("");

								return;
							}
							if (rating == entry.getRating() && !override) {
								sender.sendMessage(plugin.translate("xray-rate-nochange"));
								return;
							}
							entry.setRating(rating);
							String data = entry.getData();
							if (data.length() > 0) {
								data += "; ";
							}
							String ratedBy = LocalDateTime.now().format(ratedByDateFormatter) + ": " + sender.getName()
									+ " rated " + rating;
							data += ratedBy;
							entry.setData(data);

							try {
								plugin.getSqlManager().updateXrayEntry(entry);
							} catch (SQLException e) {
								plugin.print(e);
								sender.sendMessage(plugin.translate("lookup-error"));
								return;
							}
							sender.sendMessage(plugin.translate("xray-rate-written"));
							if (auto) {
								nextEntry(player, true);
							}
						} else {
							XrayResults.sendEntry(plugin, player, entry, auto);
						}
					}
				}.runTaskAsynchronously(plugin);
				return true;
			} else if (args[1].equalsIgnoreCase("report")) {
				String cmd = plugin.getCommandPrefix() + " l a:vein";
				if (args.length > 2) {
					cmd += " t:" + args[2];
				}
				cmd += " #xray";
				Bukkit.dispatchCommand(sender, cmd);
				return true;
			} else {
				String cmd = plugin.getCommandPrefix() + " l a:vein u:" + args[1];
				if (args.length > 2) {
					cmd += " t:" + args[2];
				}
				cmd += " #xray";
				Bukkit.dispatchCommand(sender, cmd);
				return true;
			}
		} else {
			XrayEntry current = plugin.getVeinManager().current(player);
			if (current != null) {
				executeCommandSync(plugin, player, String.format(plugin.getCommandPrefix() + " tp %d %d %d %s %d %d",
						current.x, current.y, current.z, current.world, 45, 0));
				XrayResults.sendEntry(plugin, player, current, true);
				return true;
			}
			nextEntry(player, true);
			return true;
		}
	}

	private void nextEntry(Player player, boolean auto) {
		XrayEntry en = plugin.getVeinManager().next(player);
		if (en == null) {
			player.sendMessage(plugin.translate("xray-done"));
			return;
		}
		final XrayEntry entry = en;
		executeCommandSync(plugin, player, String.format(plugin.getCommandPrefix() + " tp %d %d %d %s %d %d", entry.x,
				entry.y, entry.z, entry.world, 45, 0));
		XrayResults.sendEntry(plugin, player, en, auto);
	}

	private static void executeCommandSync(AuxProtectSpigot plugin, CommandSender sender, String command) {
		new BukkitRunnable() {
			@Override
			public void run() {
				Bukkit.getServer().dispatchCommand(sender, command);
			}
		}.runTask(plugin);
	}
}
