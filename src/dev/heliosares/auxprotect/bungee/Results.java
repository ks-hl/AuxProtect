package dev.heliosares.auxprotect.bungee;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.utils.TimeUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class Results {

	protected final ArrayList<DbEntry> entries;
	protected final CommandSender player;
	protected final boolean showTime;
	protected final DateTimeFormatter formatter;
	private final IAuxProtect plugin;

	public Results(IAuxProtect plugin, ArrayList<DbEntry> entries, CommandSender player, boolean showTime) {
		this.entries = entries;
		this.player = player;
		this.showTime = showTime;
		this.formatter = DateTimeFormatter.ofPattern("ddMMMYY HH:mm.ss");
		this.plugin = plugin;
	}

	public void showPage(int page, int perpage) {
		int lastpage = lastPage(perpage);
		if (page > lastpage) {
			AuxProtectBungee.tell(player, AuxProtect.getInstance().translate("lookup-nopage"));
			return;
		}
		AuxProtectBungee.tell(player, "§f------  §9AuxProtect Results§7  ------");
		for (int i = (page - 1) * perpage; i < (page) * perpage && i < entries.size(); i++) {
			DbEntry en = entries.get(i);
			String line1 = "";
			if (showTime) {
				line1 += "§7" + Instant.ofEpochMilli(en.getTime()).atZone(ZoneId.systemDefault()).format(formatter);
			} else {
				line1 += String.format("§7%s ago", TimeUtil.millisToString(System.currentTimeMillis() - en.getTime()),
						en.getUser(plugin.getSqlManager()));
			}
			line1 += String.format(" §f- §9%s §f%s §9%s§f §7%s", en.getUser(plugin.getSqlManager()),
					plugin.translate(en.getAction().getLang(en.getState())), en.getTarget(),
					(en.getData() != null && en.getData().length() > 0) ? "(" + en.getData() + ")" : "");
			AuxProtectBungee.tell(player, line1);
			if (en.world != null && !en.world.equals("$null")) {
				ComponentBuilder message = new ComponentBuilder();
				message.append(String.format("                §7§l^ §7(x%d/y%d/z%d/%s)", en.x, en.y, en.z, en.world))
						.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
								String.format("/apb tp %d %d %d %s", en.x, en.y, en.z, en.world)))
						.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§fClick to Teleport!")));
				player.sendMessage(message.create());
			}
		}
		// ◄►
		ComponentBuilder message = new ComponentBuilder();
		message.append("§7(");
		if (page > 1) {
			message.append("§9§l◄◄").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/apb l 1:" + perpage))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§9Jump to First Page")));
			message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
			message.append("§9§l◄")
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/apb l " + (page - 1) + ":" + perpage))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Last Page")));
		} else {
			message.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ""));
			message.append("§8§l◄◄").event((ClickEvent) null).event((HoverEvent) null);
			message.append(" ");
			message.append("§8§l◄");
		}
		message.append("  ").event((ClickEvent) null).event((HoverEvent) null);
		if (page < lastpage) {
			message.append("§9§l►")
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/apb l " + (page + 1) + ":" + perpage))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Next Page")));
			message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
			message.append("§9§l►►")
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/apb l " + lastpage + ":" + perpage))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Jump to Last Page")));
		} else {
			message.append("§8§l►").event((ClickEvent) null).event((HoverEvent) null);
			message.append(" ");
			message.append("§8§l►►");
		}
		message.append("§7)  ").event((ClickEvent) null).event((HoverEvent) null);
		message.append(String.format(plugin.translate("lookup-page-footer"), page,
				(int) Math.ceil(entries.size() / (double) perpage), entries.size()));
		player.sendMessage(message.create());
		return;
	}

	int lastPage(int perpage) {
		return (int) Math.ceil(entries.size() / (double) perpage);
	}
}