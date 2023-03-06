package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PlayTimeSolver {
    public static BaseComponent[] solvePlaytime(List<DbEntry> entries, long startTimeMillis, int hours, String player, final boolean currentlyOnline) {
        ComponentBuilder message = new ComponentBuilder().append("", FormatRetention.NONE);
        if (hours > 840) {
            message.append("" + ChatColor.COLOR_CHAR + "cTime period too long. Max 5 weeks.");
            return message.create();
        }
        StringBuilder line = new StringBuilder("" + ChatColor.COLOR_CHAR + "7" + ChatColor.COLOR_CHAR + "m");
        line.append(String.valueOf((char) 65293).repeat(6)).append("" + ChatColor.COLOR_CHAR + "r");
        player += "'" + (player.toLowerCase().endsWith("s") ? "" : "s");
        message.append(line + "  " + ChatColor.COLOR_CHAR + "9" + player + " Playtime  " + line);
        message.append("\n");
        LocalDateTime startTime = Instant.ofEpochMilli(startTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
                .withMinute(0).withSecond(0).withNano(0);
        long firstTime = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long start = System.currentTimeMillis();
        long login = 0;
        long logout = 0;
        boolean newLogin = false;
        boolean newLogout = false;
        double[] counter = new double[hours + 1];
        int hour = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            DbEntry entry = entries.get(i);
            if (entry.getAction() != EntryAction.SESSION) {
                continue;
            }
            if (login == 0 && !entry.getState()) {
                continue;
            }
            if (entry.getState()) {
                login = entry.getTime();
                newLogin = true;
            } else {
                logout = entry.getTime();
                newLogout = true;
            }
            if (i == 0 && newLogin && !newLogout) {
                if (currentlyOnline) {
                    newLogout = true;
                    logout = start;
                }
            }
            if (newLogin && newLogout) {
                if (logout < login) {
                    newLogout = false;
                    continue;
                }
                while (hour < counter.length) {
                    long hourTime = firstTime + (long) hour * 3600 * 1000;
                    long hourTimeEnd = hourTime + 3600 * 1000;

                    long overlap = 0;
                    boolean time1 = (login < hourTime);
                    boolean time2 = (logout > hourTimeEnd);
                    if (login > hourTimeEnd || logout < hourTime) {
                        hour++;
                        continue;
                    }
                    boolean next = false;
                    if (time1 && time2) {
                        overlap = 3600 * 1000;
                        next = true;
                    } else if (time1) {
                        overlap = logout - hourTime;
                    } else if (time2) {
                        overlap = hourTimeEnd - login;
                        next = true;
                    } else {
                        overlap = logout - login;
                    }
                    counter[hour] += overlap / (3600.0 * 1000.0);
                    if (next) {
                        hour++;
                        continue;
                    }
                    break;
                }

                newLogin = newLogout = false;
            }
        }
        DateTimeFormatter formatterDate = DateTimeFormatter.ofPattern("ddMMM");
        DateTimeFormatter formatterDateTime = DateTimeFormatter.ofPattern("ddMMM hh a");
        for (int i = 0; i < startTime.getHour(); i++) {
            message.append(AuxProtectSpigot.BLOCK + "").color(ChatColor.BLACK);
        }
        double hourCount = 0;
        for (int i = 0; i < counter.length; i++) {
            LocalDateTime time = startTime.plusHours(i);
            double count = counter[i];
            message.append(AuxProtectSpigot.BLOCK + "").event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(
                    "" + ChatColor.COLOR_CHAR + "9" + time.format(formatterDateTime) + "\n" + (Math.round(count * 60.0) + " " + ChatColor.COLOR_CHAR + "8min online"))));
            if (count > 0.99) {
                message.color(ChatColor.of("#ffffff"));
            } else if (count > 0.75) {
                message.color(ChatColor.of("#00ccff"));
            } else if (count > 0.5) {
                message.color(ChatColor.of("#1ecb0d"));
            } else if (count > 0.25) {
                message.color(ChatColor.of("#f9ff17"));
            } else if (count > 0.001) {
                message.color(ChatColor.of("#c50000"));
            } else {
                message.color(ChatColor.of("#4e0808"));
            }
            hourCount += count;
            if (time.getHour() == 23) {
                message.append(" " + time.format(formatterDate)).color(ChatColor.BLUE).event((HoverEvent) null);

                message.append(" (" + (Math.round(hourCount * 10.0) / 10.0) + "h)\n").color(ChatColor.GRAY)
                        .event((HoverEvent) null);
                hourCount = 0;
            }
        }
        for (int i = counter.length; ; i++) {
            LocalDateTime time = startTime.plusHours(i);
            if (time.getHour() == 0) {
                break;
            }
            message.append(AuxProtectSpigot.BLOCK + "").color(ChatColor.BLACK).event((HoverEvent) null);
        }
        message.append(" " + LocalDateTime.now().format(formatterDate)).color(ChatColor.BLUE);
        message.append(" (" + (Math.round(hourCount * 10.0) / 10.0) + "h)").color(ChatColor.GRAY)
                .event((HoverEvent) null);
        return message.create();
    }
}