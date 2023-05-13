package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.PlatformType;

import java.util.*;

public class EntryAction {
    private static final HashMap<String, EntryAction> values = new HashMap<>();
    private static final Set<Integer> usedids = new HashSet<>();
    private static final Set<String> usednames = new HashSet<>();

    // START PLACEHOLDERS
    public static final EntryAction GROUPING = new EntryAction("grouping", -1001);
    // END PLACEHOLDERS

    // START MAIN (0)
    public static final EntryAction LEASH = new EntryAction("leash", 2, 3);
    public static final EntryAction SESSION = new EntryAction("session", 4, 5);
    public static final EntryAction KICK = new EntryAction("kick", 6);
    public static final EntryAction SHOP = new EntryAction("shop", 8, 9);
    // SKIPPED 10/11
    public static final EntryAction MOUNT = new EntryAction("mount", 12, 13);
    public static final EntryAction PLUGINLOAD = new EntryAction("pluginload", 14, 15);

    public static final EntryAction ALERT = new EntryAction("alert", 128);
    public static final EntryAction RESPAWN = new EntryAction("respawn", 129);
    // SKIPPED 130
    // SKIPPED 131
    public static final EntryAction CENSOR = new EntryAction("censor", 132);
    public static final EntryAction MSG = new EntryAction("msg", 133);
    public static final EntryAction CONSUME = new EntryAction("consume", 134);
    public static final EntryAction CONNECT = new EntryAction("connect", 135);
    // SKIPPED 135
    public static final EntryAction RECOVER = new EntryAction("recover", 136);
    public static final EntryAction MONEY = new EntryAction("money", 137);
    public static final EntryAction GAMEMODE = new EntryAction("gamemode", 138);
    public static final EntryAction TAME = new EntryAction("tame", 139);
    public static final EntryAction JOBS = new EntryAction("jobs", 140);
    public static final EntryAction PAY = new EntryAction("pay", 141);
    public static final EntryAction LIGHTNING = new EntryAction("lightning", 142);
    public static final EntryAction EXPLODE = new EntryAction("explode", 143);
    public static final EntryAction NAMETAG = new EntryAction("nametag", 144);
    // END MAIN (255)

    // START SPAM(256)
    // SKIPPED 256
    public static final EntryAction HURT = new EntryAction("hurt", 257);
    public static final EntryAction INV = new EntryAction("inv", 258, 259);
    // SKIPPED 260
    public static final EntryAction KILL = new EntryAction("kill", 261);
    public static final EntryAction LAND = new EntryAction("land", 262);
    public static final EntryAction ELYTRA = new EntryAction("elytra", 263, 264);
    public static final EntryAction ACTIVITY = new EntryAction("activity", 265);
    public static final EntryAction TOTEM = new EntryAction("totem", 266);
    // END SPAM(511)

    // START IGNOREABANDONED(512)
    public static final EntryAction IGNOREABANDONED = new EntryAction("ignoreabandoned", 512);
    // END IGNOREABANDONED(767)

    // START LONGTERM (768)
    public static final EntryAction IP = new EntryAction("ip", 768);
    public static final EntryAction USERNAME = new EntryAction("username", 769);
    public static final EntryAction TOWNYNAME = new EntryAction("townyname", 770);
    // END LONGTERM (1023)

    // START INVENTORY (1024)
    public static final EntryAction INVENTORY = new EntryAction("inventory", 1024);
    public static final EntryAction LAUNCH = new EntryAction("launch", 1025);
    public static final EntryAction GRAB = new EntryAction("grab", 1026);
    public static final EntryAction DROP = new EntryAction("drop", 1027);
    public static final EntryAction PICKUP = new EntryAction("pickup", 1028);
    public static final EntryAction AUCTIONLIST = new EntryAction("auctionlist", 1029);
    public static final EntryAction AUCTIONBUY = new EntryAction("auctionbuy", 1030);
    //	public static final EntryAction AUCTIONBID = new EntryAction("auctionbid", 1031);
    public static final EntryAction BREAKITEM = new EntryAction("breakitem", 1032);

