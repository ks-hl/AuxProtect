package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.core.Parameters.Flag;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.spigot.VeinManager;
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
    private int perPage = 4;
    private int currentPage = 0;

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
            setPerPage(10);
        }
        this.params = params;
    }

    public static void sendEntry(IAuxProtect plugin, SenderAdapter player, DbEntry entry, int index, boolean time, boolean coords) throws SQLException, BusyException {
        String commandPrefix = "/" + plugin.getCommandPrefix();
        ComponentBuilder message = new ComponentBuilder();

        if (entry.getUser(false) == null) plugin.getSqlManager().execute(c -> entry.getUser(), 3000L);
        if (entry.getTarget(false) == null) plugin.getSqlManager().execute(c -> entry.getTarget(), 3000L);

        plugin.debug(entry.getTarget() + "(" + entry.getTargetId() + "): " + entry.getTargetUUID());

        APPlayer<?> apPlayer = plugin.getAPPlayer(player);

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
            if (time) entry.appendTime(message, apPlayer.getTimeZone());

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

            String target = entry.getTarget();
            if (entry instanceof TransactionEntry transaction && transaction.getQuantity() > 0) {
                target = transaction.getQuantity() + " " + target;
            }

            if (target != null && !target.isEmpty()) {
                message.append(" " + ChatColor.COLOR_CHAR + "9" + target).event(clickToCopy)
                        .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getTarget() /* Still using the base target here for TransactionEntry because I think it's the most useful. */));
            } else {
                message.event((ClickEvent) null).event((HoverEvent) null);
            }

            if (entry instanceof TransactionEntry transaction) {
                String target2 = transaction.getTarget2();
                if (target2 != null && !target2.isEmpty()) {
                    String fromTo = entry.getState() ? "from" : "to";
                    message.append(ChatColor.COLOR_CHAR + "f " + fromTo + " ").event((ClickEvent) null).event((HoverEvent) null);
                    message.append(ChatColor.COLOR_CHAR + "9" + target2).event(clickToCopy).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, target2));
                }
                message.append(ChatColor.COLOR_CHAR + "f for ").event((ClickEvent) null).event((HoverEvent) null);
                String cost = AuxProtectAPI.formatMoney(transaction.getCost());
                message.append(ChatColor.COLOR_CHAR + "9" + cost).event(clickToCopy).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, cost));

                message.append(" " + ChatColor.DARK_GRAY + "[" + ChatColor.GRAY).event((ClickEvent) null).event((HoverEvent) null);
                String balance = AuxProtectAPI.formatMoney(transaction.getBalance());
                message.append("Balance: " + balance).event(clickToCopy).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, balance));
                message.append(ChatColor.DARK_GRAY + "]").event((ClickEvent) null).event((HoverEvent) null);
            }


            if (entry instanceof XrayEntry xray) {
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
            if (entry instanceof SingleItemEntry sientry) {
                message.append(" " + ChatColor.COLOR_CHAR + "8[" + ChatColor.COLOR_CHAR + "7x" + sientry.getQty() + (sientry.getDamage() > 0 ? ", " + sientry.getDamage() + " damage" : "") + ChatColor.COLOR_CHAR + "8]").event((HoverEvent) null).event((ClickEvent) null);
            }
            String data = entry.getData();
            if (data != null && !data.isEmpty()) {
                if (entry.getAction().equals(EntryAction.SESSION) && !APPermission.LOOKUP_ACTION.dot(EntryAction.SESSION.toString().toLowerCase()).dot("ip").hasPermission(player)) {
                    message.append(" " + ChatColor.COLOR_CHAR + "8[" + ChatColor.COLOR_CHAR + "7" + Language.L.RESULTS__REDACTED.translate() + ChatColor.COLOR_CHAR + "8]");
                    message.event((ClickEvent) null).event((HoverEvent) null);
                } else {
                    message.append(" " + ChatColor.COLOR_CHAR + "8[" + ChatColor.COLOR_CHAR + "7" + data + ChatColor.COLOR_CHAR + "8]");
                    message.event(clickToCopy).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, data));
                }
            }
        }
        if (entry.getWorld() != null && !entry.getWorld().equals("$null") && coords) {
            entry.appendCoordinates(message);
        }
        player.sendMessage(message.create());
    }

    public void setPerPage(int perPage) {
        this.perPage = perPage;
    }

    protected void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
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
        showPage(page, getPerPage());
    }

    public void showPage(int page, int perPage_) throws SQLException, BusyException {
        int lastpage = getNumPages(perPage_);
        if (page > lastpage || page < 1) {
            player.sendLang(Language.L.COMMAND__LOOKUP__NOPAGE);
            return;
        }
        setPerPage(perPage_);
        setCurrentPage(page);
        sendHeader();
        for (int i = (page - 1) * getPerPage(); i < (page) * getPerPage() && i < getEntries().size(); i++) {
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
        int lastpage = getNumPages(getPerPage());
        message.append(ChatColor.COLOR_CHAR + "7(");
        if (page > 1) {
            message.append(ChatColor.COLOR_CHAR + "9" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW + AuxProtectSpigot.LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandPrefix + " l 1:" + getPerPage()))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__PAGE__FIRST.translate())));
            message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
            message.append(ChatColor.COLOR_CHAR + "9" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            commandPrefix + " l " + (page - 1) + ":" + getPerPage()))
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
                            commandPrefix + " l " + (page + 1) + ":" + getPerPage()))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__PAGE__NEXT.translate())));
            message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
            message.append(ChatColor.COLOR_CHAR + "9" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.RIGHT_ARROW + AuxProtectSpigot.RIGHT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            commandPrefix + " l " + lastpage + ":" + getPerPage()))
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
        message.append(Language.translate(lang, page, getNumPages(getPerPage()), getEntries().size()));
        player.sendMessage(message.create());
    }

    public int getNumPages(int perpage) {
        return (int) Math.ceil(getEntries().size() / (double) perpage);
    }

    public int getSize() {
        return getEntries().size();
    }

    public int getPerPage() {
        return perPage;
    }

    public int getCurrentPage() {
        return currentPage;
    }
}
