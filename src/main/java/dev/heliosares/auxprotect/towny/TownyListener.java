package dev.heliosares.auxprotect.towny;

import com.palmergames.bukkit.towny.event.*;
import com.palmergames.bukkit.towny.event.economy.BankTransactionEvent;
import com.palmergames.bukkit.towny.event.economy.NationTransactionEvent;
import com.palmergames.bukkit.towny.event.economy.TownTransactionEvent;
import com.palmergames.bukkit.towny.event.town.TownMayorChangeEvent;
import com.palmergames.bukkit.towny.event.town.TownUnclaimEvent;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.Government;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.WorldCoord;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.sql.SQLException;

public class TownyListener implements Listener {
    private final AuxProtectSpigot plugin;

    public TownyListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    private static Location toLoc(Resident res) {
        if (res == null || res.getPlayer() == null) {
            return null;
        }
        return res.getPlayer().getLocation();
    }

    private static Location toLoc(Coord coord) {
        if (coord instanceof WorldCoord c) {
            return new Location(c.getBukkitWorld(), c.getX() * 16, 128, c.getZ() * 16);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(NewDayEvent e) {
        e.getFallenTowns().forEach((t) -> {
            handleDeleted(t, false);
        });
        e.getFallenNations().forEach((n) -> {
            handleDeleted(n, true);
        });
    }

    private void handleDeleted(String name, boolean nation) {
        int uid;
        try {
            uid = plugin.getSqlManager().getTownyManager().getIDFromName(name, true);
        } catch (SQLException | BusyException e) {
            plugin.print(e);
            return;
        }

        if (uid <= 0) {
            plugin.info("Unknown town/nation " + name);
            return;
        }
        String uuid = null;
        try {
            uuid = plugin.getSqlManager().getUserManager().getUUIDFromUID(uid, true);
        } catch (SQLException | BusyException ignored) {
            //Unlikely
        }
        if (uuid == null) {
            plugin.info("Unknown town/nation " + name + " with uid " + uid);
            return;
        }
        plugin.add(
                new TownyEntry("#server", nation ? EntryAction.NATIONDELETE : EntryAction.TOWNDELETE, false, uuid, ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(NewTownEvent e) {
        plugin.getSqlManager().getTownyManager().updateName(e.getTown(), true);
        try {
            plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(e.getTown().getMayor().getPlayer()),
                    EntryAction.TOWNCREATE, false, toLoc(e.getTown().getHomeBlock().getWorldCoord()),
                    TownyManager.getLabel(e.getTown()), null));
        } catch (NullPointerException | TownyException e1) {
            plugin.print(e1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(RenameTownEvent e) {
        plugin.getSqlManager().getTownyManager().updateName(e.getTown(), true);
        plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(e.getTown().getMayor()), EntryAction.TOWNRENAME, false,
                toLoc(e.getTown().getMayor()), TownyManager.getLabel(e.getTown()),
                e.getOldName() + " -> " + e.getTown().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(DeleteTownEvent e) {
        plugin.getSqlManager().getTownyManager().updateName(e.getTownUUID(), e.getTownName(), true);
        plugin.add(new TownyEntry("$" + e.getMayorUUID(), EntryAction.TOWNDELETE, false, toLoc(e.getMayor()),
                "$t" + e.getTownUUID(), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(TownAddResidentEvent e) {
        plugin.add(new TownyEntry("$" + e.getResident().getUUID().toString(), EntryAction.TOWNJOIN, true,
                toLoc(e.getResident()), TownyManager.getLabel(e.getTown()), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(TownRemoveResidentEvent e) {
        plugin.add(new TownyEntry("$" + e.getResident().getUUID().toString(), EntryAction.TOWNJOIN, false,
                toLoc(e.getResident()), TownyManager.getLabel(e.getTown()), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(TownClaimEvent e) {
        try {
            plugin.add(new TownyEntry("$" + e.getResident().getUUID().toString(), EntryAction.TOWNCLAIM, true,
                    toLoc(e.getTownBlock().getWorldCoord()), TownyManager.getLabel(e.getTownBlock().getTown()), ""));
        } catch (NullPointerException | NotRegisteredException e1) {
            plugin.print(e1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(TownUnclaimEvent e) {
        plugin.add(new TownyEntry(TownyManager.getLabel(e.getTown()), EntryAction.TOWNCLAIM, false,
                toLoc(e.getWorldCoord()), "", ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(TownMayorChangeEvent e) {
        plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(e.getNewMayor().getPlayer()), EntryAction.TOWNMAYOR, false,
                toLoc(e.getNewMayor()), TownyManager.getLabel(e.getTown()), "Prior: " + e.getOldMayor().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(TownTransactionEvent e) {
        handleBank(e, e.getTown(), EntryAction.TOWNBANK);
    }

    // Nations

    private void handleBank(BankTransactionEvent e, Government g, EntryAction action) {
        String user = "#server";
        Location loc = null;
        if (e.getTransaction().getPlayer() != null) {
            user = AuxProtectSpigot.getLabel(e.getTransaction().getPlayer());
            loc = e.getTransaction().getPlayer().getLocation();
        }
        boolean state = switch (e.getTransaction().getType()) {
            case ADD, DEPOSIT -> true;
            default -> false;
        };
        String data = plugin.formatMoney(e.getTransaction().getAmount()) + ", Bal: "
                + plugin.formatMoney(e.getAccount().getHoldingBalance());
        plugin.add(new TownyEntry(user, action, state, loc, TownyManager.getLabel(g), data));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(NewNationEvent e) {
        Player king = e.getNation().getKing().getPlayer();
        plugin.getSqlManager().getTownyManager().updateName(e.getNation(), true);
        plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(king), EntryAction.NATIONCREATE, false,
                toLoc(e.getNation().getKing()), TownyManager.getLabel(e.getNation()), null));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(RenameNationEvent e) {
        plugin.getSqlManager().getTownyManager().updateName(e.getNation(), true);
        Player mayor = e.getNation().getKing().getPlayer();
        plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(mayor), EntryAction.NATIONRENAME, false,
                toLoc(e.getNation().getKing()), TownyManager.getLabel(e.getNation()),
                e.getOldName() + " -> " + e.getNation().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(DeleteNationEvent e) {
        plugin.getSqlManager().getTownyManager().updateName(e.getNationUUID(), e.getNationName(), true);
        plugin.add(
                new TownyEntry(e.getLeader() == null ? "" : ("$" + e.getLeader().getUUID()), EntryAction.TOWNDELETE, false, "$t" + e.getNationUUID(), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(NationAddTownEvent e) {
        plugin.add(new TownyEntry(TownyManager.getLabel(e.getTown()), EntryAction.NATIONJOIN, true, TownyManager.getLabel(e.getNation()), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(NationRemoveTownEvent e) {
        plugin.add(new TownyEntry(TownyManager.getLabel(e.getTown()), EntryAction.NATIONJOIN, false, TownyManager.getLabel(e.getNation()), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(NationTransactionEvent e) {
        handleBank(e, e.getNation(), EntryAction.NATIONBANK);
    }
}
