package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static dev.heliosares.auxprotect.database.Table.Characteristic.ACTION_ID;
import static dev.heliosares.auxprotect.database.Table.Characteristic.AP_ENTRIES;
import static dev.heliosares.auxprotect.database.Table.Characteristic.BLOB;
import static dev.heliosares.auxprotect.database.Table.Characteristic.BLOB_ID;
import static dev.heliosares.auxprotect.database.Table.Characteristic.DATA;
import static dev.heliosares.auxprotect.database.Table.Characteristic.LOCATION;
import static dev.heliosares.auxprotect.database.Table.Characteristic.PRIVATE;
import static dev.heliosares.auxprotect.database.Table.Characteristic.STRING_TARGET;

public enum Table {
    AUXPROTECT_MAIN(AP_ENTRIES, DATA, LOCATION, ACTION_ID), //
    AUXPROTECT_SPAM(AP_ENTRIES, DATA, LOCATION, ACTION_ID), //
    AUXPROTECT_LONGTERM(AP_ENTRIES, ACTION_ID), //
    AUXPROTECT_ABANDONED(AP_ENTRIES, LOCATION, ACTION_ID, PRIVATE), //
    AUXPROTECT_XRAY(AP_ENTRIES, DATA, LOCATION, PRIVATE), //
    AUXPROTECT_INVENTORY(AP_ENTRIES, DATA, LOCATION, ACTION_ID, BLOB_ID), //
    AUXPROTECT_COMMANDS(AP_ENTRIES, LOCATION, STRING_TARGET), //
    AUXPROTECT_CHAT(AP_ENTRIES, LOCATION, DATA), //
    AUXPROTECT_POSITION(AP_ENTRIES, LOCATION, ACTION_ID, BLOB), //
    AUXPROTECT_TOWNY(AP_ENTRIES, DATA, LOCATION, ACTION_ID), //
    AUXPROTECT_TRANSACTIONS(AP_ENTRIES, DATA, LOCATION, ACTION_ID, BLOB_ID), //
    AUXPROTECT_API(AP_ENTRIES, DATA, LOCATION, ACTION_ID), //

    // Utility tables
    AUXPROTECT_INVDIFF(BLOB_ID), AUXPROTECT_UIDS, AUXPROTECT_WORLDS, AUXPROTECT_API_ACTIONS, AUXPROTECT_VERSION, AUXPROTECT_MIGRATION_TASKS, AUXPROTECT_INVBLOB, AUXPROTECT_LASTS, AUXPROTECT_INVDIFFBLOB, AUXPROTECT_USERDATA_PENDINV, AUXPROTECT_TRANSACTIONS_BLOB, AUXPROTECT_ENUM_IDS;

    public static final long MIN_PURGE_INTERVAL = 1000L * 60L * 60L * 24L * 14L;
    final ConcurrentLinkedQueue<DbEntry> queue = new ConcurrentLinkedQueue<>();
    private final Set<Characteristic> characteristics;
    private final Set<Integer> usedids = new HashSet<>();
    private long autopurgeinterval;

    Table(Characteristic... characteristics) {
        this.characteristics = Set.of(characteristics);
    }

    public static String getValuesTemplate(int numColumns) {
        if (numColumns <= 0) {
            return null;
        }
        StringBuilder output = new StringBuilder("(");
        for (int i = 0; i < numColumns; i++) {
            if (i > 0) {
                output.append(", ");
            }
            output.append("?");
        }
        output.append(")");
        return output.toString();
    }

    @Override
    public String toString() {
        if (SQLManager.getInstance() == null) return super.toString().toLowerCase();
        return SQLManager.getInstance().getTablePrefix() + super.toString().toLowerCase();
    }

    public boolean exists(IAuxProtect plugin) {
        if (plugin.getPlatform().getLevel() == PlatformType.Level.PROXY) {
            return switch (this) {
                case AUXPROTECT_MAIN, AUXPROTECT_COMMANDS, AUXPROTECT_CHAT, AUXPROTECT_LONGTERM, AUXPROTECT_API,
                     AUXPROTECT_UIDS, AUXPROTECT_API_ACTIONS, AUXPROTECT_VERSION, AUXPROTECT_MIGRATION_TASKS, AUXPROTECT_ENUM_IDS -> true;
                default -> false;
            };
        }
        return plugin.isPrivate() || !characteristics.contains(PRIVATE);
    }

    public boolean hasAPEntries() {
        return characteristics.contains(AP_ENTRIES);
    }

    public boolean hasData() {
        return characteristics.contains(DATA);
    }

    public boolean hasLocation(PlatformType platform) {
        if (platform.getLevel() == PlatformType.Level.PROXY) return false;
        return characteristics.contains(LOCATION);
    }

    public boolean hasLook() {
        return this == Table.AUXPROTECT_POSITION;
    }

    public boolean hasActionId() {
        return characteristics.contains(ACTION_ID);
    }

    public boolean hasStringTarget() {
        return characteristics.contains(STRING_TARGET);
    }

    public boolean canPurge() {
        if (this == Table.AUXPROTECT_LONGTERM) return false;
        return this.hasAPEntries();
    }

    public String getValuesHeader(PlatformType platform) {
        return "(" + getColumns(platform).keySet().stream().reduce((a, b) -> a + ", " + b).orElse("") + ")";
    }

