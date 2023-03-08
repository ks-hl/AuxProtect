package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.core.Parameters.Flag;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.spigot.VeinManager;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.TimeUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Results {

    protected final SenderAdapter player;
    protected final IAuxProtect plugin;
    protected final List<DbEntry> entries;
    protected final Parameters params;
    private int perPage = 4;
    private int currentPage = 0;

    public Results(IAuxProtect plugin, List<DbEntry> entries, SenderAdapter player, Parameters params) {
        this.entries = Collections.unmodifiableList(entries);
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
        if (allNullWorld || params.hasFlag(Flag.HIDE_COORDS)) {
            setPerPage(10);
        }
        this.params = params;
    }

    public static BaseComponent[] getTime(long time) {
        String msg;
        if (System.currentTimeMillis() - time < 55) {
            msg = Language.L.RESULTS__TIME_NOW.translate();
        } else {
            msg = Language.L.RESULTS__TIME.translate(TimeUtil.millisToString(System.currentTimeMillis() - time));
        }
        ComponentBuilder message = new ComponentBuilder();
        message.append(msg).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new Text(TimeUtil.format(time, TimeUtil.entryTimeFormat)
                                + "\n" + Language.L.RESULTS__CLICK_TO_COPY_TIME.translate(time))))
                .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, time + "e"));
        return message.create();
    }

    public static BaseComponent[] getCoordinates(DbEntry entry) {
        String tpCommand = "/" + AuxProtectAPI.getInstance().getCommandPrefix() + " tp ";
        if (entry instanceof PosEntry posEntry) {
            tpCommand += String.format("%s %s %s ", posEntry.getDoubleX(), posEntry.getDoubleY(), posEntry.getDoubleZ());
        } else {
            tpCommand += String.format("%d.5 %d %d.5 ", entry.getX(), entry.getY(), entry.getZ());
        }
        tpCommand += entry.getWorld();
        if (entry.getAction().getTable().hasLook()) {
            tpCommand += String.format(" %d %d", entry.getPitch(), entry.getYaw());
        }
        ComponentBuilder message = new ComponentBuilder();
        message.append("\n" + " ".repeat(17)).event((HoverEvent) null).event((ClickEvent) null);
        message.append(String.format(ChatColor.COLOR_CHAR + "7(x%d/y%d/z%d/%s)", entry.getX(), entry.getY(), entry.getZ(), entry.getWorld()))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.COLOR_CHAR + "7" + tpCommand)));
        if (entry.getAction().getTable().hasLook()) {
            message.append(String.format(ChatColor.COLOR_CHAR + "7 (p%s/y%d)", entry.getPitch(), entry.getYaw()));
        }
        return message.create();
    }

    @SuppressWarnings("deprecation")
    public static void sendEntry(IAuxProtect plugin, SenderAdapter player, DbEntry entry, int index, boolean time, boolean coords) throws SQLException {
        String commandPrefix = "/" + plugin.getCommandPrefix();
        ComponentBuilder message = new ComponentBuilder();

        if (entry.getUser(false) == null) SQLManager.getInstance().execute(c -> entry.getUser(), 3000L);
        if (entry.getTarget(false) == null) SQLManager.getInstance().execute(c -> entry.getTarget(), 3000L);

        if (time) message.append(getTime(entry.getTime()));

        String actionColor = ChatColor.GRAY + "-";
        if (entry.getAction().hasDual) {
            actionColor = entry.getState() ? ChatColor.GREEN + "+" : ChatColor.RED + "-";
        }
        message.append(" " + actionColor + " ").event((HoverEvent) null);
        HoverEvent clickToCopy = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__CLICK_TO_COPY.translate()));
        message.append(ChatColor.BLUE + entry.getUser()).event(clickToCopy)
                .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getUser()));
        message.append(" " + ChatColor.WHITE + entry.getAction().getText(entry.getState())).event((HoverEvent) null)
                .event((ClickEvent) null);
        message.append(" " + ChatColor.BLUE + entry.getTarget()).event(clickToCopy)
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
        if (entry.getWorld() != null && !entry.getWorld().equals("$null") && coords) {
            message.append(getCoordinates(entry));
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

    public void showPage(int page) throws SQLException {
        showPage(page, getPerPage());
    }

    public void showPage(int page, int perPage_) throws SQLException {
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

    public void sendEntry(DbEntry entry, int index) throws SQLException {
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
        message.append(Language.translate(Language.L.COMMAND__LOOKUP__PAGE_FOOTER, page, getNumPages(getPerPage()), getEntries().size()));
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

    public void setPerPage(int perPage) {
        this.perPage = perPage;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    protected void setCurrentPage(int prevPage) {
        this.currentPage = prevPage;
    }
}
