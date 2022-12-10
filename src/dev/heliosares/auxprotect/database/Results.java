package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.core.Parameters.Flag;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.spigot.VeinManager;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.TimeUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Random;

public class Results {

    public static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("ddMMMYY HH:mm:ss.SSS");
    protected final SenderAdapter player;
    final IAuxProtect plugin;
    private final ArrayList<DbEntry> entries;
    private final Parameters params;
    public int perpage = 4;
    public int prevpage = 0;

    public Results(IAuxProtect plugin, ArrayList<DbEntry> entries, SenderAdapter player, Parameters params) {
        this.entries = entries;
        this.player = player;
        this.plugin = plugin;

        boolean allNullWorld = true;
        int count = 0;
        for (DbEntry entry : entries) {
            if (entry.world != null && !entry.world.equals("#null")) {
                allNullWorld = false;
                break;
            }
            if (count++ > 1000) {
                break;
            }
        }
        if (allNullWorld) {
            perpage = 10;
        }
        this.params = params;
    }

    @SuppressWarnings("deprecation")
    public static void sendEntry(IAuxProtect plugin, SenderAdapter player, DbEntry entry, int index, boolean time,
                                 boolean coords) {
        String commandPrefix = "/" + plugin.getCommandPrefix();
        ComponentBuilder message = new ComponentBuilder();

        plugin.debug(entry.getTarget() + "(" + entry.getTargetId() + "): " + entry.getTargetUUID());

        if (time) {
            String msg = "";
            if (System.currentTimeMillis() - entry.getTime() < 55) {
                msg = "&7Just Now";
            } else {
                msg = String.format("&7%s ago", TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime()),
                        entry.getUser());
            }
            message.append(msg).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new Text(Instant.ofEpochMilli(entry.getTime()).atZone(ZoneId.systemDefault()).format(dateFormatter)
                                    + "\n&7Click to copy epoch time. (" + entry.getTime() + "ms)")))
                    .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getTime() + "e"));
        }

        String actionColor = "&7-";
        if (entry.getAction().hasDual) {
            actionColor = entry.getState() ? "&a+" : "&c-";
        }
        message.append(" " + actionColor + " ").event((HoverEvent) null);
        HoverEvent clickToCopy = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to copy to clipboard"));
        message.append("&9" + entry.getUser()).event(clickToCopy)
                .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getUser()));
        message.append(" &f" + entry.getAction().getText(entry.getState())).event((HoverEvent) null)
                .event((ClickEvent) null);
        message.append(" &9" + entry.getTarget()).event(clickToCopy)
                .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getTarget()));

        XrayEntry xray = null;
        if (entry instanceof XrayEntry) {
            xray = (XrayEntry) entry;
            String rating = null;
            if (xray.getRating() == -2) {
                rating = "&5Ignored";
            } else if (xray.getRating() == -1) {
                rating = "&7Unrated";
            } else {
                rating = xray.getRating() + "";
            }
            String color = VeinManager.getSeverityColor(xray.getRating());
            message.append(String.format(" &8[%s%s&8]", color, rating)).event(new ClickEvent(
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
                message.append(" &a[View]")
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                String.format(commandPrefix + " inv %d", index)))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("&fClick to view!")));
            }
        }
        if (entry.getAction().equals(EntryAction.KILL)) {
            if (APPermission.INV.hasPermission(player) && !entry.getTarget().startsWith("#")) {
                message.append(" &a[View Inv]")
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                String.format(commandPrefix + " l u:%s a:inventory target:death time:%de+-20e",
                                        entry.getTarget(), entry.getTime())))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("&fClick to view!")));
            }
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
            message.append(" &8[&7" + data + "&8]").event(clickToCopy)
                    .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getData()));
        }
        if (entry.world != null && !entry.world.equals("$null") && coords) {
            String tpCommand = String.format(commandPrefix + " tp %d %d %d %s", entry.x, entry.y, entry.z, entry.world);
            if (entry.getAction().getTable().hasLook()) {
                tpCommand += String.format(" %d %d", entry.pitch, entry.yaw);
            }
            message.append("\n                 ").event((HoverEvent) null).event((ClickEvent) null);
            message.append(String.format("&7(x%d/y%d/z%d/%s)", entry.x, entry.y, entry.z, entry.world))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("&7" + tpCommand)));
            if (entry.getAction().getTable().hasLook()) {
                message.append(String.format("&7 (p%s/y%d)", entry.pitch, entry.yaw));
            }
        }
        player.sendMessage(message.create());
    }

    public ArrayList<DbEntry> getEntries() {
        return entries;
    }

    public DbEntry get(int i) {
        return getEntries().get(i);
    }

    public void sendHeader() {
        String headerColor = "&7";
        String line = "&m";
        for (int i = 0; i < 6; i++) {
            line += (char) 65293;
        }
        line += "&7";
        if (plugin.getAPConfig().isPrivate() && new Random().nextDouble() < 0.001) {
            headerColor = "&f"; // The header had these mismatched colors for over a year of development until
            // v1.1.3. This is a tribute to that screw up
        }
        player.sendMessageRaw(headerColor + line + "  &9AuxProtect Results&7  " + line);
    }

    public void showPage(int page) {
        showPage(page, perpage);
    }

    public void showPage(int page, int perpage_) {
        int lastpage = getNumPages(perpage_);
        if (page > lastpage || page < 1) {
            player.sendLang(Language.L.COMMAND__LOOKUP__NOPAGE);
            return;
        }
        perpage = perpage_;
        prevpage = page;
        sendHeader();
        for (int i = (page - 1) * perpage; i < (page) * perpage && i < getEntries().size(); i++) {
            DbEntry en = getEntries().get(i);

            sendEntry(en, i);
        }
        sendArrowKeys(page);
    }

    public void sendEntry(DbEntry entry, int index) {
        sendEntry(plugin, player, entry, index, true, !params.getFlags().contains(Flag.HIDE_COORDS));
    }

    public void sendArrowKeys(int page) {
        String commandPrefix = "/" + plugin.getCommandPrefix();
        ComponentBuilder message = new ComponentBuilder();
        int lastpage = getNumPages(perpage);
        message.append("&7(");
        if (page > 1) {
            message.append("&9&l" + AuxProtectSpigot.LEFT_ARROW + AuxProtectSpigot.LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandPrefix + " l 1:" + perpage))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("&9Jump to First Page")));
            message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
            message.append("&9&l" + AuxProtectSpigot.LEFT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            commandPrefix + " l " + (page - 1) + ":" + perpage))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Last Page")));
        } else {
            message.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ""));
            message.append("&8&l" + AuxProtectSpigot.LEFT_ARROW + AuxProtectSpigot.LEFT_ARROW).event((ClickEvent) null)
                    .event((HoverEvent) null);
            message.append(" ");
            message.append("&8&l" + AuxProtectSpigot.LEFT_ARROW);
        }
        message.append("  ").event((ClickEvent) null).event((HoverEvent) null);
        if (page < lastpage) {
            message.append("&9&l" + AuxProtectSpigot.RIGHT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            commandPrefix + " l " + (page + 1) + ":" + perpage))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Next Page")));
            message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
            message.append("&9&l" + AuxProtectSpigot.RIGHT_ARROW + AuxProtectSpigot.RIGHT_ARROW)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            commandPrefix + " l " + lastpage + ":" + perpage))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Jump to Last Page")));
        } else {
            message.append("&8&l" + AuxProtectSpigot.RIGHT_ARROW).event((ClickEvent) null).event((HoverEvent) null);
            message.append(" ");
            message.append("&8&l" + AuxProtectSpigot.RIGHT_ARROW + AuxProtectSpigot.RIGHT_ARROW);
        }
        message.append("&7)  ").event((ClickEvent) null).event((HoverEvent) null);
        message.append(Language.translate(Language.L.COMMAND__LOOKUP__PAGE_FOOTER, page, getNumPages(perpage), getEntries().size()));
        player.sendMessage(message.create());
    }

    public int getNumPages(int perpage) {
        return (int) Math.ceil(getEntries().size() / (double) perpage);
    }

    public int getSize() {
        return getEntries().size();
    }
}
