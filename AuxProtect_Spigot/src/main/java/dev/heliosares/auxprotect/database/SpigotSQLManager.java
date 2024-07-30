package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.towny.TownyEntry;
import dev.heliosares.auxprotect.towny.TownyManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

public class SpigotSQLManager extends SQLManager {
    private final TownyManager townyManager;
    private final InvDiffManager invDiffManager;

    public SpigotSQLManager(AuxProtectSpigot plugin, String target, String prefix, File sqliteFile, boolean mysql, String user, String pass) throws ClassNotFoundException {
        super(plugin, target, prefix, sqliteFile, mysql, user, pass);

        TownyManager _townyManager = null;
        try {
            _townyManager = new TownyManager(plugin, this);

            getLookupManager().addLoader(new EntryLoader(
                    data -> data.table() == Table.AUXPROTECT_TOWNY || data.action().equals(EntryAction.TOWNYNAME),
                    data -> new TownyEntry(data.time(), data.uid(), data.action(), data.state(), data.world(),
                            data.x(), data.y(), data.z(), data.pitch(), data.yaw(), data.target(), data.target_id(), data.data())
            ));
        } catch (ClassNotFoundException e) {
            plugin.info("Towny not detected");
        } catch (Throwable e) {
            plugin.warning("Failed to initialize TownyManager, Towny will not be logged for this session");
            plugin.print(e);
        }
        this.townyManager = _townyManager;
        this.invDiffManager = new InvDiffManager(this, plugin);


        getLookupManager().addLoader(new EntryLoader(
                data -> data.table() == Table.AUXPROTECT_POSITION,
                data -> new PosEntry(data.time(), data.uid(), data.action(), data.state(), data.world(), data.x(), data.y(), data.z(), data.rs().getByte("increment"), data.pitch(), data.yaw(), data.target(), data.target_id(), data.data()
                )));

        getLookupManager().addLoader(new EntryLoader(
                data -> data.table().hasBlobID() && data.table().hasItemMeta(),
                data -> {
                    int qty = data.rs().getInt("qty");
                    if (data.rs().wasNull()) qty = -1;
                    int damage = data.rs().getInt("damage");
                    if (data.rs().wasNull()) damage = -1;
                    if (qty < 0 || damage < 0) return null;

                    return new SingleItemEntry(data.time(), data.uid(), data.action(), data.state(), data.world(), data.x(), data.y(), data.z(), data.pitch(), data.yaw(), data.target(), data.target_id(), data.data(), qty, damage);
                }
        ));

        getLookupManager().addLoader(new EntryLoader(
                data -> data.table() == Table.AUXPROTECT_TRANSACTIONS,
                data -> {
                    short quantity = data.rs().getShort("quantity");
                    double cost = data.rs().getDouble("cost");
                    double balance = data.rs().getDouble("balance");
                    int target_id2 = data.rs().getInt("target_id2");
                    return new TransactionEntry(data.time(), data.uid(), data.action(), data.state(), data.world(), data.x(), data.y(), data.z(), data.pitch(), data.yaw(), data.target_id(), data.data(), quantity, cost, balance, target_id2, this);
                }
        ));
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
        if (townyManager != null) {
            townyManager.cleanup();
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
    protected boolean putPosEntry(PreparedStatement preparedStatement, DbEntry dbEntry, AtomicInteger i) throws SQLException {
        if (dbEntry instanceof PosEntry posEntry) {
            preparedStatement.setByte(i.getAndIncrement(), posEntry.getIncrement());
            return true;
        }
        return false;
    }

    @Override
    protected void putXrayEntry(PreparedStatement preparedStatement, DbEntry dbEntry, AtomicInteger i) throws SQLException {
        if (dbEntry instanceof XrayEntry xrayEntry) {
            preparedStatement.setShort(i.getAndIncrement(), xrayEntry.getRating());
        }
    }

    @Override
    protected boolean putSingleItemEntry(PreparedStatement preparedStatement, DbEntry dbEntry, AtomicInteger i) throws SQLException, BusyException {
        if (dbEntry instanceof SingleItemEntry sientry && sientry.getItem() != null) {
            preparedStatement.setInt(i.getAndIncrement(), sientry.getQty());
            preparedStatement.setInt(i.getAndIncrement(), sientry.getDamage());
            return true;
        }
        return false;
    }

    @Override
    protected void putTransaction(PreparedStatement preparedStatement, DbEntry dbEntry, AtomicInteger i) throws SQLException, BusyException {
        if (!(dbEntry instanceof TransactionEntry transactionEntry)) return;

        preparedStatement.setShort(i.getAndIncrement(), transactionEntry.getQuantity());
        preparedStatement.setDouble(i.getAndIncrement(), transactionEntry.getCost());
        preparedStatement.setDouble(i.getAndIncrement(), transactionEntry.getBalance());
        preparedStatement.setInt(i.getAndIncrement(), transactionEntry.getTargetId2());

    }

    @Override
    public DbEntry convertToTransactionEntryForMigration(DbEntry entry, EntryAction action, short quantity, double cost, double balance, int target_id2) throws SQLException, BusyException {
        return new TransactionEntry(entry.getTime(), entry.getUid(), action, entry.getState(), entry.getWorld(), entry.getX(), entry.getY(), entry.getZ(), 0, 0, entry.getTargetId(), "", quantity, cost, balance, target_id2, this);
    }
}
