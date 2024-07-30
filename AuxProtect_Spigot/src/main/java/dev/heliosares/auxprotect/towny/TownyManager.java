package dev.heliosares.auxprotect.towny;

import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Government;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.database.SpigotDbEntry;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.exceptions.ParseException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.BidiMapCache;

import jakarta.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TownyManager implements Runnable {
    private final AuxProtectSpigot plugin;
    private final SQLManager sql;
    private final BidiMapCache<Integer, String> names = new BidiMapCache<>(300000L, 300000L, true);
    private final Map<UUID, Double> lastBalances = new HashMap<>();
    private long lastTownBankUpdate;
    private long lastNationBankUpdate;

    public TownyManager(AuxProtectSpigot plugin, SQLManager sql) throws ClassNotFoundException {
        this.plugin = plugin;
        this.sql = sql;

        Class.forName("com.palmergames.bukkit.towny.TownyUniverse");
    }

    public static String getLabel(@Nullable Government gov) {
        if (gov == null) return "#null";
        return "$t" + gov.getUUID().toString();
    }

    public void init() {
        plugin.info("Checking for new towns/nations...");
        TownyUniverse.getInstance().getTowns().forEach((town) -> updateName(town, false));
        TownyUniverse.getInstance().getNations().forEach((nation) -> updateName(nation, false));
    }

    String getNameFromID(int uid, boolean wait) throws SQLException, BusyException {
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

    public int getIDFromName(String name, boolean wait) throws SQLException, BusyException {
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
                uid = sql.getUserManager().getUIDFromUUID("$t" + uuid, true, true);
            } catch (SQLException | BusyException e) {
                plugin.print(e);
            }
            if (uid <= 0) {
                plugin.warning("Failed to insert new town/nation name: " + name);
                return;
            }
            plugin.debug("Handling " + name);

            String newestusername;
            try {
                newestusername = getNameFromID(uid, true);
            } catch (SQLException | BusyException e) {
                plugin.print(e);
                return;
            }
            if (!name.equalsIgnoreCase(newestusername)) {
                plugin.debug("New town name: " + name + " for " + newestusername + " (ID " + uid + ")");
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

    @Override
    public void run() {
        if (lastTownBankUpdate == 0) {
            lastTownBankUpdate = 1;
            try {
                for (DbEntry dbEntry : plugin.getSqlManager().getLookupManager().lookup(new Parameters(Table.AUXPROTECT_TOWNY).addAction(null, EntryAction.TOWNBALANCE, 0))) {
                    TownyEntry townyEntry = (TownyEntry) dbEntry;
                    UUID uuid = UUID.fromString(townyEntry.getUserUUID().substring(2));
                    double bal = Double.parseDouble(townyEntry.getData().replaceAll("[$,]", ""));
                    lastBalances.put(uuid, bal);
                }
            } catch (LookupException | ParseException | SQLException | BusyException e) {
                plugin.print(e);
            }
        }

        if (lastNationBankUpdate == 0) {
            lastNationBankUpdate = 1;
            try {
                for (DbEntry dbEntry : plugin.getSqlManager().getLookupManager().lookup(new Parameters(Table.AUXPROTECT_TOWNY).addAction(null, EntryAction.NATIONBALANCE, 0))) {
                    TownyEntry townyEntry = (TownyEntry) dbEntry;
                    UUID uuid = UUID.fromString(townyEntry.getUserUUID().substring(2));
                    double bal = Double.parseDouble(townyEntry.getData().replaceAll("[$,]", ""));
                    lastBalances.put(uuid, bal);
                }
            } catch (LookupException | ParseException | SQLException | BusyException e) {
                plugin.print(e);
            }
        }

        if (EntryAction.TOWNBANK.isEnabled() && plugin.getAPConfig().getTownBankInterval() > 0) {
            if (System.currentTimeMillis() - lastTownBankUpdate >= plugin.getAPConfig().getTownBankInterval()) {
                lastTownBankUpdate = System.currentTimeMillis();
                for (Town town : TownyUniverse.getInstance().getTowns()) {
                    Double lastBalance = lastBalances.get(town.getUUID());
                    double balance = town.getAccount().getHoldingBalance();
                    if (lastBalance != null && Math.abs(lastBalance - balance) < 1E-6) continue;
                    lastBalances.put(town.getUUID(), balance);
                    plugin.add(new SpigotDbEntry(getLabel(town), EntryAction.TOWNBALANCE, false, null, "periodic", plugin.formatMoney(balance)));
                }
            }
        }

        if (EntryAction.NATIONBANK.isEnabled() && plugin.getAPConfig().getNationBankInterval() > 0) {
            if (System.currentTimeMillis() - lastNationBankUpdate >= plugin.getAPConfig().getNationBankInterval()) {
                lastNationBankUpdate = System.currentTimeMillis();
                for (Nation nation : TownyUniverse.getInstance().getNations()) {
                    Double lastBalance = lastBalances.get(nation.getUUID());
                    double balance = nation.getAccount().getHoldingBalance();
                    if (lastBalance != null && Math.abs(lastBalance - balance) < 1E-6) return;
                    lastBalances.put(nation.getUUID(), balance);
                    plugin.add(new SpigotDbEntry(getLabel(nation), EntryAction.NATIONBALANCE, false, null, "periodic", plugin.formatMoney(balance)));
                }
            }
        }
    }
}
