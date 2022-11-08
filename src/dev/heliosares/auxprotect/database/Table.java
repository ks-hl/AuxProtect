package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;

import java.util.concurrent.ConcurrentLinkedQueue;

public enum Table {
    AUXPROTECT_MAIN, AUXPROTECT_SPAM, AUXPROTECT_LONGTERM, AUXPROTECT_ABANDONED, AUXPROTECT_XRAY, AUXPROTECT_INVENTORY,
    AUXPROTECT_COMMANDS, AUXPROTECT_POSITION, AUXPROTECT_TOWNY,

    AUXPROTECT_API, AUXPROTECT_UIDS, AUXPROTECT_WORLDS, AUXPROTECT_API_ACTIONS, AUXPROTECT_VERSION, AUXPROTECT_INVBLOB, AUXPROTECT_LASTS,
    AUXPROTECT_INVDIFF, AUXPROTECT_INVDIFFBLOB, AUXPROTECT_USERDATA_PENDINV;

    public static final long MIN_PURGE_INTERVAL = 1000L * 60L * 60L * 24L * 14L;
    final ConcurrentLinkedQueue<DbEntry> queue = new ConcurrentLinkedQueue<>();
    private long autopurgeinterval;

    public static String getValuesTemplate(int numColumns) {
        if (numColumns <= 0) {
            return null;
        }
        String output = "(";
        for (int i = 0; i < numColumns; i++) {
            if (i > 0) {
                output += ", ";
            }
            output += "?";
        }
        output += ")";
        return output;
    }

    @Override
    public String toString() {
        return SQLManager.getTablePrefix() + super.toString().toLowerCase();
    }

    public String getName() {
        return super.toString().toLowerCase();
    }

    public boolean exists(IAuxProtect plugin) {
        if (plugin.getPlatform() == PlatformType.BUNGEE) {
            switch (this) {
                case AUXPROTECT_MAIN:
                case AUXPROTECT_COMMANDS:
                case AUXPROTECT_LONGTERM:
                case AUXPROTECT_API:
                case AUXPROTECT_UIDS:
                case AUXPROTECT_API_ACTIONS:
                case AUXPROTECT_VERSION:
                    return true;
                default:
                    return false;
            }
        }
        return plugin.getAPConfig().isPrivate() || this != AUXPROTECT_ABANDONED;
    }

    public boolean hasAPEntries() {
        switch (this) {
            case AUXPROTECT_MAIN:
            case AUXPROTECT_SPAM:
            case AUXPROTECT_LONGTERM:
            case AUXPROTECT_ABANDONED:
            case AUXPROTECT_XRAY:
            case AUXPROTECT_INVENTORY:
            case AUXPROTECT_COMMANDS:
            case AUXPROTECT_POSITION:
            case AUXPROTECT_API:
            case AUXPROTECT_TOWNY:
                return true;
            default:
                return false;
        }
    }

    public boolean hasData() {
        switch (this) {
            case AUXPROTECT_MAIN:
            case AUXPROTECT_INVENTORY:
            case AUXPROTECT_SPAM:
            case AUXPROTECT_API:
            case AUXPROTECT_XRAY:
            case AUXPROTECT_TOWNY:
                return true;
            default:
                return false;
        }
    }

    public boolean hasLocation() {
        switch (this) {
            case AUXPROTECT_MAIN:
            case AUXPROTECT_ABANDONED:
            case AUXPROTECT_XRAY:
            case AUXPROTECT_INVENTORY:
            case AUXPROTECT_SPAM:
            case AUXPROTECT_COMMANDS:
            case AUXPROTECT_POSITION:
            case AUXPROTECT_API:
            case AUXPROTECT_TOWNY:
                return true;
            default:
                return false;
        }
    }

    public boolean hasLook() {
        return this == Table.AUXPROTECT_POSITION;
    }

    public boolean hasActionId() {
        switch (this) {
            case AUXPROTECT_COMMANDS:
            case AUXPROTECT_XRAY:
                return false;
            default:
                return true;
        }
    }

    public boolean hasStringTarget() {
        switch (this) {
            case AUXPROTECT_COMMANDS:
            case AUXPROTECT_LONGTERM:
                return true;
            default:
                return false;
        }
    }

