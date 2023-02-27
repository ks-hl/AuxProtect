package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.*;
import dev.heliosares.auxprotect.database.*;
import dev.heliosares.auxprotect.exceptions.*;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class XrayCommand extends Command {

    public static final DateTimeFormatter ratedByDateFormatter = DateTimeFormatter.ofPattern("ddMMMYY HHmm");
    HashMap<String, Results> results = new HashMap<>();

    public XrayCommand(IAuxProtect plugin) {
        super(plugin, "xray", APPermission.XRAY, true, "x");
    }

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
                    XrayEntry entry = null;
                    if (time > 0) {
                        ArrayList<DbEntry> entries;
                        try {
                            entries = plugin.getSqlManager().getLookupManager().lookup(plugin.getSqlManager(),
                                    Table.AUXPROTECT_XRAY,
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
                            StringBuilder thiscmd = new StringBuilder("/" + label);
                            for (String arg : args) {
                                thiscmd.append(" ").append(arg);
                            }
                            message.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, thiscmd + " -i"));
                            message.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to overwrite")));
                            player.spigot().sendMessage(message.create());
                            sender.sendMessageRaw("");

                            return;
                        }
                        if (rating == entry.getRating() && !override) {
                            sender.sendLang(Language.L.XRAY_RATE_NOCHANGE);
                            return;
                        }

                        entry.setRating(rating, player.getName());

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
                        try {
                            XrayResults.sendEntry(spigot, sender, entry, auto);
                        } catch (BusyException e) {
                            sender.sendLang(Language.L.DATABASE_BUSY);
                        } catch (SQLException e) {
                            sender.sendLang(Language.L.ERROR);
                        }
                    }


                } else if (args[1].equalsIgnoreCase("report")) {
                    String cmd = plugin.getCommandPrefix() + " l a:vein #xray";
                    if (args.length > 2) {
                        cmd += " t:" + args[2];
                    } else {
                        cmd += " t:1w";
                    }
                    sender.executeCommand(cmd);
                } else if (args[1].equalsIgnoreCase("zero")) {
                    if (!APPermission.LOOKUP_XRAY_BULK.hasPermission(sender)) {
                        sender.sendLang(Language.L.NO_PERMISSION);
                        return;
                    }
                    UUID target;
                    try {
                        if (args.length != 3 && args.length != 4) throw new IllegalArgumentException();
                        target = UUID.fromString(args[2]);
                    } catch (IllegalArgumentException e) {
                        sender.sendLang(Language.L.INVALID_SYNTAX);
                        return;
                    }
                    boolean confirmed = args.length == 4 && args[3].equalsIgnoreCase("-i");
                    plugin.runAsync(() -> {
                        try {
                            plugin.getSqlManager().execute(connection -> {
                                int uid = plugin.getSqlManager().getUserManager().getUIDFromUUID("$" + target, false);
                                String name = plugin.getSqlManager().getUserManager().getUsernameFromUID(uid, false);
                                if (confirmed) {
                                    ((AuxProtectSpigot) plugin).getVeinManager().iterator(it -> {
                                        while (it.hasNext()) {
                                            XrayEntry entry = it.next();
                                            try {
                                                if (!entry.getUserUUID().equals("$" + target)) return;
                                                if (entry.getRating() != -1) return;
                                                entry.setRating((short) 0, player.getName() + " BULK");
                                                plugin.getSqlManager().updateXrayEntry(entry);
                                                it.remove();
                                            } catch (SQLException e) {
                                                sender.sendLang(Language.L.ERROR);
                                                return;
                                            }
                                        }
                                    });
                                    sender.sendMessageRaw("§aDone!");
                                } else {
                                    ComponentBuilder message = new ComponentBuilder("§cAre you sure you want to rate all entries from " + name + " as 0?\n\n");
                                    message.append("§c§l[Yes]");
                                    StringBuilder thiscmd = new StringBuilder("/" + label);
                                    for (String arg : args) {
                                        thiscmd.append(" ").append(arg);
                                    }
                                    message.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, thiscmd + " -i"));
                                    message.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to overwrite")));
                                    player.spigot().sendMessage(message.create());
                                    sender.sendMessageRaw("");
                                }
                            }, 3000L);
                        } catch (BusyException e) {
                            sender.sendLang(Language.L.DATABASE_BUSY);
                        } catch (SQLException e) {
                            sender.sendLang(Language.L.ERROR);
                        }
                    });
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
                    sender.executeCommand(String.format(plugin.getCommandPrefix() + " tp %d %d %d %s %d %d", current.getX(),
                            current.getY(), current.getZ(), current.getWorld(), 45, 0));
                    try {
                        XrayResults.sendEntry(spigot, sender, current, true);
                    } catch (BusyException e) {
                        sender.sendLang(Language.L.DATABASE_BUSY);
                        return;
                    } catch (SQLException e) {
                        sender.sendLang(Language.L.ERROR);
                        return;
                    }
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
        player.executeCommand(String.format(plugin.getCommandPrefix() + " tp %d %d %d %s %d %d", en.getX(), en.getY(),
                en.getZ(), en.getWorld(), 45, 0));
        try {
            XrayResults.sendEntry(plugin, player, en, auto);
        } catch (BusyException e) {
            player.sendLang(Language.L.DATABASE_BUSY);
        } catch (SQLException e) {
            player.sendLang(Language.L.ERROR);
        }
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
