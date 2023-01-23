package dev.heliosares.auxprotect.towny;

import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Government;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.BidiMapCache;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class TownyManager {
    private final AuxProtectSpigot plugin;
    private final SQLManager sql;
    private final BidiMapCache<Integer, String> names = new BidiMapCache<>(300000L, 300000L, true);

    public TownyManager(AuxProtectSpigot plugin, SQLManager sql) {
        this.plugin = plugin;
        this.sql = sql;
    }

    public static String getLabel(Government gov) {
        return "$t" + gov.getUUID().toString();
    }

    public void init() {
        plugin.info("Checking for new towns/nations...");
        TownyUniverse.getInstance().getTowns().forEach((town) -> {
            sql.getTownyManager().updateName(town, false);
        });
        TownyUniverse.getInstance().getNations().forEach((nation) -> {
            sql.getTownyManager().updateName(nation, false);
        });
    }

    public String getNameFromID(int uid, boolean wait) throws SQLException {
        if (uid < 0) {
            return null;
        }
        if (uid == 0) {
            return "";
        }
        if (names.containsKey(uid)) {
            return names.get(uid);
        }

        String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM
                + " WHERE action_id=? AND uid=?\nORDER BY time DESC\nLIMIT 1;";
        plugin.debug(stmt, 3);
        return sql.executeReturn(connection -> {
            try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
                pstmt.setInt(1, EntryAction.TOWNYNAME.id);
                pstmt.setInt(2, uid);
                try (ResultSet results = pstmt.executeQuery()) {
                    if (results.next()) {
                        String username = results.getString("target");
                        plugin.debug("Resolved UID " + uid + " to " + username);
                        if (username != null) {
                            names.put(uid, username);
                            return username;
                        }
                    }
                }
            }
            return null;
        }, wait ? 30000L : 1000L, String.class);
    }

    public int getIDFromName(String name, boolean wait) throws SQLException {
        if (name == null) {
            return -1;
        }
        if (names.containsValue(name)) {
            return names.getKey(name);
        }

        String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM
                + " WHERE action_id=? AND target=?\nORDER BY time DESC\nLIMIT 1;";
        plugin.debug(stmt, 3);

        return sql.executeReturn(connection -> {
            try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
                pstmt.setInt(1, EntryAction.TOWNYNAME.id);
                pstmt.setString(2, name);
                try (ResultSet results = pstmt.executeQuery()) {
                    if (results.next()) {
                        int uid = results.getInt("uid");
                        plugin.debug("Resolved name " + name + " to " + uid);
                        if (uid > 0) {
                            names.put(uid, name);
                            return uid;
                        }
                    }
                }
            }
            return null;
        }, wait ? 30000L : 1000L, Integer.class);
    }

    public void updateName(Government gov, boolean async) {
        this.updateName(gov.getUUID(), gov.getName(), async);
    }

    public void updateName(UUID uuid, String name, boolean async) {
        Runnable run = () -> {
            int uid = -1;
            try {
                uid = sql.getUserManager().getUIDFromUUID("$t" + uuid, true);
            } catch (SQLException ignored) {
                //Unlikely
            }
            if (uid <= 0) {
                plugin.warning("Failed to insert new town/nation name: " + name);
                return;
            }
            plugin.debug("Handling " + name);

            String newestusername;
            try {
                newestusername = getNameFromID(uid, true);
            } catch (SQLException e) {
                plugin.print(e);
                return;
            }
            if (!name.equalsIgnoreCase(newestusername)) {
                plugin.debug("New town name: " + name + " for " + newestusername);
                plugin.add(new TownyEntry("$t" + uuid, EntryAction.TOWNYNAME, false, name, ""));
            }
            names.put(uid, name);
            plugin.debug("Handling " + name);

            if (!name.equalsIgnoreCase(newestusername)) {
                plugin.debug("New town name: " + name + " for " + newestusername);
                plugin.add(new TownyEntry("$t" + uuid, EntryAction.TOWNYNAME, false, name, ""));
            }
            names.put(uid, name);
        };
        if (async) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, run);
        } else {
            run.run();
        }
    }

    public void cleanup() {
        names.cleanup();
    }
}