    public static final EntryAction ITEMFRAME = new EntryAction("itemframe", 1152, 1153);

    public static final EntryAction CRAFT = new EntryAction("craft", 1154);

    public static final EntryAction ANVIL = new EntryAction("anvil", 1155);

    public static final EntryAction ENCHANT = new EntryAction("enchant", 1156);

    public static final EntryAction SMITH = new EntryAction("smith", 1157);
    public static final EntryAction BUCKET = new EntryAction("bucket", 1158, 1159);
    // END INVENTORY(1279)

    // COMMANDS (1280)
    public static final EntryAction COMMAND = new EntryAction("command", 1280);

    // CHAT (1285)
    public static final EntryAction CHAT = new EntryAction("chat", 1285);

    // START POSITION (1290)
    public static final EntryAction POS = new EntryAction("pos", 1290);
    public static final EntryAction TP = new EntryAction("tp", 1291, 1292);
    // END POSITION(1299)

    // START XRAY (1300)
    public static final EntryAction VEIN = new EntryAction("vein", 1300);
    // END XRAY(1309)

    // START TOWNY (1310)
    public static final EntryAction TOWNCREATE = new EntryAction("towncreate", 1310);
    public static final EntryAction TOWNRENAME = new EntryAction("townrename", 1311);
    public static final EntryAction TOWNDELETE = new EntryAction("towndelete", 1312);
    public static final EntryAction TOWNJOIN = new EntryAction("townjoin", 1313, 1314);
    public static final EntryAction TOWNCLAIM = new EntryAction("townclaim", 1315, 1316);
    //    public static final EntryAction TOWNMERGE = new EntryAction("townmerge", 1317);
    public static final EntryAction TOWNMAYOR = new EntryAction("townmayor", 1318);
    public static final EntryAction TOWNBANK = new EntryAction("townbank", 1319, 1320);

    public static final EntryAction NATIONCREATE = new EntryAction("nationcreate", 1400);
    public static final EntryAction NATIONRENAME = new EntryAction("nationrename", 1401);
    public static final EntryAction NATIONDELETE = new EntryAction("nationdelete", 1402);
    public static final EntryAction NATIONJOIN = new EntryAction("nationjoin", 1403, 1404);
    public static final EntryAction NATIONBANK = new EntryAction("nationbank", 1405, 1406);
    // END TOWNY (1499)

    public final boolean hasDual;
    public final int id;
    public final int idPos;
    public final String name;
    private boolean enabled;
    private boolean lowestpriority;

    private String overridePText;
    private String overrideNText;

    protected EntryAction(String name, int id, int idPos) {
        this.hasDual = true;
        this.id = id;
        this.idPos = idPos;
        this.name = name;

        validateID(name, id, idPos);

        enabled = true;
        values.put(name, this);
    }

    protected EntryAction(String name, int id) {
        this.hasDual = false;
        this.id = id;
        this.idPos = id;
        this.name = name;

        validateID(name, id, -1);

        enabled = true;
        values.put(name, this);
    }

    protected EntryAction(String key, int nid, int pid, String ntext, String ptext) {
        this(key, nid, pid);
        this.overrideNText = ntext;
        this.overridePText = ptext;
    }

    protected EntryAction(String key, int id, String text) {
        this(key, id);
        this.overrideNText = text;
    }

    public static Collection<EntryAction> values() {
        return Collections.unmodifiableCollection(values.values());
    }

    public static EntryAction getAction(String key) {
        return values.get(key);
    }

    public static EntryAction getAction(int id) {
        if (id == 0)
            return null;
        for (EntryAction action : values.values()) {
            if (action.id == id || action.idPos == id) {
                return action;
            }
        }
        return null;
    }

