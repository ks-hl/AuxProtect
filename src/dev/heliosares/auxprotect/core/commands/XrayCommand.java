package dev.heliosares.auxprotect.core.commands;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.entity.Player;
import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.database.XrayResults;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.exceptions.PlatformException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class XrayCommand extends Command {

	public XrayCommand(IAuxProtect plugin) {
		super(plugin, "xray", APPermission.XRAY, "x");
	}

	public static final DateTimeFormatter ratedByDateFormatter = DateTimeFormatter.ofPattern("ddMMMYY HHmm");
	HashMap<String, Results> results = new HashMap<>();

	@Override
	public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
		if (sender.getPlatform() != PlatformType.SPIGOT) {
			throw new PlatformException();
		}
		if (sender.getSender() instanceof Player player && plugin instanceof AuxProtectSpigot spigot) {
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
						throw new SyntaxException();
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
							throw new SyntaxException();
						}
						if (time_ < 0 || time_ > System.currentTimeMillis()) {
							throw new SyntaxException();
						}

						if (skip) {
							if (spigot.getVeinManager().skip(sender.getUniqueId(), time_)) {
								nextEntry(spigot, sender, auto);
								return;
							}
							sender.sendLang(Language.L.XRAY_NOTFOUND);
							return;
						}
					}

					final long time = time_;

					plugin.runAsync(() -> {
						XrayEntry entry = null;
						if (time > 0) {
							ArrayList<DbEntry> entries;
							try {
								entries = plugin.getSqlManager().lookup(Table.AUXPROTECT_XRAY,
										"SELECT * FROM " + Table.AUXPROTECT_XRAY + " WHERE time = " + time, null);
							} catch (LookupException e) {
								plugin.print(e);
								sender.sendMessageRaw(e.getMessage());
								return;
							}
							if (entries.size() > 1 && !override) {
								sender.sendLang(Language.L.XRAY_TOOMANY);
								return;
							}
							if (entries.size() == 0 && !override) {
								sender.sendLang(Language.L.XRAY_NOTFOUND);
								return;
							}

							entry = (XrayEntry) entries.get(0);
						} else {
							entry = spigot.getVeinManager().current(sender.getUniqueId());
						}

						if (args.length >= 4) {
							short rating;
							try {
								rating = Short.parseShort(args[3]);
							} catch (NumberFormatException e) {
								sender.sendLang(Language.L.ERROR);
								return;
							}
							if (rating < -1 || rating > 3) {
								sender.sendLang(Language.L.ERROR);
								return;
							}
							if (entry == null) {
								sender.executeCommand("ap xray");
								return;
							}
							if (entry.getRating() >= 0 && !override) {
								sender.sendLang(Language.L.XRAY_ALREADY_RATED);
								ComponentBuilder message = new ComponentBuilder();
								message.append("§c§l[Overwrite]");
								String thiscmd = "/" + label;
								for (String arg : args) {
									thiscmd += " " + arg;
								}
								message.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, thiscmd + " -i"));
								message.event(
										new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to overwrite")));
								player.spigot().sendMessage(message.create());
								sender.sendMessageRaw("");

								return;
							}
							if (rating == entry.getRating() && !override) {
								sender.sendLang(Language.L.XRAY_RATE_NOCHANGE);
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
							} catch (Exception e) {
								plugin.print(e);
								sender.sendLang(Language.L.ERROR);
								return;
							}
							sender.sendLang(Language.L.XRAY_RATE_WRITTEN);
							if (auto) {
								nextEntry(spigot, sender, true);
							}
						} else {
							XrayResults.sendEntry(spigot, sender, entry, auto);
						}

					});
				} else if (args[1].equalsIgnoreCase("report")) {
					String cmd = plugin.getCommandPrefix() + " l a:vein #xray";
					if (args.length > 2) {
						cmd += " t:" + args[2];
					} else {
						cmd += " t:1w";
					}
					sender.executeCommand(cmd);
				} else {
					String cmd = plugin.getCommandPrefix() + " l a:vein u:" + args[1];
					if (args.length > 2) {
						cmd += " t:" + args[2];
					}
					cmd += " #xray";
					sender.executeCommand(cmd);
				}
			} else {
				XrayEntry current = spigot.getVeinManager().current(sender.getUniqueId());
				if (current != null) {
					sender.executeCommand(String.format(plugin.getCommandPrefix() + " tp %d %d %d %s %d %d", current.x,
							current.y, current.z, current.world, 45, 0));
					XrayResults.sendEntry(spigot, sender, current, true);
				}
				nextEntry(spigot, sender, true);
			}
		}
	}

	private void nextEntry(AuxProtectSpigot plugin, SenderAdapter player, boolean auto) {
		XrayEntry en = plugin.getVeinManager().next(player.getUniqueId());
		if (en == null) {
			player.sendLang(Language.L.XRAY_DONE);
			return;
		}
		final XrayEntry entry = en;
		player.executeCommand(String.format(plugin.getCommandPrefix() + " tp %d %d %d %s %d %d", entry.x, entry.y,
				entry.z, entry.world, 45, 0));
		XrayResults.sendEntry(plugin, player, en, auto);
	}

	@Override
	public boolean exists() {
		return plugin.getPlatform() == PlatformType.SPIGOT && plugin.getAPConfig().isPrivate();
	}

	@Override
	public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
		// TODO This whole thing...
		return null;
	}
}
