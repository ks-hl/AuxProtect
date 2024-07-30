package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.message.ClickEvent;
import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.spigot.VeinManager;

import java.sql.SQLException;

public class XrayResults {

    public static void sendHeader(SenderAdapter<?, ?> sender) {
        sender.sendMessageRaw("&f------  &9AuxProtect Xray Check Results&7  ------"); // TODO lang
    }

    public static void sendEntry(AuxProtectSpigot plugin, SenderAdapter<?, ?> sender, XrayEntry en, boolean auto) throws SQLException, BusyException {
        sendHeader(sender);

        Results.sendEntry(plugin, sender, en, -1, true, true, true);

        final GenericBuilder message = new GenericBuilder(plugin);
        String xraycmd = "/ap xray rate %de %d";
        if (auto) {
            xraycmd += " -auto";
        }
        String descFormat = "Rate this vein a %d/3 (%s)";

        for (int sev = -1; sev <= 3; sev++) {
            String color = VeinManager.getSeverityColor(sev).toString();
            String desc = VeinManager.getSeverityDescription(sev);
            message.append(String.format("%s" + GenericTextColor.COLOR_CHAR + "l[%s]", color, sev == -1 ? "Clear" : (sev)))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, String.format(xraycmd, en.getTime(), sev)))
                    .hover(color + String.format(descFormat, sev, desc));
            message.append("    ");
        }

        message.append(GenericTextColor.GRAY + "" + GenericTextColor.COLOR_CHAR + "l[Skip]")
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ap xray skip " + en.getTime() + "e" + (auto ? " -auto" : "")))
                .hover(GenericTextColor.GRAY + "Click to skip this entry.");

        if (APPermission.LOOKUP_XRAY_BULK.hasPermission(sender)) {
            message.append("    ");

            message.append("[0-All]")
                    .color(GenericTextColor.GRAY)
                    .bold(true)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ap xray zero " + en.getUserUUID().substring(1)))
                    .hover(GenericTextColor.GRAY + "Rate all entries from " + en.getUser() + " as 0.");
        }

        message.send(sender);

        sendArrowKeys(sender, plugin.getVeinManager().size());
    }

    public static void sendArrowKeys(SenderAdapter<?, ?> sender, int size) {
        sender.sendMessageRaw(String.format("&9%d&7 remaining.", size)); // TODO lang
    }
}