    public boolean canPurge() {
        if (this == Table.AUXPROTECT_LONGTERM) {
            return false;
        }
        return this.hasAPEntries();
    }

    public String getValuesHeader(PlatformType platform) {
        if (this == Table.AUXPROTECT_LONGTERM) {
            return "(time, uid, action_id, target)";
        } else if (this == Table.AUXPROTECT_COMMANDS) {
            if (platform == PlatformType.BUNGEE) {
                return "(time, uid, target)";
            }
            return "(time, uid, world_id, x, y, z, target)";
        } else if (platform == PlatformType.BUNGEE) {
            return "(time, uid, action_id, target_id, data)";
        } else if (this == Table.AUXPROTECT_MAIN || this == Table.AUXPROTECT_SPAM || this == Table.AUXPROTECT_API
                || this == Table.AUXPROTECT_TOWNY) {
            return "(time, uid, action_id, world_id, x, y, z, target_id, data)";
        } else if (this == Table.AUXPROTECT_INVENTORY) {
            return "(time, uid, action_id, world_id, x, y, z, target_id, data, blobid, qty, damage)";
        } else if (this == Table.AUXPROTECT_ABANDONED) {
            return "(time, uid, action_id, world_id, x, y, z, target_id)";
        } else if (this == Table.AUXPROTECT_POSITION) {
            return "(time, uid, action_id, world_id, x, y, z, pitch, yaw, target_id)";
        } else if (this == Table.AUXPROTECT_XRAY) {
            return "(time, uid, world_id, x, y, z, target_id, rating, data)";
        }
        return null;
    }

    public int getNumColumns(PlatformType platform) {
        if (this == Table.AUXPROTECT_LONGTERM) {
            return 4;
        }
        if (this == Table.AUXPROTECT_COMMANDS) {
            if (platform == PlatformType.BUNGEE) {
                return 3;
            }
            return 7;
        }
        if (platform == PlatformType.BUNGEE) {
            return 5;
        }

        return switch (this) {
            case AUXPROTECT_ABANDONED -> 8;
            case AUXPROTECT_MAIN, AUXPROTECT_SPAM, AUXPROTECT_API, AUXPROTECT_XRAY, AUXPROTECT_TOWNY -> 9;
            case AUXPROTECT_POSITION -> 10;
            case AUXPROTECT_INVENTORY -> 12;
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
        String stmt = "CREATE TABLE IF NOT EXISTS " + this + " (\n";
        stmt += "    time BIGINT";
        stmt += ",\n    uid integer";
        if (hasActionId()) {
            stmt += ",\n    action_id SMALLINT";
        }
        if (plugin.getPlatform() == PlatformType.SPIGOT && hasLocation()) {
            stmt += ",\n    world_id SMALLINT";
            stmt += ",\n    x INTEGER";
            stmt += ",\n    y SMALLINT";
            stmt += ",\n    z INTEGER";
        }
        if (hasLook()) {
            stmt += ",\n    pitch SMALLINT";
            stmt += ",\n    yaw SMALLINT";
        }
        if (hasStringTarget()) {
            stmt += ",\n    target ";
            if (this == AUXPROTECT_COMMANDS) {
                stmt += "LONGTEXT";
            } else {
                stmt += "varchar(255)";
            }
        } else {
            stmt += ",\n    target_id integer";
        }
        if (this == AUXPROTECT_XRAY) {
            stmt += ",\n    rating SMALLINT";
        }
        if (hasData()) {
            stmt += ",\n    data LONGTEXT";
        }
        if (hasBlob()) {
            stmt += ",\n    blobid BIGINT";
        }
        if (hasItemMeta()) {
            stmt += ",\n    qty INTEGER";
            stmt += ",\n    damage INTEGER";
        }
        stmt += "\n);";

        return stmt;
    }

    public boolean hasBlob() {
        return this == AUXPROTECT_INVENTORY || this == AUXPROTECT_INVDIFF;
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

    public boolean hasItemMeta() {
        return this == AUXPROTECT_INVENTORY || this == AUXPROTECT_INVDIFF;
    }
}