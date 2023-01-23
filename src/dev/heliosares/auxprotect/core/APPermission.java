package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.adapters.SenderAdapter;

public class APPermission {
    public static final APPermission NONE = new APPermission(null);
    public static final APPermission NOTIFY_INACTIVE = new APPermission("inactive.notify");
    public static final APPermission BYPASS_INACTIVE = new APPermission("inactive.bypass");
    private static final APPermission ROOT = new APPermission("auxprotect");
    public static final APPermission ADMIN = ROOT.dot("admin");
    public static final APPermission PURGE = ROOT.dot("purge");
    public static final APPermission TP = ROOT.dot("tp");
    public static final APPermission HELP = ROOT.dot("help");
    public static final APPermission SQL = ROOT.dot("sql");
    public static final APPermission LOOKUP = ROOT.dot("lookup");
    public static final APPermission LOOKUP_ACTION = LOOKUP.dot("action");
    public static final APPermission LOOKUP_XRAY = LOOKUP.dot("xray");
    public static final APPermission LOOKUP_PLAYTIME = LOOKUP.dot("playtime");
    public static final APPermission LOOKUP_ACTIVITY = LOOKUP.dot("activity");
    public static final APPermission LOOKUP_PLAYBACK = LOOKUP.dot("playback");
    public static final APPermission LOOKUP_RETENTION = LOOKUP.dot("retention");
    public static final APPermission LOOKUP_MONEY = LOOKUP.dot("money");
    public static final APPermission XRAY = ROOT.dot("xray");
    public static final APPermission XRAY_EXEMPT = XRAY.dot("exempt");
    public static final APPermission INV = ROOT.dot("inv");
    public static final APPermission INV_SAVE = INV.dot("save");
    public static final APPermission INV_EDIT = INV.dot("edit");
    public static final APPermission INV_RECOVER = INV.dot("recover");
    public static final APPermission WATCH = ROOT.dot("watch");

    public final String node;

    private APPermission(String node) {
        this.node = node;
    }

    public boolean hasPermission(SenderAdapter player) {
        if (this.equals(NONE)) {
            return true;
        }
        return player.hasPermission(node);
    }

    public boolean hasPermission(org.bukkit.command.CommandSender player) {
        if (this.equals(NONE)) {
            return true;
        }
        return player.hasPermission(node);
    }

    public APPermission dot(String node) {
        return new APPermission(this.node + "." + node);
    }
}
