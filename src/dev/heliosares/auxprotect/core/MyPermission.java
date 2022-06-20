package dev.heliosares.auxprotect.core;

import org.bukkit.entity.Player;

public class MyPermission {
	private static final MyPermission ROOT = new MyPermission("auxprotect");

	public static final MyPermission ADMIN = ROOT.dot("admin");
	public static final MyPermission PURGE = ROOT.dot("purge");
	public static final MyPermission TP = ROOT.dot("tp");
	public static final MyPermission HELP = ROOT.dot("help");
	public static final MyPermission SQL = ROOT.dot("sql");

	public static final MyPermission LOOKUP = ROOT.dot("lookup");
	public static final MyPermission LOOKUP_ACTION = LOOKUP.dot("action");
	public static final MyPermission LOOKUP_XRAY = LOOKUP.dot("xray");
	public static final MyPermission LOOKUP_XRAY_OVERWRITE = LOOKUP_XRAY.dot("overwrite");
	public static final MyPermission LOOKUP_PLAYTIME = LOOKUP.dot("playtime");
	public static final MyPermission LOOKUP_ACTIVITY = LOOKUP.dot("activity");
	public static final MyPermission LOOKUP_RETENTION = LOOKUP.dot("retention");
	public static final MyPermission LOOKUP_MONEY = LOOKUP.dot("money");

	public static final MyPermission INV = ROOT.dot("inv");
	public static final MyPermission INV_EDIT = INV.dot("edit");
	public static final MyPermission INV_RECOVER = INV.dot("recover");

	public static final MyPermission NOTIFY_INACTIVE = new MyPermission("inactive.notify");
	public static final MyPermission BYPASS_INACTIVE = new MyPermission("inactive.bypass");

	public final String node;

	private MyPermission(String node) {
		this.node = node;
	}

	public boolean hasPermission(Player player) {
		return player.hasPermission(node);
	}

	public boolean hasPermission(MySender player) {
		return player.hasPermission(node);
	}

	public boolean hasPermission(net.md_5.bungee.api.CommandSender player) {
		return player.hasPermission(node);
	}

	public boolean hasPermission(org.bukkit.command.CommandSender player) {
		return player.hasPermission(node);
	}

	public MyPermission dot(String node) {
		return new MyPermission(this.node + "." + node);
	}
}
