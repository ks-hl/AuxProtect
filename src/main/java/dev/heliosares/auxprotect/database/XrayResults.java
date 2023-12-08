package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.spigot.VeinManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.sql.SQLException;

public class XrayResults {

    public static void sendHeader(SenderAdapter sender) {
        sender.sendMessageRaw("&f------  &9AuxProtect Xray Check Results&7  ------"); // TODO lang
    }

    public static void sendEntry(AuxProtectSpigot plugin, SenderAdapter sender, XrayEntry en, boolean auto) throws SQLException {
        sendHeader(sender);

        Results.sendEntry(plugin, sender, en, -1, true, true);

        ComponentBuilder message = new ComponentBuilder();
        String xraycmd = "/ap xray rate %de %d";
        if (auto) {
            xraycmd += " -auto";
        }
        String descFormat = "Rate this vein a %d/3 (%s)";

        for (int sev = -1; sev <= 3; sev++) {
            String color = VeinManager.getSeverityColor(sev);
            String desc = VeinManager.getSeverityDescription(sev);
            message.append(String.format("%s" + ChatColor.COLOR_CHAR + "l[%s]", color, sev == -1 ? "Clear" : (sev)))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, String.format(xraycmd, en.getTime(), sev)))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(color + String.format(descFormat, sev, desc))));
            message.append("    ").event((ClickEvent) null).event((HoverEvent) null);
        }

        message.append(ChatColor.COLOR_CHAR + "7" + ChatColor.COLOR_CHAR + "l[Skip]")
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ap xray skip " + en.getTime() + "e" + (auto ? " -auto" : "")))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("" + ChatColor.COLOR_CHAR + "7Click to skip this entry.")));

        if (APPermission.LOOKUP_XRAY_BULK.hasPermission(sender)) {
            message.append("    ").event((ClickEvent) null).event((HoverEvent) null);

            message.append("" + ChatColor.COLOR_CHAR + "7" + ChatColor.COLOR_CHAR + "l[0-All]")
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ap xray zero " + en.getUserUUID().substring(1)))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.COLOR_CHAR + "7Rate all entries from " + en.getUser() + " as 0.")));
        }

        sender.sendMessage(message.create());

        sendArrowKeys(sender, plugin.getVeinManager().size());
    }

    public static void sendArrowKeys(SenderAdapter sender, int size) {
        sender.sendMessageRaw(String.format("&9%d&7 remaining.", size)); // TODO lang
    }
}