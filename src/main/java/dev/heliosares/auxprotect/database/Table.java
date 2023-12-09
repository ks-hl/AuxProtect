package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;

import java.util.HashSet;
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
    AUXPROTECT_LONGTERM(AP_ENTRIES, ACTION_ID, STRING_TARGET), //
    AUXPROTECT_ABANDONED(AP_ENTRIES, LOCATION, ACTION_ID, PRIVATE), //
    AUXPROTECT_XRAY(AP_ENTRIES, DATA, LOCATION, ACTION_ID, PRIVATE), //
    AUXPROTECT_INVENTORY(AP_ENTRIES, DATA, LOCATION, ACTION_ID, BLOB_ID), //
    AUXPROTECT_COMMANDS(AP_ENTRIES, LOCATION, STRING_TARGET), //
    AUXPROTECT_CHAT(AP_ENTRIES, LOCATION, STRING_TARGET), //
    AUXPROTECT_POSITION(AP_ENTRIES, LOCATION, ACTION_ID, BLOB), //
    AUXPROTECT_TOWNY(AP_ENTRIES, DATA, LOCATION, ACTION_ID), //
    AUXPROTECT_TRANSACTIONS(AP_ENTRIES, DATA, LOCATION, ACTION_ID, BLOB_ID), //
    AUXPROTECT_API(AP_ENTRIES, DATA, LOCATION, ACTION_ID), //

    // Utility tables
    AUXPROTECT_INVDIFF(BLOB_ID), AUXPROTECT_UIDS, AUXPROTECT_WORLDS, AUXPROTECT_API_ACTIONS, AUXPROTECT_VERSION, AUXPROTECT_INVBLOB, AUXPROTECT_LASTS, AUXPROTECT_INVDIFFBLOB, AUXPROTECT_USERDATA_PENDINV, AUXPROTECT_TRANSACTIONS_BLOB;

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
        if (plugin.getPlatform() == PlatformType.BUNGEE) {
            return switch (this) {
                case AUXPROTECT_MAIN, AUXPROTECT_COMMANDS, AUXPROTECT_CHAT, AUXPROTECT_LONGTERM, AUXPROTECT_API, AUXPROTECT_UIDS, AUXPROTECT_API_ACTIONS, AUXPROTECT_VERSION ->
                        true;
                default -> false;
            };
        }
        return plugin.getAPConfig().isPrivate() || !characteristics.contains(PRIVATE);
    }

    public boolean hasAPEntries() {
        return characteristics.contains(AP_ENTRIES);
    }

    public boolean hasData() {
        return characteristics.contains(DATA);
    }

    public boolean hasLocation() {
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
        if (this == Table.AUXPROTECT_LONGTERM) {
            return "(time, uid, action_id, target, target_hash)";
        } else if (this == Table.AUXPROTECT_COMMANDS || this == Table.AUXPROTECT_CHAT) {
            if (platform == PlatformType.BUNGEE) {
                return "(time, uid, target)";
            }
            return "(time, uid, world_id, x, y, z, target)";
        } else if (platform == PlatformType.BUNGEE) {
            return "(time, uid, action_id, target_id, data)";
        } else if (this == Table.AUXPROTECT_MAIN || this == Table.AUXPROTECT_SPAM || this == Table.AUXPROTECT_API || this == Table.AUXPROTECT_TOWNY) {
            return "(time, uid, action_id, world_id, x, y, z, target_id, data)";
        } else if (this == Table.AUXPROTECT_TRANSACTIONS) {
            return "(time, uid, action_id, world_id, x, y, z, target_id, data, blobid, quantity, cost, balance, target_id2)";
        } else if (this == Table.AUXPROTECT_INVENTORY) {
            return "(time, uid, action_id, world_id, x, y, z, target_id, data, blobid, qty, damage)";
        } else if (this == Table.AUXPROTECT_ABANDONED) {
            return "(time, uid, action_id, world_id, x, y, z, target_id)";
        } else if (this == Table.AUXPROTECT_POSITION) {
            return "(time, uid, action_id, world_id, x, y, z, increment, pitch, yaw, target_id, ablob)";
        } else if (this == Table.AUXPROTECT_XRAY) {
            return "(time, uid, world_id, x, y, z, target_id, rating, data)";
        }
        return null;
    }

    public int getNumColumns(PlatformType platform) {
        if (this == Table.AUXPROTECT_LONGTERM) {
            return 5;
        }
        if (this == Table.AUXPROTECT_COMMANDS || this == Table.AUXPROTECT_CHAT) {
            if (platform == PlatformType.BUNGEE) {
                return 3;
            }
            return 7;
        }
        if (platform == PlatformType.BUNGEE) return 5;

        return switch (this) {
            case AUXPROTECT_ABANDONED -> 8;
            case AUXPROTECT_MAIN, AUXPROTECT_SPAM, AUXPROTECT_API, AUXPROTECT_XRAY, AUXPROTECT_TOWNY -> 9;
            case AUXPROTECT_POSITION, AUXPROTECT_INVENTORY -> 12;
            case AUXPROTECT_TRANSACTIONS -> 14;
            default -> -1;
        };
    }

    public String getValuesTemplate(PlatformType platform) {
        return getValuesTemplate(getNumColumns(platform));
    }

    public String getSQLCreateString(IAuxProtect plugin) {
        if (!this.hasAPEntries()) {
            return null;
        }
        String stmt = "CREATE TABLE IF NOT EXISTS " + this + " (";
        stmt += "time BIGINT";
        stmt += ",uid integer";
        if (hasActionId()) {
            stmt += ",action_id SMALLINT";
        }
        if (plugin.getPlatform() == PlatformType.SPIGOT && hasLocation()) {
            stmt += ",world_id SMALLINT";
            stmt += ",x INTEGER";
            stmt += ",y SMALLINT";
            stmt += ",z INTEGER";
        }
        if (this == AUXPROTECT_POSITION) {
            stmt += ",increment TINYINT";
        }
        if (hasLook()) {
            stmt += ",pitch SMALLINT";
            stmt += ",yaw SMALLINT";
        }
        if (hasStringTarget()) {
            stmt += ",target ";
            if (this == AUXPROTECT_COMMANDS || this == AUXPROTECT_CHAT) {
                stmt += "LONGTEXT";
            } else {
                stmt += "varchar(255)";
            }
            if (this == AUXPROTECT_LONGTERM) {
                stmt += ",target_hash INT";
            }
        } else {
            stmt += ",target_id integer";
        }
        if (this == AUXPROTECT_XRAY) {
            stmt += ",rating SMALLINT";
        }
        if (hasData()) {
            stmt += ",data LONGTEXT";
        }

        if (hasBlob()) stmt += ",ablob BLOB";
        else if (hasBlobID()) stmt += ",blobid BIGINT";

        if (hasItemMeta()) {
            stmt += ",qty INTEGER";
            stmt += ",damage INTEGER";
        }

        if (this == AUXPROTECT_TRANSACTIONS) {
            stmt += ",quantity SMALLINT";
            stmt += ",cost DECIMAL(12,3)";
            stmt += ",balance DECIMAL(15,3)";
            stmt += ",target_id2 INTEGER";
        }

        stmt += "\n)";

        if (plugin.getSqlManager().isMySQL() && (hasStringTarget() || hasData())) {
            stmt += " CHARACTER SET utf8mb4";
            stmt += " COLLATE utf8mb4_general_ci;";
        }

        return stmt;
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