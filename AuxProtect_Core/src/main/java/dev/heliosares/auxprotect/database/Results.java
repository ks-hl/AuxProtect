package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.message.ClickEvent;
import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.adapters.message.HoverEvent;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.core.Parameters.Flag;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.utils.TimeUtil;

import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

public class Results {

    protected final SenderAdapter player;
    final IAuxProtect plugin;
    private final List<DbEntry> entries;
    private final Parameters params;
    private int perPage = 4;
    private int currentPage = 0;
    public static final char LEFT_ARROW = 9668;
    public static final char RIGHT_ARROW = 9658;
    public static final HoverEvent clickToCopyHoverEvent = HoverEvent.showText(Language.L.RESULTS__CLICK_TO_COPY.translate());

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

    public static void sendEntry(IAuxProtect plugin, SenderAdapter player, DbEntry entry, int index, boolean time, boolean coords, boolean showData) throws SQLException, BusyException {
        String commandPrefix = "/" + plugin.getCommandPrefix();
        final GenericBuilder message = new GenericBuilder(plugin);

        if (entry.getUser(false) == null) plugin.getSqlManager().execute(c -> entry.getUser(), 3000L);
        if (entry.getTarget(false) == null) plugin.getSqlManager().execute(c -> entry.getTarget(), 3000L);

        plugin.debug(entry.getTarget() + "(" + entry.getTargetId() + "): " + entry.getTargetUUID());

        APPlayer<?> apPlayer = plugin.getAPPlayer(player);
        TimeZone timeZone = time ? (apPlayer == null ? TimeZone.getDefault() : apPlayer.getTimeZone()) : null;
        if (entry instanceof DbEntryGroup group) {
            if (time) {
                message.append(Language.L.RESULTS__TIME.translate(TimeUtil.millisToString(System.currentTimeMillis() - group.getFirstTime()) +
                                "-" + TimeUtil.millisToString(System.currentTimeMillis() - group.getLastTime())))
                        .hover(TimeUtil.format(group.getFirstTime(), TimeUtil.entryTimeFormat, timeZone.toZoneId()) + " -\n" + TimeUtil.format(group.getLastTime(), TimeUtil.entryTimeFormat, apPlayer.getTimeZone().toZoneId())
                                + "\n" + Language.L.RESULTS__CLICK_TO_COPY_TIME.translate(entry.getTime()))
                        .click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, group.getFormattedEpoch()));
            }
            message.append(" ");
            message.append(Language.L.RESULTS__GROUPING_OF.translate(group.getNumEntries())).click(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND, commandPrefix + " lookup " + group.hash() + "g"));
        } else {
            if (time) entry.appendTime(message, timeZone);

            String actionColor = GenericTextColor.COLOR_CHAR + "7-";
            if (entry.getAction().hasDual) {
                actionColor = entry.getState() ? GenericTextColor.COLOR_CHAR + "a+" : GenericTextColor.COLOR_CHAR + "c-";
            }
            message.append(" " + actionColor + " ");

            entry.appendUser(message);
            message.append(" ");
            entry.appendAction(message);

            if (entry.getTarget() != null && !entry.getTarget().isEmpty()) {
                message.append(" ");
                entry.appendTarget(message, plugin);
            }

            if (showData) {
                entry.appendData(message, plugin, player);
            }
            entry.appendButtons(message, player, commandPrefix, index);
        }
        if (entry.getWorld() != null && !entry.getWorld().equals("$null") && coords) {
            entry.appendCoordinates(player, message);
        }
        message.send(player);
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
        sendEntry(plugin, player, entry, index, true, !params.hasFlag(Flag.HIDE_COORDS), !params.hasFlag(Flag.HIDE_DATA));
    }

    protected String getCommandPrefix() {
        return "/" + plugin.getCommandPrefix() + " l ";
    }

    protected String getCommand(int which) {
        return switch (which) {
            case -2 -> "1:" + getPerPage();
            case -1 -> (currentPage - 1) + ":" + getPerPage();
            case 1 -> (currentPage + 1) + ":" + getPerPage();
            case 2 -> getNumPages(getPerPage()) + ":" + getPerPage();
            default -> throw new IllegalArgumentException();
        };
    }

    public void sendArrowKeys(int page) {
        final GenericBuilder message = new GenericBuilder(plugin);
        int lastpage = getNumPages(getPerPage());
        message.append(GenericTextColor.COLOR_CHAR + "7(");
        if (page > 1) {
            message.append(GenericTextColor.COLOR_CHAR + "9" + GenericTextColor.COLOR_CHAR + "l" + LEFT_ARROW + LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(-2)))
                    .hover(Language.L.RESULTS__PAGE__FIRST.translate());
            message.append(" ");
            message.append(GenericTextColor.COLOR_CHAR + "9" + GenericTextColor.COLOR_CHAR + "l" + LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(-1)))
                    .hover(Language.L.RESULTS__PAGE__PREVIOUS.translate());
        } else {
            message.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ""));
            message.append(GenericTextColor.COLOR_CHAR + "8" + GenericTextColor.COLOR_CHAR + "l" + LEFT_ARROW + LEFT_ARROW)
            ;
            message.append(" ");
            message.append(GenericTextColor.COLOR_CHAR + "8" + GenericTextColor.COLOR_CHAR + "l" + LEFT_ARROW);
        }
        message.append("  ");
        if (page < lastpage) {
            message.append(GenericTextColor.COLOR_CHAR + "9" + GenericTextColor.COLOR_CHAR + "l" + RIGHT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(1)))
                    .hover(Language.L.RESULTS__PAGE__NEXT.translate());
            message.append(" ");
            message.append(GenericTextColor.COLOR_CHAR + "9" + GenericTextColor.COLOR_CHAR + "l" + RIGHT_ARROW + RIGHT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(2)))
                    .hover(Language.L.RESULTS__PAGE__LAST.translate());
        } else {
            message.append(GenericTextColor.COLOR_CHAR + "8" + GenericTextColor.COLOR_CHAR + "l" + RIGHT_ARROW);
            message.append(" ");
            message.append(GenericTextColor.COLOR_CHAR + "8" + GenericTextColor.COLOR_CHAR + "l" + RIGHT_ARROW + RIGHT_ARROW);
        }
        message.append(GenericTextColor.COLOR_CHAR + "7)  ");
        Language.L lang;
        if (entries.get(0) instanceof DbEntryGroup) {
            lang = Language.L.COMMAND__LOOKUP__PAGE_FOOTER_GROUPS;
        } else {
            lang = Language.L.COMMAND__LOOKUP__PAGE_FOOTER;
        }
        message.append(Language.translate(lang, page, getNumPages(getPerPage()), getEntries().size()));
        message.send(player);
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
