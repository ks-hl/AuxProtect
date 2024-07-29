package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.towny.TownyManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SpigotSQLManager extends SQLManager {
    private final TownyManager townyManager;
    private final InvDiffManager invDiffManager;

    public SpigotSQLManager(AuxProtectSpigot plugin, String target, String prefix, File sqliteFile, boolean mysql, String user, String pass) throws ClassNotFoundException {
        super(plugin, target, prefix, sqliteFile, mysql, user, pass);

        TownyManager _townyManager = null;
        if (plugin.isHooked("Towny")) {
            try {
                _townyManager = new TownyManager(plugin, this);
            } catch (Throwable e) {
                plugin.warning("Failed to initialize TownyManager, Towny will not be logged for this session");
                plugin.print(e);
            }
        }
        this.townyManager = _townyManager;
        this.invDiffManager = new InvDiffManager(this, plugin);
    }

    @Override
    protected void otherConnectTasks() {
        if (townyManager != null) townyManager.init();
    }

    public TownyManager getTownyManager() {
        return townyManager;
    }

    public InvDiffManager getInvDiffManager() {
        return invDiffManager;
    }

    @Override
    protected void createTables(Connection connection) throws SQLException, BusyException {
        if (invDiffManager != null) {
            invDiffManager.createTable(connection);
        }
        super.createTables(connection);
    }

    @Override
    protected void postTables(Connection connection) throws SQLException {
        if (invDiffManager != null) {
            invDiffManager.init(connection);
        }
        super.postTables(connection);
    }

    @Override
    protected void tickPuts(Connection connection) {
        super.tickPuts(connection);
        if (invDiffManager != null) {
            invDiffManager.put(connection);
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        if (invDiffManager != null) {
            invDiffManager.cleanup();
        }
    }

    public void updateXrayEntry(XrayEntry entry) throws SQLException, BusyException {
        if (!super.isConnected())
            return;
        String stmt = "UPDATE " + entry.getAction().getTable().toString();
        stmt += "\nSET rating=?, data=?";
        stmt += "\nWHERE time = ? AND uid = ? AND target_id = ?";

        if (executeReturnRows(stmt, entry.getRating(), sanitize(entry.getData()), entry.getTime(), entry.getUid(), entry.getTargetId()) > 1) {
            getPlugin().warning("Updated multiple entries when updating the following entry:");
            Results.sendEntry(getPlugin(), getPlugin().getConsoleSender(), entry, 0, true, true, true);
        }
    }

    @Override
    protected boolean putPosEntry(PreparedStatement preparedStatement, DbEntry dbEntry, int i) throws SQLException {
        if (dbEntry instanceof PosEntry posEntry) {
            preparedStatement.setByte(i, posEntry.getIncrement());
            return true;
        }
        return false;
    }

    @Override
    protected boolean putXrayEntry(PreparedStatement preparedStatement, DbEntry dbEntry, int i) throws SQLException {
        if (dbEntry instanceof XrayEntry xrayEntry) {
            preparedStatement.setShort(i, xrayEntry.getRating());
            return true;
        }
        return false;
    }
}
