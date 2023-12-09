package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.PlatformType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EntryAction {
    private static final Map<String, EntryAction> values = new HashMap<>();
    private static final Set<String> usednames = new HashSet<>();

    // PLACEHOLDERS
    public static final EntryAction GROUPING = new EntryAction("grouping", -1001, null);

    // MAIN
    public static final EntryAction LEASH = new EntryAction("leash", 2, 3, Table.AUXPROTECT_MAIN);
    public static final EntryAction SESSION = new EntryAction("session", 4, 5, Table.AUXPROTECT_MAIN);
    public static final EntryAction KICK = new EntryAction("kick", 6, Table.AUXPROTECT_MAIN);

    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public static final EntryAction SHOP_OLD = new EntryAction("shop_old", 8, 9, Table.AUXPROTECT_MAIN);
    // SKIPPED 10/11
    public static final EntryAction MOUNT = new EntryAction("mount", 12, 13, Table.AUXPROTECT_MAIN);
    public static final EntryAction PLUGINLOAD = new EntryAction("pluginload", 14, 15, Table.AUXPROTECT_MAIN);
    public static final EntryAction ENTITY = new EntryAction("entity", 16, 17, Table.AUXPROTECT_MAIN);

    public static final EntryAction ALERT = new EntryAction("alert", 128, Table.AUXPROTECT_MAIN);
    public static final EntryAction RESPAWN = new EntryAction("respawn", 129, Table.AUXPROTECT_MAIN);
    // SKIPPED 130
    // SKIPPED 131
    public static final EntryAction CENSOR = new EntryAction("censor", 132, Table.AUXPROTECT_MAIN);
    public static final EntryAction MSG = new EntryAction("msg", 133, Table.AUXPROTECT_MAIN);
    public static final EntryAction CONSUME = new EntryAction("consume", 134, Table.AUXPROTECT_MAIN);
    public static final EntryAction CONNECT = new EntryAction("connect", 135, Table.AUXPROTECT_MAIN);
    // SKIPPED 135
    public static final EntryAction RECOVER = new EntryAction("recover", 136, Table.AUXPROTECT_MAIN);
    public static final EntryAction MONEY = new EntryAction("money", 137, Table.AUXPROTECT_MAIN);
    public static final EntryAction GAMEMODE = new EntryAction("gamemode", 138, Table.AUXPROTECT_MAIN);
    public static final EntryAction TAME = new EntryAction("tame", 139, Table.AUXPROTECT_MAIN);
    public static final EntryAction JOBS = new EntryAction("jobs", 140, Table.AUXPROTECT_MAIN);
    public static final EntryAction PAY = new EntryAction("pay", 141, Table.AUXPROTECT_MAIN);
    public static final EntryAction LIGHTNING = new EntryAction("lightning", 142, Table.AUXPROTECT_MAIN);
    public static final EntryAction EXPLODE = new EntryAction("explode", 143, Table.AUXPROTECT_MAIN);
    public static final EntryAction NAMETAG = new EntryAction("nametag", 144, Table.AUXPROTECT_MAIN);

    // SPAM
    // SKIPPED 256
    public static final EntryAction HURT = new EntryAction("hurt", 257, Table.AUXPROTECT_SPAM);
    public static final EntryAction INV = new EntryAction("inv", 258, 259, Table.AUXPROTECT_SPAM);
    // SKIPPED 260
    public static final EntryAction KILL = new EntryAction("kill", 261, Table.AUXPROTECT_SPAM);
    public static final EntryAction LAND = new EntryAction("land", 262, Table.AUXPROTECT_SPAM);
    public static final EntryAction ELYTRA = new EntryAction("elytra", 263, 264, Table.AUXPROTECT_SPAM);
    public static final EntryAction ACTIVITY = new EntryAction("activity", 265, Table.AUXPROTECT_SPAM);
    public static final EntryAction TOTEM = new EntryAction("totem", 266, Table.AUXPROTECT_SPAM);
    public static final EntryAction RAIDTRIGGER = new EntryAction("raidtrigger", 267, Table.AUXPROTECT_SPAM);
    public static final EntryAction RAIDSPAWN = new EntryAction("raidspawn", 268, Table.AUXPROTECT_SPAM);

    // ABANDONED
    public static final EntryAction IGNOREABANDONED = new EntryAction("ignoreabandoned", 512, Table.AUXPROTECT_ABANDONED);

    // LONGTERM
    public static final EntryAction IP = new EntryAction("ip", 768, Table.AUXPROTECT_LONGTERM);
    public static final EntryAction USERNAME = new EntryAction("username", 769, Table.AUXPROTECT_LONGTERM);
    public static final EntryAction TOWNYNAME = new EntryAction("townyname", 770, Table.AUXPROTECT_LONGTERM);

    // INVENTORY
    public static final EntryAction INVENTORY = new EntryAction("inventory", 1024, Table.AUXPROTECT_INVENTORY);
    public static final EntryAction LAUNCH = new EntryAction("launch", 1025, Table.AUXPROTECT_INVENTORY);
    public static final EntryAction GRAB = new EntryAction("grab", 1026, Table.AUXPROTECT_INVENTORY);
    public static final EntryAction DROP = new EntryAction("drop", 1027, Table.AUXPROTECT_INVENTORY);
    public static final EntryAction PICKUP = new EntryAction("pickup", 1028, Table.AUXPROTECT_INVENTORY);
    public static final EntryAction AUCTIONLIST = new EntryAction("auctionlist", 1029, Table.AUXPROTECT_INVENTORY);
    public static final EntryAction AUCTIONBUY = new EntryAction("auctionbuy", 1030, Table.AUXPROTECT_INVENTORY);
    public static final EntryAction BREAKITEM = new EntryAction("breakitem", 1032, Table.AUXPROTECT_INVENTORY);

    public static final EntryAction ITEMFRAME = new EntryAction("itemframe", 1152, 1153, Table.AUXPROTECT_INVENTORY);

    public static final EntryAction CRAFT = new EntryAction("craft", 1154, Table.AUXPROTECT_INVENTORY);

    public static final EntryAction ANVIL = new EntryAction("anvil", 1155, Table.AUXPROTECT_INVENTORY);

    public static final EntryAction ENCHANT = new EntryAction("enchant", 1156, Table.AUXPROTECT_INVENTORY);

    public static final EntryAction SMITH = new EntryAction("smith", 1157, Table.AUXPROTECT_INVENTORY);
    public static final EntryAction BUCKET = new EntryAction("bucket", 1158, 1159, Table.AUXPROTECT_INVENTORY);

    // COMMANDS
    public static final EntryAction COMMAND = new EntryAction("command", 1280, Table.AUXPROTECT_COMMANDS);

    // CHAT
    public static final EntryAction CHAT = new EntryAction("chat", 1285, Table.AUXPROTECT_CHAT);

    // POSITION
    public static final EntryAction POS = new EntryAction("pos", 1290, Table.AUXPROTECT_POSITION);
    public static final EntryAction TP = new EntryAction("tp", 1291, 1292, Table.AUXPROTECT_POSITION);

    // XRAY
    public static final EntryAction VEIN = new EntryAction("vein", 1300, Table.AUXPROTECT_XRAY);

    // TOWNY
    public static final EntryAction TOWNCREATE = new EntryAction("towncreate", 1310, Table.AUXPROTECT_TOWNY);
    public static final EntryAction TOWNRENAME = new EntryAction("townrename", 1311, Table.AUXPROTECT_TOWNY);
    public static final EntryAction TOWNDELETE = new EntryAction("towndelete", 1312, Table.AUXPROTECT_TOWNY);
    public static final EntryAction TOWNJOIN = new EntryAction("townjoin", 1313, 1314, Table.AUXPROTECT_TOWNY);
    public static final EntryAction TOWNCLAIM = new EntryAction("townclaim", 1315, 1316, Table.AUXPROTECT_TOWNY);
    //    public static final EntryAction TOWNMERGE = new EntryAction("townmerge", 1317);
    public static final EntryAction TOWNMAYOR = new EntryAction("townmayor", 1318, Table.AUXPROTECT_TOWNY);
    public static final EntryAction TOWNBANK = new EntryAction("townbank", 1319, 1320, Table.AUXPROTECT_TOWNY);
    public static final EntryAction TOWNBALANCE = new EntryAction("townbalance", 1321, Table.AUXPROTECT_TOWNY);

    public static final EntryAction NATIONCREATE = new EntryAction("nationcreate", 1400, Table.AUXPROTECT_TOWNY);
    public static final EntryAction NATIONRENAME = new EntryAction("nationrename", 1401, Table.AUXPROTECT_TOWNY);
    public static final EntryAction NATIONDELETE = new EntryAction("nationdelete", 1402, Table.AUXPROTECT_TOWNY);
    public static final EntryAction NATIONJOIN = new EntryAction("nationjoin", 1403, 1404, Table.AUXPROTECT_TOWNY);
    public static final EntryAction NATIONBANK = new EntryAction("nationbank", 1405, 1406, Table.AUXPROTECT_TOWNY);
    public static final EntryAction NATIONBALANCE = new EntryAction("nationbalance", 1407, Table.AUXPROTECT_TOWNY);

    // TRANSACTIONS

    public static final EntryAction SHOP_SGP = new EntryAction("shop_sgp", 1500, 1501, Table.AUXPROTECT_TRANSACTIONS);
    public static final EntryAction SHOP_ESG = new EntryAction("shop_esg", 1502, 1503, Table.AUXPROTECT_TRANSACTIONS);
    public static final EntryAction SHOP_DS = new EntryAction("shop_ds", 1504, 1505, Table.AUXPROTECT_TRANSACTIONS);
    public static final EntryAction SHOP_CS = new EntryAction("shop_cs", 1506, 1507, Table.AUXPROTECT_TRANSACTIONS);

    public final boolean hasDual;
    public final int id;
    public final int idPos;
    public final String name;
    private final Table table;
    private boolean enabled;
    private boolean lowestpriority;

    private String overridePText;
    private String overrideNText;

    protected EntryAction(String name, int id, int idPos, Table table) {
        this.hasDual = true;
        this.id = id;
        this.idPos = idPos;
        this.name = name;
        this.table = table;

        validateID(table, name, id, idPos);

        enabled = true;
        values.put(name, this);
    }

    protected EntryAction(String name, int id, Table table) {
        this.hasDual = false;
        this.id = id;
        this.idPos = id;
        this.name = name;
        this.table = table;

        validateID(table, name, id, -1);

        enabled = true;
        values.put(name, this);
    }

    protected EntryAction(String key, int nid, int pid, String ntext, String ptext, Table table) {
        this(key, nid, pid, table);
        this.overrideNText = ntext;
        this.overridePText = ptext;
    }

    protected EntryAction(String key, int id, String text, Table table) {
        this(key, id, table);
        this.overrideNText = text;
    }

    public static Collection<EntryAction> values() {
        return Collections.unmodifiableCollection(values.values());
    }

    public static EntryAction getAction(String key) {
        return values.get(key);
    }

    public static EntryAction getAction(Table table, int id) {
        if (id == 0) return null;
        for (EntryAction action : values.values()) {
            if (action.getTable() != table) continue;
            if (action.id == id || action.idPos == id) {
                return action;
            }
        }
        return null;
    }

    private void validateID(Table table, String name, int id, int idPos) throws IllegalArgumentException {
        if (table != null) table.validateID(name, id, idPos);
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
        if (!getTable().exists(plugin)) return false;
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
                    equals(PLUGINLOAD) ||
                    equals(KICK);
        } else if (plugin.getPlatform() == PlatformType.SPIGOT) {
            return !equals(MSG) && !equals(CONNECT);
        }
        throw new UnsupportedOperationException("Unknown platform " + plugin.getPlatform());
    }

    public Table getTable() {
        return table;
    }

    public int getId(boolean state) {
        if (state) {
            return idPos;
        }
        return id;
    }

    public boolean isEnabled() {
        if (!exists()) return false;
        if (super.toString().endsWith("_old")) return false;
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
