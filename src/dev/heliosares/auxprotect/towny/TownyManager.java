package dev.heliosares.auxprotect.towny;

import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Government;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.BidiMapCache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class TownyManager {
    private final AuxProtectSpigot plugin;
    private final SQLManager sql;
    private BidiMapCache<Integer, String> names = new BidiMapCache<>(300000L, 300000L, true);

    public TownyManager(AuxProtectSpigot plugin, SQLManager sql) {
        this.plugin = plugin;
        this.sql = sql;
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

    public String getNameFromID(int uid) {
        if (uid < 0) {
            return null;
        }
        if (uid == 0) {
            return "";
        }
        if (names.containsKey(uid)) {
            return names.get(uid);
        }
        /*
         * if (plugin.isBungee()) { return uuid; } else { OfflinePlayer player =
         * Bukkit.getOfflinePlayer(UUID.fromString(uuid.substring(1))); if (player !=
         * null) { usernames.put(uuid, player.getName()); return player.getName
         */

        String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM.toString()
                + " WHERE action_id=? AND uid=?\nORDER BY time DESC\nLIMIT 1;";
        plugin.debug(stmt, 3);

        Connection connection;
        try {
            connection = sql.getConnection();
        } catch (SQLException e1) {
            plugin.print(e1);
            return null;
        }
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
        } catch (SQLException e) {
            plugin.print(e);
        } finally {
            sql.returnConnection(connection);
        }
        return null;
    }

    public int getIDFromName(String name) {
        if (name == null) {
            return -1;
        }
        if (names.containsValue(name)) {
            return names.getKey(name);
        }
        /*
         * if (plugin.isBungee()) { return uuid; } else { OfflinePlayer player =
         * Bukkit.getOfflinePlayer(UUID.fromString(uuid.substring(1))); if (player !=
         * null) { usernames.put(uuid, player.getName()); return player.getName
         */

        String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM.toString()
                + " WHERE action_id=? AND target=?\nORDER BY time DESC\nLIMIT 1;";
        plugin.debug(stmt, 3);

        Connection connection;
        try {
            connection = sql.getConnection();
        } catch (SQLException e1) {
            plugin.print(e1);
            return -1;
        }
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
        } catch (SQLException e) {
            plugin.print(e);
        } finally {
            sql.returnConnection(connection);
        }
        return -1;
    }

//	public int getIDFromName(String name, boolean insert) {
//		if (name == null) {
//			return -1;
//		}
//		if (names.containsValue(name)) {
//			return names.getKey(name);
//		}
//		String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM.toString()
//				+ " WHERE action_id=? AND lower(target)=?\nORDER BY time DESC\nLIMIT 1;";
//		plugin.debug(stmt, 3);
//
//		Connection connection;
//		try {
//			connection = sql.getConnection();
//		} catch (SQLException e1) {
//			plugin.print(e1);
//			return -1;
//		}
//		try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
//			pstmt.setInt(1, EntryAction.TOWNYNAME.id);
//			pstmt.setString(2, name.toLowerCase());
//			try (ResultSet results = pstmt.executeQuery()) {
//				if (results.next()) {
//					int uid = results.getInt("uid");
//					String username_ = results.getString("target");
//					plugin.debug("Resolved username " + username_ + " to UID " + uid, 5);
//					if (username_ != null && uid > 0) {
//						names.put(uid, username_);
//						return uid;
//					}
//				}
//			}
//		} catch (SQLException e) {
//			plugin.print(e);
//		} finally {
//			sql.returnConnection(connection);
//		}
//		return -1;
//	}

    public void updateName(Government gov, boolean async) {
        this.updateName(gov.getUUID(), gov.getName(), async);
    }

    public void updateName(UUID uuid, String name, boolean async) {
        Runnable run = new Runnable() {

            @Override
            public void run() {
                final int uid = sql.getUIDFromUUID("$t" + uuid, true);
                if (uid <= 0) {
                    plugin.warning("Failed to insert new town/nation name: " + name);
                    return;
                }
                plugin.debug("Handling " + name);

                String newestusername = getNameFromID(uid);
                if (!name.equalsIgnoreCase(newestusername)) {
                    plugin.debug("New town name: " + name + " for " + newestusername);
                    plugin.add(new TownyEntry("$t" + uuid, EntryAction.TOWNYNAME, false, name, ""));
                }
                names.put(uid, name);
            }
        };
        if (async) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, run);
        } else {
            run.run();
        }
    }

    public static String getLabel(Government gov) {
        return "$t" + gov.getUUID().toString();
    }

    public void cleanup() {
        names.cleanup();
    }
}
