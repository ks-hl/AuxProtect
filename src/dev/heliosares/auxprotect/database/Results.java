package dev.heliosares.auxprotect.database;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.IAuxProtect;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.MyPermission;
import dev.heliosares.auxprotect.utils.MySender;
import dev.heliosares.auxprotect.utils.TimeUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class Results {

	protected final ArrayList<DbEntry> entries;
	protected final MySender player;
	protected final DateTimeFormatter formatter;
	private final IAuxProtect plugin;
	public int perpage = 4;
	public int prevpage = 0;
	final String commandPrefix;

	public Results(IAuxProtect plugin, ArrayList<DbEntry> entries, MySender player, String commandPrefix) {
		this.entries = entries;
		this.player = player;
		this.formatter = DateTimeFormatter.ofPattern("ddMMMYY HH:mm:ss.SSS");
		this.plugin = plugin;
		if (!commandPrefix.startsWith("/")) {
			commandPrefix = "/" + commandPrefix;
		}
		this.commandPrefix = commandPrefix;

		boolean allNullWorld = true;
		int count = 0;
		for (DbEntry entry : entries) {
			if (entry.world != null && !entry.world.equals("#null")) {
				allNullWorld = false;
				break;
			}
			if (count++ > 1000) {
				break;
			}
		}
		if (allNullWorld) {
			perpage = 10;
		}
	}

	public DbEntry get(int i) {
		return entries.get(i);
	}

	public void showPage(int page) {
		showPage(page, perpage);
	}

	public void showPage(int page, int perpage_) {
		int lastpage = getLastPage(perpage_);
		if (page > lastpage || page < 1) {
			player.sendMessage(plugin.translate("lookup-nopage"));
			return;
		}
		perpage = perpage_;
		prevpage = page;
		player.sendMessage("§f------  §9AuxProtect Results§7  ------");
		for (int i = (page - 1) * perpage; i < (page) * perpage && i < entries.size(); i++) {
			DbEntry en = entries.get(i);
			ComponentBuilder message = new ComponentBuilder();
			plugin.debug(en.getTarget() + "(" + en.getTargetId() + "): " + en.getTargetUUID());

			message.append(String.format("§7%s ago", TimeUtil.millisToString(System.currentTimeMillis() - en.getTime()),
					en.getUser()))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							new Text(Instant.ofEpochMilli(en.getTime()).atZone(ZoneId.systemDefault()).format(formatter)
									+ "\n§7Click to copy epoch time.")))
					.event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, en.getTime() + "e"));

			// message.append(String.format(" §f- §9%s §f%s §9%s§f", en.getUser(),
			// en.getAction().getText(plugin, en.getState()),
			// en.getTarget())).event((HoverEvent) null);
			message.append(" §f- ").event((HoverEvent) null);
			HoverEvent clickToCopy = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
					new Text("Click to copy to clipboard"));
			message.append("§9" + en.getUser()).event(clickToCopy)
					.event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, en.getUser()));
			message.append(" §f" + en.getAction().getText(plugin, en.getState())).event((HoverEvent) null)
					.event((ClickEvent) null);
			message.append(" §9" + en.getTarget()).event(clickToCopy)
					.event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, en.getTarget()));

			String data = en.getData();
			if (data != null && data.contains(InvSerialization.itemSeparator)) {
				data = data.split(InvSerialization.itemSeparator)[0];
				if (MyPermission.INV.hasPermission(player)) {
					message.append(" §a[View]")
							.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
									String.format(commandPrefix + " inv %d", i)))
							.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§fClick to view!")));
				}
			}
			if (en.getAction().equals(EntryAction.INVENTORY)) {
				if (MyPermission.INV.hasPermission(player)) {
					message.append(" §a[View]")
							.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
									String.format(commandPrefix + " inv %d", i)))
							.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§fClick to view!")));
				}
			} else if (en.getAction().equals(EntryAction.KILL)) {
				if (MyPermission.INV.hasPermission(player) && !en.getTarget().startsWith("#")) {
					message.append(" §a[View Inv]").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
							String.format(commandPrefix + " l u:%s a:inventory target:death before:%de after:%de",
									en.getTarget(), en.getTime() + 50L, en.getTime() - 50L)))
							.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§fClick to view!")));
				}
				message.append(" §7(" + data + ")").event(clickToCopy)
						.event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, en.getData()));
			} else if (data != null && data.length() > 0) {
				message.append(" §7(" + data + ")").event(clickToCopy)
						.event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, en.getData()));
			}
			if (en.world != null && !en.world.equals("$null")) {
				String tpCommand = String.format(commandPrefix + " tp %d %d %d %s", en.x, en.y, en.z, en.world);
				if (en.getAction().getTable().hasLook()) {
					tpCommand += String.format(" %d %d", en.pitch, en.yaw);
				}
				message.append("\n                §7§l^ ").event((HoverEvent) null).event((ClickEvent) null);
				message.append(String.format("§7(x%d/y%d/z%d/%s)", en.x, en.y, en.z, en.world))
						.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
						.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7" + tpCommand)));
				if (en.getAction().getTable().hasLook()) {
					message.append(String.format("§7 (p%s/y%d)", en.pitch, en.yaw));
				}
			}
			player.sendMessage(message.create());
		}

		ComponentBuilder message = new ComponentBuilder();
		message.append("§7(");
		if (page > 1) {
			message.append("§9§l" + AuxProtect.LEFT_ARROW + AuxProtect.LEFT_ARROW)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandPrefix + " l 1:" + perpage))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§9Jump to First Page")));
			message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
			message.append("§9§l" + AuxProtect.LEFT_ARROW)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
							commandPrefix + " l " + (page - 1) + ":" + perpage))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Last Page")));
		} else {
			message.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ""));
			message.append("§8§l" + AuxProtect.LEFT_ARROW + AuxProtect.LEFT_ARROW).event((ClickEvent) null)
					.event((HoverEvent) null);
			message.append(" ");
			message.append("§8§l" + AuxProtect.LEFT_ARROW);
		}
		message.append("  ").event((ClickEvent) null).event((HoverEvent) null);
		if (page < lastpage) {
			message.append("§9§l" + AuxProtect.RIGHT_ARROW)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
							commandPrefix + " l " + (page + 1) + ":" + perpage))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Next Page")));
			message.append(" ").event((ClickEvent) null).event((HoverEvent) null);
			message.append("§9§l" + AuxProtect.RIGHT_ARROW + AuxProtect.RIGHT_ARROW)
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
							commandPrefix + " l " + lastpage + ":" + perpage))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Jump to Last Page")));
		} else {
			message.append("§8§l" + AuxProtect.RIGHT_ARROW).event((ClickEvent) null).event((HoverEvent) null);
			message.append(" ");
			message.append("§8§l" + AuxProtect.RIGHT_ARROW + AuxProtect.RIGHT_ARROW);
		}
		message.append("§7)  ").event((ClickEvent) null).event((HoverEvent) null);
		message.append(String.format(plugin.translate("lookup-page-footer"), page,
				(int) Math.ceil(entries.size() / (double) perpage), entries.size()));
		player.sendMessage(message.create());
		return;
	}

	public int getLastPage(int perpage) {
		return (int) Math.ceil(entries.size() / (double) perpage);
	}

	public int getSize() {
		return entries.size();
	}
}
