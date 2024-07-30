package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.adapters.message.ClickEvent;
import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.exceptions.BusyException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class XraySolver {

    public static GenericBuilder solve(IAuxProtect plugin, List<DbEntry> entries) throws SQLException, BusyException {
        GenericBuilder message = new GenericBuilder(plugin);
        HashMap<String, ArrayList<DbEntry>> hash = new HashMap<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            DbEntry entry = entries.get(i);
            ArrayList<DbEntry> hits = hash.computeIfAbsent(entry.getUserUUID(), k -> new ArrayList<>());
            if (entry.getAction().equals(EntryAction.VEIN)) {
                hits.add(entry);
            }
        }

        for (ArrayList<DbEntry> entries1 : hash.values()) {
            int score = 0;
            for (DbEntry entry : entries1) {
                short rating = ((XrayEntry) entry).getRating();
                if (rating > 0) {
                    score += rating;
                }
            }
            if (score >= 6 || hash.size() == 1) {
                String user = entries1.get(0).getUser();
                StringBuilder tooltip = new StringBuilder(GenericTextColor.DARK_RED + "Hits for '" + user + "':\n");
                for (DbEntry entry : entries1) {
                    short severity = ((XrayEntry) entry).getRating();
                    switch (severity) {
                        case 1 -> tooltip.append(GenericTextColor.YELLOW);
                        case 2 -> tooltip.append(GenericTextColor.RED);
                        case 3 -> tooltip.append(GenericTextColor.DARK_RED);
                        default -> {
                            continue;
                        }
                    }
                    tooltip.append("\n").append(TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime())).append(" ago, severity ").append(severity);
                }

                message.append(String.format(GenericTextColor.DARK_RED + "§l" + "%s" + GenericTextColor.RED + " - score %d / 6", user, score));
                message.hover(tooltip.toString());
                message.click(new ClickEvent(ClickEvent.Action.RUN_COMMAND, String.format("/ap lookup action:vein #xray user:%s", user)));
                message.append("\n");
            }
        }
        return message;
    }
}
