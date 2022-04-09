package dev.heliosares.auxprotect.utils;

import java.util.ArrayList;
import java.util.HashMap;

import dev.heliosares.auxprotect.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention;
import net.md_5.bungee.api.chat.hover.content.Text;

public class XraySolver {

	public static BaseComponent[] solvePlaytime(ArrayList<DbEntry> entries, IAuxProtect plugin) {
		ComponentBuilder message = new ComponentBuilder().append("", FormatRetention.NONE);
		HashMap<String, ArrayList<DbEntry>> hash = new HashMap<>();
		for (int i = entries.size() - 1; i >= 0; i--) {
			DbEntry entry = entries.get(i);
			ArrayList<DbEntry> hits = hash.get(entry.getUserUUID());
			if (hits == null) {
				hash.put(entry.getUserUUID(), hits = new ArrayList<>());
			}
			if (entry.getAction() == EntryAction.XRAYCHECK) {
				hits.add(entry);
			}
		}

		for (ArrayList<DbEntry> entries1 : hash.values()) {
			double score = 0;
			for (DbEntry entry : entries1) {
				if (entry.getTarget().equals("1")) {
					score += 1;
				} else if (entry.getTarget().equals("2")) {
					score += 2;
				} else if (entry.getTarget().equals("3")) {
					score += 3;
				}
			}
			if (score >= 6) {
				String user = entries1.get(0).getUser();
				String tooltip = "§4Hits for '" + user + "':\n";
				for (DbEntry entry : entries1) {
					switch (entry.getTarget()) {
					case "1":
						tooltip += "§e";
						break;
					case "2":
						tooltip += "§c";
						break;
					case "3":
						tooltip += "§4";
						break;
					default:
						continue;
					}
					tooltip += "\n" + TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime())
							+ " ago, severity " + entry.getTarget();
				}
				message.append("§4§l" + user + "§c - score " + score + " / 6.0")
						.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(tooltip)))
						.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
								String.format("/ap lookup action:xraycheck target:1,2,3 user:%s", user)));
				message.append("\n").event((ClickEvent) null).event((HoverEvent) null);
			}
		}
		return message.create();
	}
}