    public int getNumColumns(PlatformType platform) {
        return getColumns(platform).size();
    }

    public String getValuesTemplate(PlatformType platform) {
        return getValuesTemplate(getNumColumns(platform));
    }

    public String getSQLCreateString(IAuxProtect plugin) {
        if (!this.hasAPEntries()) {
            return null;
        }
        String stmt = "CREATE TABLE IF NOT EXISTS " + this + " (";

        stmt += getColumns(plugin.getPlatform()).entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).reduce((a, b) -> a + ", " + b).orElse("");

        stmt += ")";

        if (plugin.getSqlManager().isMySQL() && (hasStringTarget() || hasData())) {
            stmt += " CHARACTER SET utf8mb4";
            stmt += " COLLATE utf8mb4_general_ci;";
        }

        return stmt;
    }

    public LinkedHashMap<String, String> getColumns(PlatformType platform) {
        var map = new LinkedHashMap<String, String>();

        map.put("time", "BIGINT PRIMARY KEY");
        map.put("uid", "INTEGER");

        if (hasActionId()) {
            map.put("action_id", "SMALLINT");
        }

        if (hasLocation(platform)) {
            map.put("world_id", "SMALLINT");
            map.put("x", "INTEGER");
            map.put("y", "SMALLINT");
            map.put("z", "INTEGER");

            if (this == AUXPROTECT_POSITION) {
                map.put("increment", "TINYINT");
            }

            if (hasLook()) {
                map.put("pitch", "SMALLINT");
                map.put("yaw", "SMALLINT");
            }
        }

        if (hasStringTarget()) {
            map.put("target", "LONGTEXT");
        } else {
            map.put("target_id", "INTEGER");
        }

        if (this == AUXPROTECT_XRAY) {
            map.put("rating", "SMALLINT");
        }

        if (hasData()) {
            map.put("data", "LONGTEXT");
        }

        if (hasBlob()) {
            map.put("ablob", "BLOB");
        } else if (hasBlobID()) {
            map.put("blobid", "BIGINT");
        }

        if (hasItemMeta()) {
            map.put("qty", "INTEGER");
            map.put("damage", "INTEGER");
        }

        if (this == AUXPROTECT_TRANSACTIONS) {
            map.put("quantity", "SMALLINT");
            map.put("cost", "DECIMAL(12,3)");
            map.put("balance", "DECIMAL(15,3)");
            map.put("target_id2", "INTEGER");
        }

        return map;
    }


    public boolean hasBlobID() {
        return characteristics.contains(BLOB_ID);
    }

    public boolean hasBlob() {
        return characteristics.contains(BLOB);
    }

    public boolean hasItemMeta() {
        return this == AUXPROTECT_INVENTORY || this == AUXPROTECT_INVDIFF;
    }

    void validateID(String name, int id, int idPos) throws IllegalArgumentException {
        if (!usedids.add(id)) {
            throw new IllegalArgumentException("Duplicate entry id: " + id + " from action: " + name);
        }
        if (idPos > 0 && !usedids.add(idPos)) {
            throw new IllegalArgumentException("Duplicate entry id: " + idPos + " from action: " + name);
        }
        if (idPos > 0 && idPos != id + 1) {
            throw new IllegalArgumentException("idPos is not id+1: id=" + id + ", idPos=" + idPos + " for action: " + name);
        }
    }

    public List<String> getIndexStatements(PlatformType platform) {
        return Arrays.stream(Index.values()).filter(i -> i.exists(this, platform)).map(i -> i.getCreate(this)).toList();
    }

    @RequiredArgsConstructor
    public enum Index {
        UID(
                "idx_%s_action_uid",
                List.of("action_id", "uid")
        ),
        TIME(
                "uidx_%s_time_action",
                List.of("time", "action_id")
        ),
        XZ(
                "idx_%s_action_xz",
                List.of("action_id", "x", "z")
        );

        private final String name;
        private final List<String> columns;

        public String getName(Table table) {
            return String.format(this.name, table.toString());
        }

        public boolean exists(Table table, PlatformType platform) {
            if (this == XZ) {
                return table.hasLocation(platform);
            }
            return true;
        }

        public String getCreate(Table table) {
            String create = "CREATE ";
            if (this == TIME) create += "UNIQUE ";
            create += "INDEX IF NOT EXISTS " + getName(table) + " ON " + table + " " + getColumns(table);
            return create;
        }

        private String getColumns(Table table) {
            List<String> columns = new ArrayList<>(this.columns);
            if (!table.hasActionId()) {
                columns.remove("action_id");
            }
            return "(" + columns.stream().reduce((a, b) -> a + ", " + b).orElse("") + ")";
        }
    }

    public enum Characteristic {
        AP_ENTRIES, DATA, LOCATION, LOOK, ACTION_ID, STRING_TARGET, BLOB_ID, BLOB, PRIVATE
    }

    public String getName() {
        return super.toString().toLowerCase();
    }

    public long getAutoPurgeInterval() {
        if (!canPurge()) {
            throw new UnsupportedOperationException();
        }
        return autopurgeinterval;
    }

    public void setAutoPurgeInterval(long autopurgeinterval) {
        if (!canPurge()) {
            throw new UnsupportedOperationException();
        }
        this.autopurgeinterval = autopurgeinterval;
    }
}