package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.*;
import dev.heliosares.auxprotect.core.Parameters.Flag;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.spigot.VeinManager;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.TimeUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.sql.SQLException;
import java.util.List;
import java.util.Random;

public class Results {

    protected final SenderAdapter player;
    final IAuxProtect plugin;
    private final List<DbEntry> entries;
    private final Parameters params;
    public int perPage = 4;
    public int prevPage = 0;

    public Results(IAuxProtect plugin, List<DbEntry> entries, SenderAdapter player, Parameters params) {
        this.entries = entries;
        this.player = player;
        this.plugin = plugin;

        boolean allNullWorld = true;
        int count = 0;
        for (DbEntry entry : entries) {
            if (entry.getWorld() != null && !entry.getWorld().equals("#null")) {
                allNullWorld = false;
                break;
            }
            if (count++ > 1000) {
                break;
            }
        }
        if (allNullWorld) {
            perPage = 10;
        }
        this.params = params;
    }

    @SuppressWarnings("deprecation")
    public static void sendEntry(IAuxProtect plugin, SenderAdapter player, DbEntry entry, int index, boolean time, boolean coords) throws SQLException, BusyException {
        String commandPrefix = "/" + plugin.getCommandPrefix();
        ComponentBuilder message = new ComponentBuilder();

        if (entry.getUser(false) == null) plugin.getSqlManager().execute(c -> entry.getUser(), 3000L);
        if (entry.getTarget(false) == null) plugin.getSqlManager().execute(c -> entry.getTarget(), 3000L);

        plugin.debug(entry.getTarget() + "(" + entry.getTargetId() + "): " + entry.getTargetUUID());

        APPlayer apPlayer = plugin.getAPPlayer(player);
        if (time && apPlayer.getTimeZone() == null) {
            apPlayer.fetchTimeZone();
        }

        if (entry instanceof DbEntryGroup group) {
            if (time) {
                message.append(Language.L.RESULTS__TIME.translate(TimeUtil.millisToString(System.currentTimeMillis() - group.getFirstTime()) +
                                "-" + TimeUtil.millisToString(System.currentTimeMillis() - group.getLastTime())))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new Text(TimeUtil.format(group.getFirstTime(), TimeUtil.entryTimeFormat, apPlayer.getTimeZone().toZoneId()) + " -\n" + TimeUtil.format(group.getLastTime(), TimeUtil.entryTimeFormat, apPlayer.getTimeZone().toZoneId())
                                        + "\n" + Language.L.RESULTS__CLICK_TO_COPY_TIME.translate(entry.getTime()))))
                        .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, group.getFormattedEpoch()));
            }
            message.append(" ").event((HoverEvent) null).event((ClickEvent) null);
            message.append(Language.L.RESULTS__GROUPING_OF.translate(group.getNumEntries())).event(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND, commandPrefix + " lookup " + group.hash() + "g"));
        } else {
            if (time) {
                String msg;
                if (System.currentTimeMillis() - entry.getTime() < 55) {
                    msg = Language.L.RESULTS__TIME_NOW.translate();
                } else {
                    msg = Language.L.RESULTS__TIME.translate(TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime()));
                }
                message.append(msg).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new Text(TimeUtil.format(entry.getTime(), TimeUtil.entryTimeFormat, apPlayer.getTimeZone().toZoneId())
                                        + "\n" + Language.L.RESULTS__CLICK_TO_COPY_TIME.translate(entry.getTime()))))
                        .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getTime() + "e"));
            }
            String actionColor = ChatColor.COLOR_CHAR + "7-";
            if (entry.getAction().hasDual) {
                actionColor = entry.getState() ? ChatColor.COLOR_CHAR + "a+" : ChatColor.COLOR_CHAR + "c-";
            }
            message.append(" " + actionColor + " ").event((HoverEvent) null);
            HoverEvent clickToCopy = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__CLICK_TO_COPY.translate()));
            message.append(ChatColor.COLOR_CHAR + "9" + entry.getUser()).event(clickToCopy)
                    .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getUser()));
            message.append(" " + ChatColor.COLOR_CHAR + "f" + entry.getAction().getText(entry.getState())).event((HoverEvent) null)
                    .event((ClickEvent) null);
            message.append(" " + ChatColor.COLOR_CHAR + "9" + entry.getTarget()).event(clickToCopy)
                    .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getTarget()));

            XrayEntry xray;
            if (entry instanceof XrayEntry) {
                xray = (XrayEntry) entry;
                String rating;
                if (xray.getRating() == -2) {
                    rating = ChatColor.COLOR_CHAR + "5Ignored";
                } else if (xray.getRating() == -1) {
                    rating = ChatColor.COLOR_CHAR + "7Unrated";
                } else {
                    rating = xray.getRating() + "";
                }
                String color = VeinManager.getSeverityColor(xray.getRating());
                message.append(String.format(" " + ChatColor.COLOR_CHAR + "8[%s%s" + ChatColor.COLOR_CHAR + "8]", color, rating)).event(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, "/" + plugin.getCommandPrefix() + " xray rate " + entry.getTime()));
                String hover = "";
                if (xray.getRating() >= 0) {
                    hover += color + VeinManager.getSeverityDescription(xray.getRating()) + "\n\n";
                }
                hover += Language.translate(Language.L.XRAY_CLICK_TO_CHANGE);
                message.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)));
            }

            String data = entry.getData();
            if (entry.hasBlob()) {
                if (APPermission.INV.hasPermission(player)) {
                    message.append(" " + ChatColor.COLOR_CHAR + "a[" + Language.L.RESULTS__VIEW + "]")
                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    String.format(commandPrefix + " inv %d", index)))
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__CLICK_TO_VIEW.translate())));
                }
            }
            if (entry.getAction().equals(EntryAction.KILL)) {
                if (APPermission.INV.hasPermission(player) && !entry.getTarget().startsWith("#")) {
                    message.append(" " + ChatColor.COLOR_CHAR + "a[" + Language.L.RESULTS__VIEW_INV + "]")
                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    String.format(commandPrefix + " l u:%s a:inventory target:death time:%de+-20e",
                                            entry.getTarget(), entry.getTime())))
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__CLICK_TO_VIEW.translate())));
                }
            }
            if (entry.getAction().equals(EntryAction.SESSION)) {
                if (!APPermission.LOOKUP_ACTION.dot(EntryAction.SESSION.toString().toLowerCase()).dot("ip").hasPermission(player)) {
                    data = Language.L.RESULTS__REDACTED.translate();
                }
            }
            if (entry instanceof SingleItemEntry sientry) {
                message.append(" " + ChatColor.COLOR_CHAR + "8[" + ChatColor.COLOR_CHAR + "7x" + sientry.getQty() + (sientry.getDamage() > 0 ? ", " + sientry.getDamage() + " damage" : "") + ChatColor.COLOR_CHAR + "8]").event((HoverEvent) null).event((ClickEvent) null);
            }
            if (plugin.getAPConfig().doSkipV6Migration()) {
                if (data.contains(InvSerialization.ITEM_SEPARATOR)) {
                    data = data.substring(0, data.indexOf(InvSerialization.ITEM_SEPARATOR));
                }
                if (entry.getAction().equals(EntryAction.INVENTORY)) {
                    data = null;
                }
            }
            if (data != null && data.length() > 0) {
                message.append(" " + ChatColor.COLOR_CHAR + "8[" + ChatColor.COLOR_CHAR + "7" + data + ChatColor.COLOR_CHAR + "8]").event(clickToCopy)
                        .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getData()));
            }
        }
        if (entry.getWorld() != null && !entry.getWorld().equals("$null") && coords) {
            String tpCommand = commandPrefix + " tp ";
            if (entry instanceof PosEntry posEntry) {
                tpCommand += String.format("%s %s %s ", posEntry.getDoubleX(), posEntry.getDoubleY(), posEntry.getDoubleZ());
            } else {
                tpCommand += String.format("%d.5 %d %d.5 ", entry.getX(), entry.getY(), entry.getZ());
            }
            tpCommand += entry.getWorld();
            if (entry.getAction().getTable().hasLook()) {
                tpCommand += String.format(" %d %d", entry.getPitch(), entry.getYaw());
            }
            message.append("\n                 ").event((HoverEvent) null).event((ClickEvent) null);
            message.append(String.format(ChatColor.COLOR_CHAR + "7(x%d/y%d/z%d/%s)", entry.getX(), entry.getY(), entry.getZ(), entry.getWorld()))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.COLOR_CHAR + "7" + tpCommand)));
            if (entry.getAction().getTable().hasLook()) {
                message.append(String.format(ChatColor.COLOR_CHAR + "7 (p%s/y%d)", entry.getPitch(), entry.getYaw()));
            }
        }
        player.sendMessage(message.create());
    }

    public List<DbEntry> getEntries() {
        return entries;
    }

    public DbEntry get(int i) {
        return getEntries().get(i);
    }

    public void sendHeader() {
        String headerColor = "&7";
        StringBuilder line = new StringBuilder("&m");
        line.append(String.valueOf((char) 65293).repeat(6));
        line.append("&7");
        if (new Random().nextDouble() < 0.001) {
            headerColor = "&f"; // The header had these mismatched colors for over a year of development until
            // v1.1.3. This is a tribute to that screw up
        }
        player.sendMessageRaw(headerColor + line + "  " + Language.L.RESULTS__HEADER + "&7  " + line);
    }

    public void showPage(int page) throws SQLException, BusyException {
        showPage(page, perPage);
    }

    public void showPage(int page, int perPage_) throws SQLException, BusyException {
        int lastpage = getNumPages(perPage_);
        if (page > lastpage || page < 1) {
            player.sendLang(Language.L.COMMAND__LOOKUP__NOPAGE);
            return;
        }
        perPage = perPage_;
        prevPage = page;
        sendHeader();
        for (int i = (page - 1) * perPage; i < (page) * perPage && i < getEntries().size(); i++) {
            DbEntry en = getEntries().get(i);

            sendEntry(en, i);
        }
        sendArrowKeys(page);
    }

    public void sendEntry(DbEntry entry, int index) throws SQLException, BusyException {
        sendEntry(plugin, player, entry, index, true, !params.hasFlag(Flag.HIDE_COORDS));
    }

    public void sendArrowKeys(int page) {
        String commandPrefix = "/" + plugin.getCommandPrefix();
        ComponentBuilder message = new ComponentBuilder();
        int lastpage = getNumPages(perPage);
        message.append(ChatColor.COLOR_CHAR + "7(");
        if (page > 1) {
            message.append(ChatColor.COLOR_CHAR + "9" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW + AuxProtectSpigot.LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandPrefix + " l 1:" + perPage))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__PAGE__FIRST.translate())));
            message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
            message.append(ChatColor.COLOR_CHAR + "9" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            commandPrefix + " l " + (page - 1) + ":" + perPage))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__PAGE__PREVIOUS.translate())));
        } else {
            message.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ""));
            message.append(ChatColor.COLOR_CHAR + "8" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW + AuxProtectSpigot.LEFT_ARROW).event((ClickEvent) null)
                    .event((HoverEvent) null);
            message.append(" ");
            message.append(ChatColor.COLOR_CHAR + "8" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW);
        }
        message.append("  ").event((ClickEvent) null).event((HoverEvent) null);
        if (page < lastpage) {
            message.append(ChatColor.COLOR_CHAR + "9" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.RIGHT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            commandPrefix + " l " + (page + 1) + ":" + perPage))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__PAGE__NEXT.translate())));
            message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
            message.append(ChatColor.COLOR_CHAR + "9" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.RIGHT_ARROW + AuxProtectSpigot.RIGHT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            commandPrefix + " l " + lastpage + ":" + perPage))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__PAGE__LAST.translate())));
        } else {
            message.append(ChatColor.COLOR_CHAR + "8" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.RIGHT_ARROW).event((ClickEvent) null).event((HoverEvent) null);
            message.append(" ");
            message.append(ChatColor.COLOR_CHAR + "8" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.RIGHT_ARROW + AuxProtectSpigot.RIGHT_ARROW);
        }
        message.append(ChatColor.COLOR_CHAR + "7)  ").event((ClickEvent) null).event((HoverEvent) null);
        Language.L lang;
        if (entries.get(0) instanceof DbEntryGroup) {
            lang = Language.L.COMMAND__LOOKUP__PAGE_FOOTER_GROUPS;
        } else {
            lang = Language.L.COMMAND__LOOKUP__PAGE_FOOTER;
        }
        message.append(Language.translate(lang, page, getNumPages(perPage), getEntries().size()));
        player.sendMessage(message.create());
    }

    public int getNumPages(int perpage) {
        return (int) Math.ceil(getEntries().size() / (double) perpage);
    }

    public int getSize() {
        return getEntries().size();
    }
}
