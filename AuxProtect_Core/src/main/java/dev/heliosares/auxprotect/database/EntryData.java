package dev.heliosares.auxprotect.database;

import java.sql.ResultSet;

public record EntryData(Table table, long time, int uid, EntryAction action,
                        boolean state, String world, int x, int y, int z,
                        int pitch, int yaw, String target,
                        int target_id, String data, ResultSet rs) {
}
