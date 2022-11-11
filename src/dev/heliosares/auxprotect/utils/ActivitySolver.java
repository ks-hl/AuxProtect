package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class ActivitySolver {
    public static BaseComponent[] solveActivity(List<DbEntry> entries, long rangeStart, long rangeEnd) {
        ComponentBuilder message = new ComponentBuilder().append("", FormatRetention.NONE);
//		if (minutes > 60 * 12) {
//			message.append("Time period too long. Max 12 hours.");
//			return message.create();
//		}
        LocalDateTime startTime = Instant.ofEpochMilli(rangeStart).atZone(ZoneId.systemDefault()).toLocalDateTime()
                .withSecond(0).withNano(0);
        DateTimeFormatter formatterDateTime = DateTimeFormatter.ofPattern("ddMMM hh:mm a");
        DateTimeFormatter formatterHour = DateTimeFormatter.ofPattern("Ka");
        final long startMillis = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        final int minutes = (int) Math.ceil((rangeEnd - rangeStart) / 1000.0 / 60.0);
        int[] counter = new int[minutes];
        Location[] locations = new Location[minutes];
        Arrays.fill(counter, -1);
        String line = "§7§m";
        for (int i = 0; i < 6; i++) {
            line += (char) 65293;
        }
        line += "§7";
        long lastTime = startMillis;
        for (int i = entries.size() - 1, minute = 0; i >= 0; i--) {
            DbEntry entry = entries.get(i);
            if (entry.getTime() < rangeStart || entry.getTime() > rangeEnd) {
                continue;
            }
            long thisTime = lastTime + 50000L;
            long nextTime = lastTime + 70000L;

            if (entry.getAction() != EntryAction.ACTIVITY) {
                continue;
            }
            if (thisTime > entry.getTime()) {
                continue;
            }

            while (nextTime < entry.getTime()) {
                minute++;
                nextTime += 60000L;
            }

            if (minute >= counter.length) {
                break;
            }

            if (counter[minute] < 0) {
                counter[minute] = 0;
            }

            int activity = Integer.parseInt(entry.getData());
            counter[minute] += activity;
            locations[minute] = new Location(Bukkit.getWorld(entry.world), entry.x, entry.y, entry.z);

            lastTime = entry.getTime();
            minute++;
        }
        int shiftMinutes = 0;
        // while ((counter.length + shiftMinutes) % 30 != 0) {
        for (int i = 0; i < (startTime.getMinute() % 30); i++) {
            message.append(AuxProtectSpigot.BLOCK + "").color(ChatColor.BLACK);
            shiftMinutes++;
        }

        final long newestEntryAt = entries.get(entries.size() - 1).getTime();

        for (int i = 0; i < counter.length; i++) {
            LocalDateTime time = startTime.plusMinutes(i);
            long millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (millis > System.currentTimeMillis()) {
                break;
            }
            if (time.getMinute() == 0) {
                message.append(line + String.format("§f %s ", time.format(formatterHour)) + line + "\n")
                        .event((ClickEvent) null).event((HoverEvent) null);
            }
            if (millis < newestEntryAt) {
                message.append(AuxProtectSpigot.BLOCK + "").event((HoverEvent) null).event((ClickEvent) null);
                message.color(ChatColor.BLACK);
            } else {

                int activity = counter[i];

                String hovertext = "§9" + time.format(formatterDateTime) + "\n";

                if (activity < 0) {
                    hovertext += "§7Offline";
                } else if (activity > 0) {
                    hovertext += "§7Activity Level §9" + activity;
                } else {
                    hovertext += "§cNo Activity";
                }
                ClickEvent clickevent = null;
                if (locations[i] != null) {
                    hovertext += String.format("\n\n§7(x%d/y%d/z%d/%s)\n§7Click to teleport", locations[i].getBlockX(),
                            locations[i].getBlockY(), locations[i].getBlockZ(), locations[i].getWorld().getName());
                    clickevent = new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            String.format("/auxprotect tp %d %d %d %s", locations[i].getBlockX(),
                                    locations[i].getBlockY(), locations[i].getBlockZ(),
                                    locations[i].getWorld().getName()));
                }

                message.append(AuxProtectSpigot.BLOCK + "")
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hovertext))).event(clickevent);
                if (activity >= 20) {
                    message.color(ChatColor.of("#1ecb0d")); // green
                } else if (activity >= 10) {
                    message.color(ChatColor.of("#f9ff17")); // yellow
                } else if (activity > 0) {
                    message.color(ChatColor.of("#c50000")); // Light red
                } else if (activity == 0) {
                    message.color(ChatColor.of("#4e0808")); // Dark red
                } else {
                    message.color(ChatColor.DARK_GRAY);
                }
            }
            if ((i + 1 + shiftMinutes) % 30 == 0 && i < counter.length - 1) {
                message.append("\n");
            }
        }
        /*
         * int[] counter = new int[minutes + 1]; for (int i = 0; i < counter.length;
         * i++) { counter[i] = -1; } int minute = 0; for (int i = entries.size() - 1; i
         * >= 0; i--) { DbEntry entry = entries.get(i); if (entry.getAction() !=
         * EntryAction.ACTIVITY) { continue; } int activity =
         * Integer.parseInt(entry.getData());
         *
         * while (minute < counter.length) { long hourTime = firstTime + minute *
         * 60000L; long hourTimeEnd = hourTime + 60000L;
         *
         * if (entry.getTime() > hourTimeEnd) { minute++; continue; }
         *
         * counter[minute] = activity; break; } } int shiftMinutes = 0; while
         * ((counter.length + shiftMinutes) % 30 != 0) { message.append(AuxProtect.BLOCK
         * + "").color(ChatColor.BLACK); shiftMinutes++; }
         *
         * for (int i = 0; i < counter.length; i++) { LocalDateTime time =
         * startTime.plusMinutes(i);
         *
         * int activity = counter[i];
         *
         * message.append(AuxProtect.BLOCK + "").event(new
         * HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§9" +
         * time.format(formatterDateTime) + "\n" + "Activity Level " + activity))); if
         * (activity >= 20) { message.color(ChatColor.of("#1ecb0d")); // green } else if
         * (activity >= 10) { message.color(ChatColor.of("#f9ff17")); // yellow } else
         * if (activity > 0) { message.color(ChatColor.of("#c50000")); // Light red }
         * else if (activity == 0) { message.color(ChatColor.of("#4e0808")); // Dark red
         * } else { message.color(ChatColor.BLACK); } if ((i + 1 + shiftMinutes) % 30 ==
         * 0 && i < counter.length - 1) { message.append("\n"); } }
         */
        return message.create();
    }
}