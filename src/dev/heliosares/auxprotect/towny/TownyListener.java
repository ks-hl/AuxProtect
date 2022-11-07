package dev.heliosares.auxprotect.towny;

import com.palmergames.bukkit.towny.event.*;
import com.palmergames.bukkit.towny.event.town.TownMayorChangeEvent;
import com.palmergames.bukkit.towny.event.town.TownMergeEvent;
import com.palmergames.bukkit.towny.event.town.TownUnclaimEvent;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.*;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

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

    // Towns

    public static String getLabel(Government gov) {
        return "#" + (gov instanceof Nation ? "N" : "T") + gov.getUUID().toString();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(NewDayEvent e) {
        e.getFallenTowns().forEach((t) -> {
            handledeleted(t, false);
        });
        e.getFallenNations().forEach((n) -> {
            handledeleted(n, true);
        });
    }

    private void handledeleted(String name, boolean nation) {
        int uid = plugin.getSqlManager().getTownyManager().getIDFromName(name);
        if (uid <= 0) {
            plugin.info("Unknown town/nation " + name);
            return;
        }
        String uuid = plugin.getSqlManager().getUserManager().getUUIDFromUID(uid);
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
        plugin.add(new TownyEntry("$" + e.getMayorUUID().toString(), EntryAction.TOWNDELETE, false, toLoc(e.getMayor()),
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
    public void on(TownMergeEvent e) {
        plugin.add(new TownyEntry("$t" + e.getSuccumbingTownUUID(), EntryAction.TOWNCLAIM, false,
                TownyManager.getLabel(e.getRemainingTown()), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(TownMayorChangeEvent e) {
        plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(e.getNewMayor().getPlayer()), EntryAction.TOWNMAYOR, false,
                toLoc(e.getNewMayor()), getLabel(e.getTown()), "Prior: " + e.getOldMayor().getName()));
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
        boolean state;
        switch (e.getTransaction().getType()) {
            case ADD:
            case DEPOSIT:
                state = true;
                break;
            default:
                state = false;
        }
        String data = plugin.formatMoney(e.getTransaction().getAmount()) + ", Bal: "
                + plugin.formatMoney(e.getAccount().getHoldingBalance());
        plugin.add(new TownyEntry(user, action, state, loc, TownyManager.getLabel(g), data));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(NewNationEvent e) {
        Player king = e.getNation().getKing().getPlayer();
        plugin.getSqlManager().getTownyManager().updateName(e.getNation(), true);
        plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(king), EntryAction.TOWNCREATE, false,
                toLoc(e.getNation().getKing()), TownyManager.getLabel(e.getNation()), null));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(RenameNationEvent e) {
        plugin.getSqlManager().getTownyManager().updateName(e.getNation(), true);
        Player mayor = e.getNation().getKing().getPlayer();
        plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(mayor), EntryAction.TOWNRENAME, false,
                toLoc(e.getNation().getKing()), TownyManager.getLabel(e.getNation()),
                e.getOldName() + " -> " + e.getNation().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(DeleteNationEvent e) {
        plugin.getSqlManager().getTownyManager().updateName(e.getNationUUID(), e.getNationName(), true);
        plugin.add(
                new TownyEntry("$" + e.getNationKing(), EntryAction.TOWNDELETE, false, "$t" + e.getNationUUID(), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(NationAddTownEvent e) {
        plugin.add(new TownyEntry(getLabel(e.getTown()), EntryAction.NATIONJOIN, true, getLabel(e.getNation()), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(NationRemoveTownEvent e) {
        plugin.add(new TownyEntry(getLabel(e.getTown()), EntryAction.NATIONJOIN, false, getLabel(e.getNation()), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(NationTransactionEvent e) {
        handleBank(e, e.getNation(), EntryAction.NATIONBANK);
    }
}
