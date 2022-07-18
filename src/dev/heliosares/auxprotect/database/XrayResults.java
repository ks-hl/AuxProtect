package dev.heliosares.auxprotect.database;

import org.bukkit.entity.Player;

import dev.heliosares.auxprotect.core.MySender;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.spigot.VeinManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class XrayResults {

	public static void sendHeader(MySender sender) {
		sender.sendMessage("§f------  §9AuxProtect Xray Check Results§7  ------");
	}

	public static void sendEntry(AuxProtectSpigot plugin, Player player, XrayEntry en, boolean auto) {
		MySender sender = new MySender(player);
		sendHeader(sender);

		Results.sendEntry(plugin, sender, en, -1);

		ComponentBuilder message = new ComponentBuilder();
		String xraycmd = "/ap xray rate %de %d";
		if (auto) {
			xraycmd += " -auto";
		}
		String descFormat = "Rate this vein a %d/3 (%s)";

		for (int sev = -1; sev <= 3; sev++) {
			String color = VeinManager.getSeverityColor(sev);
			String desc = VeinManager.getSeverityDescription(sev);
			message.append(String.format("%s§l[%s]", color, sev == -1 ? "Clear" : ("" + sev)))
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, String.format(xraycmd, en.getTime(), sev)))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							new Text(color + String.format(descFormat, sev, desc))));
			message.append("    ").event((ClickEvent) null).event((HoverEvent) null);
		}

		message.append("§7§l[Skip]")
				.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
						"/ap xray skip " + en.getTime() + "e" + (auto ? " -auto" : "")))
				.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7Click to skip this entry.")));
		player.spigot().sendMessage(message.create());

		sendArrowKeys(sender, plugin.getVeinManager().size());
	}

	public static void sendArrowKeys(MySender sender, int size) {
		sender.sendMessage(String.format("§9%d§7 remaining.", size));
	}
}