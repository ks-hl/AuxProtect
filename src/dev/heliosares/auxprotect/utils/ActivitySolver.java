package dev.heliosares.auxprotect.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention;
import net.md_5.bungee.api.chat.hover.content.Text;

public class ActivitySolver {
	public static BaseComponent[] solveActivity(ArrayList<DbEntry> entries, long lookupStartMillis, int minutes,
			String player) {
		ComponentBuilder message = new ComponentBuilder().append("", FormatRetention.NONE);
		if (minutes > 60 * 12) {
			message.append("Time period too long. Max 12 hours.");
			return message.create();
		}
		LocalDateTime startTime = Instant.ofEpochMilli(lookupStartMillis).atZone(ZoneId.systemDefault())
				.toLocalDateTime().withSecond(0).withNano(0);
		DateTimeFormatter formatterDateTime = DateTimeFormatter.ofPattern("ddMMM hh:mm a");
		DateTimeFormatter formatterHour = DateTimeFormatter.ofPattern("Ka");
		final long startMillis = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		int[] counter = new int[minutes];
		for (int i = 0; i < counter.length; i++) {
			counter[i] = -1;
		}
		String line = "§7§m";
		for (int i = 0; i < 14; i++) {
			line += (char) 65293;
		}
		long lastTime = startMillis;
		for (int i = entries.size() - 1, minute = 0; i >= 0; i--) {
			DbEntry entry = entries.get(i);
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

			lastTime = entry.getTime();
			minute++;
		}
		int shiftMinutes = 0;
		// while ((counter.length + shiftMinutes) % 30 != 0) {
		for (int i = 0; i < (startTime.getMinute() % 30); i++) {
			message.append(AuxProtect.BLOCK + "").color(ChatColor.BLACK);
			shiftMinutes++;
		}

		for (int i = 0; i < counter.length; i++) {
			LocalDateTime time = startTime.plusMinutes(i);
			if (time.getMinute() == 0) {
				message.append(line + String.format("§f %s ", time.format(formatterHour)) + line + "\n")
						.event((ClickEvent) null).event((HoverEvent) null);
			}

			int activity = counter[i];

			String hovertext = "§9" + time.format(formatterDateTime) + "\n";

			if (activity < 0) {
				hovertext += "§7Offline";
			} else if (activity > 0) {
				hovertext += "§7Activity Level §9" + activity;
			} else {
				hovertext += "§cNo Activity";
			}

			message.append(AuxProtect.BLOCK + "")
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hovertext)));
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