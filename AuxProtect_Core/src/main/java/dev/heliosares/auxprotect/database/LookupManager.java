package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.towny.TownyEntry;
import dev.heliosares.auxprotect.utils.BidiMapCache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class LookupManager {
    private final SQLManager sql;
    private final IAuxProtect plugin;
    private static final BidiMapCache<Long, Parameters> groupParameterCache = new BidiMapCache<>(3 * 3600000L, 3 * 3600000L, true);

    public LookupManager(SQLManager sql, IAuxProtect plugin) {
        this.sql = sql;
        this.plugin = plugin;
    }

    public List<DbEntry> lookup(Parameters param) throws LookupException {
        String[] sqlstmts = param.toSQL(plugin);

        ArrayList<String> writeparams = new ArrayList<>(Arrays.asList(sqlstmts).subList(1, sqlstmts.length));
        String stmt = "SELECT * FROM " + param.getTable().toString();
        if (sqlstmts[0].length() > 1) {
            stmt += "\nWHERE " + sqlstmts[0];
        }
        stmt += "\nORDER BY time DESC\nLIMIT " + (SQLManager.MAX_LOOKUP_SIZE + 1) + ";";
        List<DbEntry> out = lookup(param.getTable(), stmt, writeparams);

        if (param.hasFlag(Parameters.Flag.PLAYBACK) || param.hasFlag(Parameters.Flag.INCREMENTAL_POS)) {
            try {
                sql.getMultipleBlobs(out.toArray(new DbEntry[0]));
            } catch (BusyException e) {
                throw new LookupException(Language.L.DATABASE_BUSY);
            } catch (SQLException e) {
                throw new LookupException(Language.L.ERROR);
            }
        }

        if (param.getGroupRange() > 0) {
            List<DbEntryGroup> groups = new ArrayList<>();
            entries:
            for (DbEntry entry : out) {
                for (DbEntryGroup group : groups) {
                    if (group.add(entry)) continue entries;
                }
                groups.add(new DbEntryGroup(entry, param));
            }
            out = groups.stream().map(entry -> (DbEntry) entry).toList();
            groups.forEach(group -> groupParameterCache.put(group.hash(), group.getParams()));
            groupParameterCache.cleanup();
        }

        return out;
    }

    public static Parameters getParametersForGroup(long groupHash) {
        return groupParameterCache.get(groupHash);
    }

    public int count(Parameters... params) throws LookupException {
        try {
            return sql.executeReturn(connection -> {
                int count = 0;
                for (Parameters param : params) {
                    String[] sqlstmts = param.toSQL(plugin);
                    String stmt = sql.getCountStmt(param.getTable().toString());
                    if (sqlstmts[0].length() > 1) {
                        stmt += "\nWHERE " + sqlstmts[0];
                    }
                    plugin.debug(stmt);
                    try (PreparedStatement statement = connection.prepareStatement(stmt)) {
                        for (int i = 1; i < sqlstmts.length; i++) {
                            statement.setString(i, sqlstmts[i]);
                        }
                        try (ResultSet rs = statement.executeQuery()) {
                            if (rs.next()) {
                                count += rs.getInt(1);
                            }
                        }
                    }
                }
                return count;
            }, 3000L, Integer.class);
        } catch (BusyException e) {
            throw new LookupException(Language.L.DATABASE_BUSY);
        } catch (Exception e1) {
            plugin.print(e1);
        }
        throw new LookupException(Language.L.ERROR);
    }

    /**
     * Performs a SQL Lookup in the table provided with the statement provided.
     *
     * @param table       The table being utilized. This is not user in the
     *                    statement and is merely provided for entry parsing
     * @param stmt        The statement to be executed
     * @param writeParams An in-order array of parameters to be inserted into ? of
     *                    stmt
     * @return An ArrayList of the DbEntry's meeting the provided conditions
     * @see LookupManager#lookup(dev.heliosares.auxprotect.core.Parameters)
     */
    public ArrayList<DbEntry> lookup(Table table, String stmt, ArrayList<String> writeParams) throws LookupException {
        plugin.debug(stmt, 3);

        ArrayList<DbEntry> output = new ArrayList<>();
        AtomicReference<LookupException> error = new AtomicReference<>();

        try {
            sql.execute(connection -> {
                try {
                    lookup(connection, output, table, stmt, writeParams);
                } catch (LookupException e) {
                    error.set(e);
                }
            }, 3000L);
        } catch (BusyException e) {
            throw new LookupException(Language.L.DATABASE_BUSY);
        } catch (SQLException e) {
            plugin.warning("Error while executing command");
            plugin.warning("SQL Code: " + stmt);
            plugin.print(e);
            throw new LookupException(Language.L.ERROR);
        }
        if (error.get() != null) throw error.get();
        return output;
    }

    /**
     * Performs a SQL Lookup in the table provided with the statement provided.
     *
     * @param table       The table being utilized. This is not user in the
     *                    statement and is merely provided for entry parsing
     * @param output      The List which the entries will be entered into
     * @param stmt        The statement to be executed
     * @param writeParams An in-order array of parameters to be inserted into ? of
     *                    stmt
     * @see LookupManager#lookup(dev.heliosares.auxprotect.core.Parameters)
     */
    protected void lookup(Connection connection, ArrayList<DbEntry> output, Table table, String stmt, ArrayList<String> writeParams) throws LookupException {
        final boolean hasLocation = plugin.getPlatform() .getLevel() == PlatformType.Level.SERVER && table.hasLocation();
        final boolean hasData = table.hasData();
        final boolean hasAction = table.hasActionId();
        final boolean hasLook = table.hasLook();
        plugin.debug(stmt, 3);

        long lookupStart = System.currentTimeMillis();

        try {
            long parseStart;
            try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {

                pstmt.setFetchSize(500);
                if (writeParams != null) {
                    int i1 = 1;
                    plugin.debug("writeParamsSize: " + writeParams.size());
                    for (String param : writeParams) {
                        pstmt.setString(i1++, param);
                    }
                }
                try (ResultSet rs = pstmt.executeQuery()) {

                    int count = 0;
                    parseStart = System.currentTimeMillis();
                    while (rs.next()) {
                        long time = rs.getLong("time");
                        int uid = rs.getInt("uid");
                        int action_id = -1;
                        if (hasAction) {
                            action_id = rs.getInt("action_id");
                        } else if (table == Table.AUXPROTECT_COMMANDS) {
                            action_id = EntryAction.COMMAND.id;
                        } else if (table == Table.AUXPROTECT_CHAT) {
                            action_id = EntryAction.CHAT.id;
                        } else if (table == Table.AUXPROTECT_XRAY) {
                            action_id = EntryAction.VEIN.id;
                        }
                        String world = null;
                        int x = 0, y = 0, z = 0;
                        if (hasLocation) {
                            world = sql.getWorld(rs.getInt("world_id"));
                            x = rs.getInt("x");
                            y = rs.getInt("y");
                            z = rs.getInt("z");
                        }

                        int pitch = 0, yaw = 180;
                        if (hasLook) {
                            pitch = rs.getInt("pitch");
                            yaw = rs.getInt("yaw");
                        }

                        String data = null;
                        if (hasData) {
                            data = rs.getString("data");
                        }
                        EntryAction entryAction = EntryAction.getAction(table, action_id);
                        if (entryAction == null) {
                            plugin.debug("Unknown action_id: " + action_id, 1);
                            continue;
                        }
                        boolean state = entryAction.hasDual && entryAction.id != action_id;
                        DbEntry entry;
                        String target = null;
                        int target_id = -1;
                        if (table.hasStringTarget()) {
                            target = rs.getString("target");
                        } else {
                            target_id = rs.getInt("target_id");
                        }
                        int qty = -1;
                        int damage = -1;
                        if (table.hasBlobID() && table.hasItemMeta()) {
                            qty = rs.getInt("qty");
                            if (rs.wasNull()) qty = -1;
                            damage = rs.getInt("damage");
                            if (rs.wasNull()) damage = -1;
                        }
                        if (table == Table.AUXPROTECT_XRAY) {
                            short rating = rs.getShort("rating");
                            entry = new XrayEntry(time, uid, world, x, y, z, target_id, rating, data);
                        } else if (table == Table.AUXPROTECT_TOWNY || entryAction.equals(EntryAction.TOWNYNAME)) {
                            entry = new TownyEntry(time, uid, entryAction, state, world, x, y, z, pitch, yaw, target,
                                    target_id, data);
                        } else if (table == Table.AUXPROTECT_POSITION) {
                            entry = new PosEntry(time, uid, entryAction, state, world, x, y, z, rs.getByte("increment"), pitch, yaw, target, target_id, data);
                        } else if (table == Table.AUXPROTECT_TRANSACTIONS) {
                            short quantity = rs.getShort("quantity");
                            double cost = rs.getDouble("cost");
                            double balance = rs.getDouble("balance");
                            int target_id2 = rs.getInt("target_id2");
                            entry = new TransactionEntry(time, uid, entryAction, state, world, x, y, z, pitch, yaw, target_id, data, quantity, cost, balance, target_id2, sql);
                        } else if (table.hasBlobID() && table.hasItemMeta() && qty >= 0 && damage >= 0) {
                            entry = new SingleItemEntry(time, uid, entryAction, state, world, x, y, z, pitch, yaw, target, target_id, data, qty, damage);
                        } else {
                            entry = new DbEntry(time, uid, entryAction, state, world, x, y, z, pitch, yaw, target, target_id, data, sql);
                        }

                        if (table.hasBlobID()) {
                            long blobid = rs.getLong("blobid");
                            if (rs.wasNull()) blobid = -1;
                            entry.setBlobID(blobid);
                        }
                        output.add(entry);
                        if (++count >= SQLManager.MAX_LOOKUP_SIZE) {
                            throw new LookupException(Language.L.COMMAND__LOOKUP__TOOMANY, count);
                        }
                    }
                }
            }
            plugin.debug("Completed lookup. Total: " + (System.currentTimeMillis() - lookupStart) + "ms Lookup: " + (parseStart - lookupStart) + "ms Parse: " + (System.currentTimeMillis() - parseStart) + "ms", 1);
        } catch (SQLException e) {
            plugin.warning("Error while executing command");
            plugin.warning("SQL Code: " + stmt);
            plugin.print(e);
            throw new LookupException(Language.L.ERROR);
        }
    }
}