    private void validateID(String name, int id, int idPos) throws IllegalArgumentException {
        if (!usedids.add(id)) {
            throw new IllegalArgumentException("Duplicate entry id: " + id + " from action: " + name);
        }
        if (idPos > 0 && !usedids.add(idPos)) {
            throw new IllegalArgumentException("Duplicate entry id: " + idPos + " from action: " + name);
        }
        if (!usednames.add(name)) {
            throw new IllegalArgumentException("Duplicate action name: " + name);
        }
    }

    public String getText(boolean state) {
        if (hasDual) {
            if (state) {
                if (overridePText != null) {
                    return overridePText;
                }
            } else {
                if (overrideNText != null) {
                    return overrideNText;
                }
            }
            return Language.L.ACTIONS.translateSubcategory(getLang(state));
        }
        if (overrideNText != null) {
            return overrideNText;
        }
        return Language.L.ACTIONS.translateSubcategory(getLang(state));
    }

    private String getLang(boolean state) {
        if (hasDual) {
            return toString().toLowerCase() + "." + (state ? "p" : "n");
        }
        return toString().toLowerCase();
    }

    public boolean exists() {
        IAuxProtect plugin = AuxProtectAPI.getInstance();
        if (plugin.getAPConfig().isDemoMode()) {
            if (equals(IP) || equals(SESSION)) {
                return false;
            }
        }
        if (!plugin.getAPConfig().isPrivate()) {
            if (equals(IGNOREABANDONED) || equals(VEIN)) {
                return false;
            }
        }
        if (plugin.getPlatform() == PlatformType.BUNGEE) {
            return equals(MSG) ||
                    equals(COMMAND) ||
                    equals(CHAT) ||
                    equals(IP) ||
                    equals(USERNAME) ||
                    equals(SESSION) ||
                    equals(CONNECT) ||
                    equals(PLUGINLOAD);
        } else if (plugin.getPlatform() == PlatformType.SPIGOT) {
            return !equals(MSG) && !equals(CONNECT);
        }
        throw new UnsupportedOperationException("Unknown platform " + plugin.getPlatform());
    }

    public Table getTable() {
        if (id < 256) return Table.AUXPROTECT_MAIN;
        if (id < 512) return Table.AUXPROTECT_SPAM;
        if (id < 768) return Table.AUXPROTECT_ABANDONED;
        if (id < 1024) return Table.AUXPROTECT_LONGTERM;
        if (id < 1280) return Table.AUXPROTECT_INVENTORY;
        if (equals(COMMAND)) return Table.AUXPROTECT_COMMANDS;
        if (equals(CHAT)) return Table.AUXPROTECT_CHAT;
        if (id < 1300) return Table.AUXPROTECT_POSITION;
        if (id < 1310) return Table.AUXPROTECT_XRAY;
        if (id < 1500) return Table.AUXPROTECT_TOWNY;
        if (id > 1000000) return Table.AUXPROTECT_API;

        throw new IllegalArgumentException("Action with unknown table: " + this + ", id=" + id);
    }

    public int getId(boolean state) {
        if (state) {
            return idPos;
        }
        return id;
    }

    public boolean isEnabled() {
        if (!exists()) {
            return false;
        }
        return enabled;
    }

    public void setEnabled(boolean state) {
        if (equals(USERNAME)) { // Don't allow this to be disabled.
            enabled = true;
            return;
        }
        this.enabled = state;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof EntryAction otherEntry) {
            return this.id == otherEntry.id && this.idPos == otherEntry.idPos;
        }
        return false;
    }

    public boolean isLowestpriority() {
        return lowestpriority;
    }

    public void setLowestpriority(boolean lowestpriority) {
        this.lowestpriority = lowestpriority;
    }

    public String getNode() {
        return APPermission.LOOKUP_ACTION.dot(toString().toLowerCase()).node;
    }

    public boolean hasPermission(SenderAdapter sender) {
        return APPermission.LOOKUP_ACTION.dot(toString().toLowerCase()).hasPermission(sender);
    }
}
