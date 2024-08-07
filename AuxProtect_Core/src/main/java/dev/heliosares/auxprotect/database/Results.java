package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.ActivityRecord;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
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
import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.hover.content.Item;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.TimeZone;

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

    public static void sendEntry(IAuxProtect plugin, SenderAdapter player, DbEntry entry, int index, boolean time, boolean coords, boolean showData) throws SQLException, BusyException {
        String commandPrefix = "/" + plugin.getCommandPrefix();
        ComponentBuilder message = new ComponentBuilder();

        if (entry.getUser(false) == null) plugin.getSqlManager().execute(c -> entry.getUser(), 3000L);
        if (entry.getTarget(false) == null) plugin.getSqlManager().execute(c -> entry.getTarget(), 3000L);

        plugin.debug(entry.getTarget() + "(" + entry.getTargetId() + "): " + entry.getTargetUUID());

        APPlayer<?> apPlayer = plugin.getAPPlayer(player);
        TimeZone timeZone = time ? (apPlayer == null ? TimeZone.getDefault() : apPlayer.getTimeZone()) : null;
        if (entry instanceof DbEntryGroup group) {
            if (time) {
                message.append(Language.L.RESULTS__TIME.translate(TimeUtil.millisToString(System.currentTimeMillis() - group.getFirstTime()) +
                                "-" + TimeUtil.millisToString(System.currentTimeMillis() - group.getLastTime())))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new Text(TimeUtil.format(group.getFirstTime(), TimeUtil.entryTimeFormat, timeZone.toZoneId()) + " -\n" + TimeUtil.format(group.getLastTime(), TimeUtil.entryTimeFormat, apPlayer.getTimeZone().toZoneId())
                                        + "\n" + Language.L.RESULTS__CLICK_TO_COPY_TIME.translate(entry.getTime()))))
                        .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, group.getFormattedEpoch()));
            }
            message.append(" ").event((HoverEvent) null).event((ClickEvent) null);
            message.append(Language.L.RESULTS__GROUPING_OF.translate(group.getNumEntries())).event(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND, commandPrefix + " lookup " + group.hash() + "g"));
        } else {
            if (time) entry.appendTime(message, timeZone);

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

            if (entry.getTarget() != null && !entry.getTarget().isEmpty()) {
                HoverEvent hoverEvent = null;
                if (entry instanceof TransactionEntry transaction) {
                    if (transaction.getQuantity() > 0) {
                        message.append(ChatColor.BLUE + " " + transaction.getQuantity()).event((ClickEvent) null).event((HoverEvent) null);
                    }
                    if (transaction.getBlob() != null && transaction.getBlob().length > 0) {
                        try {
                            ItemStack item = InvSerialization.toItemStack(transaction.getBlob());
                            if (item.hasItemMeta()) {
                                ItemTag tag = ItemTag.ofNbt(Objects.requireNonNull(item.getItemMeta()).getAsString());
                                hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_ITEM, new Item(item.getType().getKey().getKey(), transaction.getQuantity(), tag));
                            }
                        } catch (ClassNotFoundException | IOException e) {
                            plugin.warning("Error while deserializing item " + transaction);
                        }
                    }
                }
                message.append(" " + (hoverEvent == null ? ChatColor.BLUE : (ChatColor.AQUA + "[")) + entry.getTarget() + (hoverEvent == null ? "" : "]"));
                message.event(hoverEvent == null ? clickToCopy : hoverEvent);
                message.event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getTarget() /* Still using the base target here for TransactionEntry because I think it's the most useful. */));
            } else {
                message.event((ClickEvent) null).event((HoverEvent) null);
            }

            if (entry instanceof TransactionEntry transaction) {
                String target2 = transaction.getTarget2();
                if (target2 != null && !target2.isEmpty() && showData) {
                    String fromTo = entry.getState() ? "from" : "to";
                    message.append(ChatColor.COLOR_CHAR + "f " + fromTo + " ").event((ClickEvent) null).event((HoverEvent) null);
                    message.append(ChatColor.COLOR_CHAR + "9" + target2).event(clickToCopy).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, target2));
                }
                message.append(ChatColor.COLOR_CHAR + "f for ").event((ClickEvent) null).event((HoverEvent) null);
                String cost = plugin.formatMoney(transaction.getCost());
                message.append(ChatColor.COLOR_CHAR + "9" + cost).event(clickToCopy).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, cost));

                if (showData) {
                    message.append(" " + ChatColor.DARK_GRAY + "[").event((ClickEvent) null).event((HoverEvent) null);
                    String balance = plugin.formatMoney(transaction.getBalance());
                    message.append(ChatColor.GRAY + "Balance: " + balance).event(clickToCopy).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, balance));
                    message.append(ChatColor.DARK_GRAY + "]").event((ClickEvent) null).event((HoverEvent) null);
                }
            }


            if (showData) {
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
                    HoverEvent hoverEvent = clickToCopy;
                    if (entry.getAction().equals(EntryAction.ACTIVITY)) {
                        try {
                            ActivityRecord record = ActivityRecord.parse(data);
                            if (record != null) {
                                message.append(" " + org.bukkit.ChatColor.COLOR_CHAR + "a" + record.countScore());
                                hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__CLICK_TO_COPY.translate() + record.getHoverText()));
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (entry.getAction().equals(EntryAction.SESSION) && !APPermission.LOOKUP_ACTION.dot(EntryAction.SESSION.toString().toLowerCase()).dot("ip").hasPermission(player)) {
                        message.append(" " + ChatColor.COLOR_CHAR + "8[" + ChatColor.COLOR_CHAR + "7" + Language.L.RESULTS__REDACTED.translate() + ChatColor.COLOR_CHAR + "8]");
                        message.event((ClickEvent) null).event((HoverEvent) null);
                    } else {
                        message.append(" " + ChatColor.COLOR_CHAR + "8[" + ChatColor.COLOR_CHAR + "7" + data + ChatColor.COLOR_CHAR + "8]");
                        message.event(hoverEvent).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, data));
                    }
                }
                if (entry.getAction().equals(EntryAction.ACTIVITY)) {
                    message.append(" " + ChatColor.COLOR_CHAR + "8[" + ChatColor.COLOR_CHAR + "7Copy Minute Range" + ChatColor.COLOR_CHAR + "8]");
                    ZonedDateTime zonedDateTime = Instant.ofEpochMilli(entry.getTime()).atZone(ZoneId.systemDefault());
                    ZonedDateTime start = zonedDateTime.withSecond(0).withNano(0);
                    ZonedDateTime end = start.plusMinutes(1).minusNanos(1000000);
                    message.event(clickToCopy).event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, start.toInstant().toEpochMilli() + "e-" + end.toInstant().toEpochMilli() + "e"));
                }
            }
        }
        if (entry.getWorld() != null && !entry.getWorld().equals("$null") && coords) {
            entry.appendCoordinates(player, message);
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
        ComponentBuilder message = new ComponentBuilder();
        int lastpage = getNumPages(getPerPage());
        message.append(ChatColor.COLOR_CHAR + "7(");
        if (page > 1) {
            message.append(ChatColor.COLOR_CHAR + "9" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW + AuxProtectSpigot.LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(-2)))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__PAGE__FIRST.translate())));
            message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
            message.append(ChatColor.COLOR_CHAR + "9" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(-1)))
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
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(1)))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__PAGE__NEXT.translate())));
            message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
            message.append(ChatColor.COLOR_CHAR + "9" + ChatColor.COLOR_CHAR + "l" + AuxProtectSpigot.RIGHT_ARROW + AuxProtectSpigot.RIGHT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(2)))
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
