package dev.heliosares.auxprotect.database;

import java.util.ArrayList;
import org.bukkit.command.CommandSender;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.MySender;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class XrayResults extends Results {

	public XrayResults(IAuxProtect plugin, ArrayList<DbEntry> entries, CommandSender player) {
		super(plugin, entries, new MySender(player), "ap");
	}

	public DbEntry getEntry(int index) {
		if (index >= entries.size()) {
			return null;
		}
		return entries.get(index);
	}

	private int index;

	@Override
	public void sendHeader() {
		player.sendMessage("§f------  §9AuxProtect Xray Check Results§7  ------");
	}

	@Override
	public void showPage(int page, int perpage) {
		index = page;
		perpage = 1;
		int lastpage = getNumPages(perpage);
		if (page > lastpage) {
			player.sendMessage(AuxProtectSpigot.getInstance().translate("lookup-nopage"));
			return;
		}
		sendHeader();
		for (int i = (page - 1) * perpage; i < (page) * perpage && i < entries.size(); i++) {
			DbEntry en = entries.get(i);
			this.sendEntry(en, i);

			ComponentBuilder message = new ComponentBuilder();
			String xraycmd = "/ap xray rate %d %d";
			message.append("                §9Set Rating: ");
			message.append("    ").event((ClickEvent) null).event((HoverEvent) null);
			message.append("§a§l[0]").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, String.format(xraycmd, 0, i)))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							new Text("§aRate this vein a 0/3 (no concern)")));
			message.append("    ").event((ClickEvent) null).event((HoverEvent) null);
			message.append("§e§l[1]").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, String.format(xraycmd, 1, i)))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							new Text("§eRate this vein a 1/3 (slightly suspicious)")));
			message.append("    ").event((ClickEvent) null).event((HoverEvent) null);
			message.append("§c§l[2]").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, String.format(xraycmd, 2, i)))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							new Text("§cRate this vein a 2/3 (suspicious, not certain)")));
			message.append("    ").event((ClickEvent) null).event((HoverEvent) null);
			message.append("§4§l[3]").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, String.format(xraycmd, 3, i)))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							new Text("§4Rate this vein a 3/3 (almost certain or entirely certain)")));
			player.sendMessage(message.create());
		}
		this.sendArrowKeys(page);
		return;
	}

	@Override
	public void sendArrowKeys(int page) {
		ComponentBuilder message = new ComponentBuilder();
		int lastpage = getNumPages(perpage);
		message.append("§7(");
		if (page > 1) {
			message.append("§9§l" + AuxProtectSpigot.LEFT_ARROW + AuxProtectSpigot.LEFT_ARROW)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ap xray page 1"))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§9Jump to First Vein")));
			message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
			message.append("§9§l" + AuxProtectSpigot.LEFT_ARROW)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ap xray page " + (page - 1)))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Last Vein")));
		} else {
			message.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ""));
			message.append("§8§l" + AuxProtectSpigot.LEFT_ARROW + AuxProtectSpigot.LEFT_ARROW).event((ClickEvent) null)
					.event((HoverEvent) null);
			message.append(" ");
			message.append("§8§l" + AuxProtectSpigot.LEFT_ARROW);
		}
		message.append("  ").event((ClickEvent) null).event((HoverEvent) null);
		if (page < lastpage) {
			message.append("§9§l" + AuxProtectSpigot.RIGHT_ARROW)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ap xray page " + (page + 1)))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Next Vein")));
			message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
			message.append("§9§l" + AuxProtectSpigot.RIGHT_ARROW + AuxProtectSpigot.RIGHT_ARROW)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ap xray page " + lastpage))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Jump to Last Vein")));
		} else {
			message.append("§8§l" + AuxProtectSpigot.RIGHT_ARROW).event((ClickEvent) null).event((HoverEvent) null);
			message.append(" ");
			message.append("§8§l" + AuxProtectSpigot.RIGHT_ARROW + AuxProtectSpigot.RIGHT_ARROW);
		}
		message.append("§7)  ").event((ClickEvent) null).event((HoverEvent) null);
		message.append(String.format(AuxProtectSpigot.getInstance().translate("lookup-page-footer"), page,
				(int) Math.ceil(entries.size() / (double) perpage), entries.size()));
		player.sendMessage(message.create());
	}

	public int getPage() {
		return index;
	}
}