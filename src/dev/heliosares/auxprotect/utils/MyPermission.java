package dev.heliosares.auxprotect.utils;

import org.bukkit.entity.Player;

public enum MyPermission {
	ADMIN("admin"), LOOKUP("lookup"), PURGE("purge"), TP("tp"), HELP("help"), LOOKUP_XRAY("lookup.xray"),
	LOOKUP_XRAY_OVERWRITE("lookup.xray.overwrite"), LOOKUP_PLAYTIME("lookup.playtime"),
	LOOKUP_ACTIVITY("lookup.activity"), INV("inv"), INV_EDIT("inv.edit"), INV_RECOVER("inv.recover"),
	LOOKUP_MONEY("lookup.money"), SQL("sql"), NOTIFY_INACTIVE("notify.inactive");

	public final String node;

	private MyPermission(String node) {
		this.node = "auxprotect." + node;
	}

	public boolean hasPermission(Player player) {
		return player.hasPermission(node);
	}

	public boolean hasPermission(org.bukkit.command.CommandSender player) {
		return player.hasPermission(node);
	}

	public boolean hasPermission(MySender player) {
		return player.hasPermission(node);
	}

	public boolean hasPermission(net.md_5.bungee.api.CommandSender player) {
		return player.hasPermission(node);
	}

	public boolean hasPermission(String dot, Player player) {
		return player.hasPermission(node + "." + dot);
	}

	public boolean hasPermission(String dot, org.bukkit.command.CommandSender player) {
		return player.hasPermission(node + "." + dot);
	}

	public boolean hasPermission(String dot, net.md_5.bungee.api.CommandSender player) {
		return player.hasPermission(node + "." + dot);
	}
}
