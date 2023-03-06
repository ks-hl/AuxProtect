package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.XrayEntry;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class XraySolver {

    public static BaseComponent[] solve(List<DbEntry> entries) throws SQLException {
        ComponentBuilder message = new ComponentBuilder().append("", FormatRetention.NONE);
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
                StringBuilder tooltip = new StringBuilder(ChatColor.DARK_RED + "Hits for '" + user + "':\n");
                for (DbEntry entry : entries1) {
                    short severity = ((XrayEntry) entry).getRating();
                    switch (severity) {
                        case 1 -> tooltip.append(ChatColor.YELLOW);
                        case 2 -> tooltip.append(ChatColor.RED);
                        case 3 -> tooltip.append(ChatColor.DARK_RED);
                        default -> {
                            continue;
                        }
                    }
                    tooltip.append("\n").append(TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime())).append(" ago, severity ").append(severity);
                }

                message.append(String.format(ChatColor.DARK_RED + "" + ChatColor.BOLD + "%s" + ChatColor.RED + " - score %d / 6", user, score))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(tooltip.toString())))
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                String.format("/ap lookup action:vein #xray user:%s", user)));
                message.append("\n").event((ClickEvent) null).event((HoverEvent) null);
            }
        }
        return message.create();
    }
}
