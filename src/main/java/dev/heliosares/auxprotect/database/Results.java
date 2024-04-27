package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.message.ClickEvent;
import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.adapters.message.HoverEvent;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.core.Parameters.Flag;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.spigot.VeinManager;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.TimeUtil;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.sql.SQLException;
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
        final GenericBuilder message = new GenericBuilder();

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
            final HoverEvent clickToCopy = HoverEvent.showText(Language.L.RESULTS__CLICK_TO_COPY.translate());
            message.append(GenericTextColor.BLUE + entry.getUser()).hover(clickToCopy)
                    .click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getUser()));
            message.append(" " + GenericTextColor.WHITE + entry.getAction().getText(entry.getState()));

            if (entry.getTarget() != null && !entry.getTarget().isEmpty()) {
                HoverEvent hoverEvent = null;
                if (entry instanceof TransactionEntry transaction) {
                    if (transaction.getQuantity() > 0) {
                        message.append(GenericTextColor.BLUE + " " + transaction.getQuantity());
                    }
                    if (transaction.getBlob() != null && transaction.getBlob().length > 0) {
                        try {
                            ItemStack item = InvSerialization.toItemStack(transaction.getBlob());
                            if (item.hasItemMeta()) {
                                hoverEvent = HoverEvent.showItem(item.getType().getKey().getKey(), transaction.getQuantity(), Objects.requireNonNull(item.getItemMeta()).getAsString());
                            }
                        } catch (ClassNotFoundException | IOException e) {
                            plugin.warning("Error while deserializing item " + transaction);
                        }
                    }
                }
                message.append(" " + (hoverEvent == null ? GenericTextColor.BLUE : (GenericTextColor.AQUA + "[")) + entry.getTarget() + (hoverEvent == null ? "" : "]"));
                message.hover(hoverEvent == null ? clickToCopy : hoverEvent);
                message.click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getTarget() /* Still using the base target here for TransactionEntry because I think it's the most useful. */));
            }

            if (entry instanceof TransactionEntry transaction) {
                String target2 = transaction.getTarget2();
                if (target2 != null && !target2.isEmpty() && showData) {
                    String fromTo = entry.getState() ? "from" : "to";
                    message.append(GenericTextColor.WHITE + " " + fromTo + " ");
                    message.append(GenericTextColor.BLUE + target2).hover(clickToCopy).click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, target2));
                }
                message.append(GenericTextColor.COLOR_CHAR + "f for ");
                String cost = plugin.formatMoney(transaction.getCost());
                message.append(GenericTextColor.BLUE + cost).hover(clickToCopy).click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, cost));

                if (showData) {
                    message.append(" " + GenericTextColor.DARK_GRAY + "[");
                    String balance = plugin.formatMoney(transaction.getBalance());
                    message.append(GenericTextColor.GRAY + "Balance: " + balance).hover(clickToCopy).click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, balance));
                    message.append(GenericTextColor.DARK_GRAY + "]");
                }
            }


            if (showData) {
                if (entry instanceof XrayEntry xray) {
                    String rating;
                    if (xray.getRating() == -2) {
                        rating = GenericTextColor.COLOR_CHAR + "5Ignored";
                    } else if (xray.getRating() == -1) {
                        rating = GenericTextColor.COLOR_CHAR + "7Unrated";
                    } else {
                        rating = xray.getRating() + "";
                    }
                    String color = VeinManager.getSeverityColor(xray.getRating()).toString();
                    message.append(String.format(" " + GenericTextColor.COLOR_CHAR + "8[%s%s" + GenericTextColor.COLOR_CHAR + "8]", color, rating)).event(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND, "/" + plugin.getCommandPrefix() + " xray rate " + entry.getTime()));
                    String hover = "";
                    if (xray.getRating() >= 0) {
                        hover += color + VeinManager.getSeverityDescription(xray.getRating()) + "\n\n";
                    }
                    hover += Language.translate(Language.L.XRAY_CLICK_TO_CHANGE);
                    message.hover(hover);
                }

                if (entry.hasBlob()) {
                    if (APPermission.INV.hasPermission(player)) {
                        message.append(" " + GenericTextColor.COLOR_CHAR + "a[" + Language.L.RESULTS__VIEW + "]")
                                .click(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        String.format(commandPrefix + " inv %d", index)))
                                .hover(Language.L.RESULTS__CLICK_TO_VIEW.translate());
                    }
                }
                if (entry.getAction().equals(EntryAction.KILL)) {
                    if (APPermission.INV.hasPermission(player) && !entry.getTarget().startsWith("#")) {
                        message.append(" " + GenericTextColor.COLOR_CHAR + "a[" + Language.L.RESULTS__VIEW_INV + "]")
                                .click(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        String.format(commandPrefix + " l u:%s a:inventory target:death time:%de+-20e",
                                                entry.getTarget(), entry.getTime())))
                                .hover(Language.L.RESULTS__CLICK_TO_VIEW.translate());
                    }
                }
                if (entry instanceof SingleItemEntry sientry) {
                    message.append(" " + GenericTextColor.COLOR_CHAR + "8[" + GenericTextColor.COLOR_CHAR + "7x" + sientry.getQty() + (sientry.getDamage() > 0 ? ", " + sientry.getDamage() + " damage" : "") + GenericTextColor.COLOR_CHAR + "8]");
                }
                String data = entry.getData();
                if (data != null && !data.isEmpty()) {
                    if (entry.getAction().equals(EntryAction.SESSION) && !APPermission.LOOKUP_ACTION.dot(EntryAction.SESSION.toString().toLowerCase()).dot("ip").hasPermission(player)) {
                        message.append(" " + GenericTextColor.COLOR_CHAR + "8[" + GenericTextColor.COLOR_CHAR + "7" + Language.L.RESULTS__REDACTED.translate() + GenericTextColor.COLOR_CHAR + "8]");
                    } else {
                        message.append(" " + GenericTextColor.COLOR_CHAR + "8[" + GenericTextColor.COLOR_CHAR + "7" + data + GenericTextColor.COLOR_CHAR + "8]");
                        message.hover(clickToCopy).click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, data));
                    }
                }
            }
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
        final GenericBuilder message = new GenericBuilder();
        int lastpage = getNumPages(getPerPage());
        message.append(GenericTextColor.COLOR_CHAR + "7(");
        if (page > 1) {
            message.append(GenericTextColor.COLOR_CHAR + "9" + GenericTextColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW + AuxProtectSpigot.LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(-2)))
                    .hover(Language.L.RESULTS__PAGE__FIRST.translate());
            message.append(" ");
            message.append(GenericTextColor.COLOR_CHAR + "9" + GenericTextColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(-1)))
                    .hover(Language.L.RESULTS__PAGE__PREVIOUS.translate());
        } else {
            message.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ""));
            message.append(GenericTextColor.COLOR_CHAR + "8" + GenericTextColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW + AuxProtectSpigot.LEFT_ARROW)
            ;
            message.append(" ");
            message.append(GenericTextColor.COLOR_CHAR + "8" + GenericTextColor.COLOR_CHAR + "l" + AuxProtectSpigot.LEFT_ARROW);
        }
        message.append("  ");
        if (page < lastpage) {
            message.append(GenericTextColor.COLOR_CHAR + "9" + GenericTextColor.COLOR_CHAR + "l" + AuxProtectSpigot.RIGHT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(1)))
                    .hover(Language.L.RESULTS__PAGE__NEXT.translate());
            message.append(" ");
            message.append(GenericTextColor.COLOR_CHAR + "9" + GenericTextColor.COLOR_CHAR + "l" + AuxProtectSpigot.RIGHT_ARROW + AuxProtectSpigot.RIGHT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getCommandPrefix() + getCommand(2)))
                    .hover(Language.L.RESULTS__PAGE__LAST.translate());
        } else {
            message.append(GenericTextColor.COLOR_CHAR + "8" + GenericTextColor.COLOR_CHAR + "l" + AuxProtectSpigot.RIGHT_ARROW);
            message.append(" ");
            message.append(GenericTextColor.COLOR_CHAR + "8" + GenericTextColor.COLOR_CHAR + "l" + AuxProtectSpigot.RIGHT_ARROW + AuxProtectSpigot.RIGHT_ARROW);
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
