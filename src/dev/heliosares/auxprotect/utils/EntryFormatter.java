package dev.heliosares.auxprotect.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.bukkit.command.CommandSender;

import dev.heliosares.auxprotect.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class EntryFormatter {
	public static final DateTimeFormatter formatter;

	static {
		formatter = DateTimeFormatter.ofPattern("ddMMMYY HH:mm.ss");
	}

	public static void sendEntry(IAuxProtect plugin, DbEntry en, CommandSender sender) {
		ComponentBuilder message = new ComponentBuilder();

		message.append(String.format("§7%s ago", TimeUtil.millisToString(System.currentTimeMillis() - en.getTime()),
				en.getUser(plugin.getSqlManager())))
				.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(
						"§7" + Instant.ofEpochMilli(en.getTime()).atZone(ZoneId.systemDefault()).format(formatter))));

		message.append(String.format(" §f- §9%s §f%s §9%s§f §7%s", en.getUser(plugin.getSqlManager()),
				plugin.translate(en.getAction().getLang(en.getState())), en.getTarget(),
				(en.getData() != null && en.getData().length() > 0) ? "(" + en.getData() + ")" : "") + "\n")
				.event((HoverEvent) null);

		message.append(String.format("                §7§l^ §7(x%d/y%d/z%d/%s)", en.x, en.y, en.z, en.world))
				.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
						String.format("/ap tp %d %d %d %s", en.x, en.y, en.z, en.world)))
				.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§fClick to Teleport!")));
		sender.spigot().sendMessage(message.create());
	}
}
