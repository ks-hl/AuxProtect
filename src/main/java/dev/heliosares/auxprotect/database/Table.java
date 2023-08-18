package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;

import java.util.concurrent.ConcurrentLinkedQueue;

public enum Table {
    AUXPROTECT_MAIN, AUXPROTECT_SPAM, AUXPROTECT_LONGTERM, AUXPROTECT_ABANDONED, AUXPROTECT_XRAY, AUXPROTECT_INVENTORY,
    AUXPROTECT_COMMANDS, AUXPROTECT_CHAT, AUXPROTECT_POSITION, AUXPROTECT_TOWNY,

    AUXPROTECT_API, AUXPROTECT_UIDS, AUXPROTECT_WORLDS, AUXPROTECT_API_ACTIONS, AUXPROTECT_VERSION, AUXPROTECT_INVBLOB, AUXPROTECT_LASTS,
    AUXPROTECT_INVDIFF, AUXPROTECT_INVDIFFBLOB, AUXPROTECT_USERDATA_PENDINV;

    public static final long MIN_PURGE_INTERVAL = 1000L * 60L * 60L * 24L * 14L;
    final ConcurrentLinkedQueue<DbEntry> queue = new ConcurrentLinkedQueue<>();
    private long autopurgeinterval;

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
        return SQLManager.getInstance().getTablePrefix() + super.toString().toLowerCase();
    }

    public String getName() {
        return super.toString().toLowerCase();
    }

    public boolean exists(IAuxProtect plugin) {
        if (plugin.getPlatform() == PlatformType.BUNGEE) {
            return switch (this) {
                case AUXPROTECT_MAIN,
                        AUXPROTECT_COMMANDS,
                        AUXPROTECT_CHAT,
                        AUXPROTECT_LONGTERM,
                        AUXPROTECT_API,
                        AUXPROTECT_UIDS,
                        AUXPROTECT_API_ACTIONS,
                        AUXPROTECT_VERSION -> true;
                default -> false;
            };
        }
        return plugin.getAPConfig().isPrivate() || this != AUXPROTECT_ABANDONED;
    }

    public boolean hasAPEntries() {
        return switch (this) {
            case AUXPROTECT_MAIN, AUXPROTECT_SPAM, AUXPROTECT_LONGTERM, AUXPROTECT_ABANDONED, AUXPROTECT_XRAY, AUXPROTECT_INVENTORY, AUXPROTECT_COMMANDS, AUXPROTECT_CHAT, AUXPROTECT_POSITION, AUXPROTECT_API, AUXPROTECT_TOWNY ->
                    true;
            default -> false;
        };
    }

    public boolean hasData() {
        return switch (this) {
            case AUXPROTECT_MAIN, AUXPROTECT_INVENTORY, AUXPROTECT_SPAM, AUXPROTECT_API, AUXPROTECT_XRAY, AUXPROTECT_TOWNY ->
                    true;
            default -> false;
        };
    }

    public boolean hasLocation() {
        return switch (this) {
            case AUXPROTECT_MAIN, AUXPROTECT_ABANDONED, AUXPROTECT_XRAY, AUXPROTECT_INVENTORY, AUXPROTECT_SPAM, AUXPROTECT_COMMANDS, AUXPROTECT_CHAT, AUXPROTECT_POSITION, AUXPROTECT_API, AUXPROTECT_TOWNY ->
                    true;
            default -> false;
        };
    }

    public boolean hasLook() {
        return this == Table.AUXPROTECT_POSITION;
    }

    public boolean hasActionId() {
        return switch (this) {
            case AUXPROTECT_COMMANDS, AUXPROTECT_CHAT, AUXPROTECT_XRAY -> false;
            default -> true;
        };
    }

    public boolean hasStringTarget() {
        return switch (this) {
            case AUXPROTECT_COMMANDS, AUXPROTECT_LONGTERM, AUXPROTECT_CHAT -> true;
            default -> false;
        };
    }

    public boolean canPurge() {
        if (this == Table.AUXPROTECT_LONGTERM) {
            return false;
        }
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
        } else if (this == Table.AUXPROTECT_MAIN || this == Table.AUXPROTECT_SPAM || this == Table.AUXPROTECT_API
                || this == Table.AUXPROTECT_TOWNY) {
            return "(time, uid, action_id, world_id, x, y, z, target_id, data)";
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
        if (platform == PlatformType.BUNGEE) {
            return 5;
        }

        return switch (this) {
            case AUXPROTECT_ABANDONED -> 8;
            case AUXPROTECT_MAIN, AUXPROTECT_SPAM, AUXPROTECT_API, AUXPROTECT_XRAY, AUXPROTECT_TOWNY -> 9;
            case AUXPROTECT_POSITION, AUXPROTECT_INVENTORY -> 12;
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
        if (this == AUXPROTECT_POSITION) {
            stmt += ",\n    increment TINYINT";
        }
        if (hasLook()) {
            stmt += ",\n    pitch SMALLINT";
            stmt += ",\n    yaw SMALLINT";
        }
        if (hasStringTarget()) {
            stmt += ",\n    target ";
            if (this == AUXPROTECT_COMMANDS || this == AUXPROTECT_CHAT) {
                stmt += "LONGTEXT";
            } else {
                stmt += "varchar(255)";
            }
            if (this == AUXPROTECT_LONGTERM) {
                stmt += ",\n    target_hash INT";
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

        if (hasBlob()) stmt += ",\n    ablob BLOB";
        else if (hasBlobID()) stmt += ",\n    blobid BIGINT";

        if (hasItemMeta()) {
            stmt += ",\n    qty INTEGER";
            stmt += ",\n    damage INTEGER";
        }
        stmt += "\n)";

        if (plugin.getSqlManager().isMySQL() && (hasStringTarget() || hasData())) {
            stmt += " CHARACTER SET utf8mb4";
            stmt += " COLLATE utf8mb4_general_ci;";
        }

        return stmt;
    }

    public boolean hasBlobID() {
        return this == AUXPROTECT_INVENTORY || this == AUXPROTECT_INVDIFF;
    }

    public boolean hasBlob() {
        return this == AUXPROTECT_POSITION;
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