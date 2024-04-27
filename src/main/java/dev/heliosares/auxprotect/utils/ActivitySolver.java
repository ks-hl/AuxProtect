package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.adapters.message.ClickEvent;
import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericComponent;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class ActivitySolver {
    public static GenericBuilder solveActivity(List<DbEntry> entries, long rangeStart, long rangeEnd) {
        GenericBuilder message = new GenericBuilder();
        LocalDateTime startTime = Instant.ofEpochMilli(rangeStart).atZone(ZoneId.systemDefault()).toLocalDateTime()
                .withSecond(0).withNano(0);
        DateTimeFormatter formatterDateTime = DateTimeFormatter.ofPattern("ddMMM hh:mm a");
        DateTimeFormatter formatterHour = DateTimeFormatter.ofPattern("Ka");
        final long startMillis = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        final int minutes = (int) Math.ceil((rangeEnd - rangeStart) / 1000.0 / 60.0);
        int[] counter = new int[minutes];
        Location[] locations = new Location[minutes];
        Arrays.fill(counter, -1);
        GenericComponent line = new GenericComponent(String.valueOf((char) 65293).repeat(6));
        line.color(GenericTextColor.GRAY);
        line.strikethrough(true);

        message.builderBreak();

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
            message.append(AuxProtectSpigot.BLOCK + "").color(GenericTextColor.BLACK);
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
                message.append(line).append(String.format("" + GenericTextColor.COLOR_CHAR + "f %s ", time.format(formatterHour))).append(line);
                message.builderBreak();
            }
            if (millis < newestEntryAt) {
                message.append(AuxProtectSpigot.BLOCK).color(GenericTextColor.BLACK);
            } else {

                int activity = counter[i];

                String hovertext = GenericTextColor.COLOR_CHAR + "9" + time.format(formatterDateTime) + "\n";

                if (activity < 0) {
                    hovertext += "" + GenericTextColor.COLOR_CHAR + "7Offline";
                } else if (activity > 0) {
                    hovertext += GenericTextColor.COLOR_CHAR + "7Activity Level " + GenericTextColor.COLOR_CHAR + "9" + activity;
                } else {
                    hovertext += "" + GenericTextColor.COLOR_CHAR + "cNo Activity";
                }
                ClickEvent clickevent = null;
                if (locations[i] != null) {
                    hovertext += String.format("\n\n" + GenericTextColor.COLOR_CHAR + "7(x%d/y%d/z%d/%s)\n" + GenericTextColor.COLOR_CHAR + "7Click to teleport", locations[i].getBlockX(),
                            locations[i].getBlockY(), locations[i].getBlockZ(), locations[i].getWorld().getName());
                    clickevent = new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            String.format("/auxprotect tp %d %d %d %s", locations[i].getBlockX(),
                                    locations[i].getBlockY(), locations[i].getBlockZ(),
                                    locations[i].getWorld().getName()));
                }

                message.append(AuxProtectSpigot.BLOCK + "")
                        .hover(hovertext).event(clickevent);
                if (activity >= 20) {
                    message.color(new GenericTextColor("#1ecb0d")); // green
                } else if (activity >= 10) {
                    message.color(new GenericTextColor("#f9ff17")); // yellow
                } else if (activity > 0) {
                    message.color(new GenericTextColor("#c50000")); // Light red
                } else if (activity == 0) {
                    message.color(new GenericTextColor("#4e0808")); // Dark red
                } else {
                    message.color(GenericTextColor.DARK_GRAY);
                }
            }
            if ((i + 1 + shiftMinutes) % 30 == 0 && i < counter.length - 1) {
                message.append("\n");
            }
        }
        return message;
    }
}