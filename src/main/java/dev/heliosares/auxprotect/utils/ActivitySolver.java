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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ActivitySolver {
    public static BaseComponent[][] solveActivity(List<DbEntry> entries, long rangeStart, long rangeEnd) {
        ComponentBuilder message = new ComponentBuilder().append("", FormatRetention.NONE);
        LocalDateTime startTime = Instant.ofEpochMilli(rangeStart).atZone(ZoneId.systemDefault()).toLocalDateTime()
                .withSecond(0).withNano(0);
        DateTimeFormatter formatterDateTime = DateTimeFormatter.ofPattern("ddMMM hh:mm a");
        DateTimeFormatter formatterHour = DateTimeFormatter.ofPattern("Ka");
        final long startMillis = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        final int minutes = (int) Math.ceil((rangeEnd - rangeStart) / 1000.0 / 60.0);
        int[] counter = new int[minutes];
        Location[] locations = new Location[minutes];
        Arrays.fill(counter, -1);
        StringBuilder line = new StringBuilder("" + ChatColor.COLOR_CHAR + "7" + ChatColor.COLOR_CHAR + "m");
        line.append(String.valueOf((char) 65293).repeat(6));
        line.append("" + ChatColor.COLOR_CHAR + "7");

        List<BaseComponent[]> components = new ArrayList<>();
        components.add(message.create());
        message = new ComponentBuilder();

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
            locations[minute] = new Location(Bukkit.getWorld(entry.getWorld()), entry.getX(), entry.getY(), entry.getZ());

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
                message.append(line + String.format("" + ChatColor.COLOR_CHAR + "f %s ", time.format(formatterHour)) + line)
                        .event((ClickEvent) null).event((HoverEvent) null);
                components.add(message.create());
                message = new ComponentBuilder();
            }
            if (millis < newestEntryAt) {
                message.append(AuxProtectSpigot.BLOCK + "").event((HoverEvent) null).event((ClickEvent) null);
                message.color(ChatColor.BLACK);
            } else {

                int activity = counter[i];

                String hovertext = "" + ChatColor.COLOR_CHAR + "9" + time.format(formatterDateTime) + "\n";

                if (activity < 0) {
                    hovertext += "" + ChatColor.COLOR_CHAR + "7Offline";
                } else if (activity > 0) {
                    hovertext += "" + ChatColor.COLOR_CHAR + "7Activity Level " + ChatColor.COLOR_CHAR + "9" + activity;
                } else {
                    hovertext += "" + ChatColor.COLOR_CHAR + "cNo Activity";
                }
                ClickEvent clickevent = null;
                if (locations[i] != null) {
                    hovertext += String.format("\n\n" + ChatColor.COLOR_CHAR + "7(x%d/y%d/z%d/%s)\n" + ChatColor.COLOR_CHAR + "7Click to teleport", locations[i].getBlockX(),
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
        components.add(message.create());
        return components.toArray(new BaseComponent[0][0]);
    }
}