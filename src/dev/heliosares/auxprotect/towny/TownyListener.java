package dev.heliosares.auxprotect.towny;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.palmergames.bukkit.towny.event.BankTransactionEvent;
import com.palmergames.bukkit.towny.event.DeleteNationEvent;
import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.NationAddTownEvent;
import com.palmergames.bukkit.towny.event.NationRemoveTownEvent;
import com.palmergames.bukkit.towny.event.NationTransactionEvent;
import com.palmergames.bukkit.towny.event.NewDayEvent;
import com.palmergames.bukkit.towny.event.NewNationEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.RenameNationEvent;
import com.palmergames.bukkit.towny.event.RenameTownEvent;
import com.palmergames.bukkit.towny.event.TownAddResidentEvent;
import com.palmergames.bukkit.towny.event.TownClaimEvent;
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import com.palmergames.bukkit.towny.event.TownTransactionEvent;
import com.palmergames.bukkit.towny.event.town.TownMayorChangeEvent;
import com.palmergames.bukkit.towny.event.town.TownMergeEvent;
import com.palmergames.bukkit.towny.event.town.TownUnclaimEvent;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.Government;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.WorldCoord;

import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class TownyListener implements Listener {
	private final AuxProtectSpigot plugin;

	public TownyListener(AuxProtectSpigot plugin) {
		this.plugin = plugin;
	}

	// General

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(NewDayEvent e) {
		e.getBankruptedTowns();
		e.getFallenNations();
		e.getFallenTowns();

		// TODO does delete town handle this?
	}

	// Towns

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(NewTownEvent e) {
		plugin.getSqlManager().getTownyManager().updateName(e.getTown());
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
		plugin.getSqlManager().getTownyManager().updateName(e.getTown());
		Player mayor = e.getTown().getMayor().getPlayer();
		plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(mayor), EntryAction.TOWNRENAME, false, mayor.getLocation(),
				TownyManager.getLabel(e.getTown()), e.getOldName() + " -> " + e.getTown().getName()));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(DeleteTownEvent e) {
		plugin.getSqlManager().getTownyManager().updateName(e.getTownUUID(), e.getTownName());
		plugin.add(new TownyEntry("$" + e.getMayorUUID().toString(), EntryAction.TOWNDELETE, false,
				e.getMayor().getPlayer().getLocation(), "$t" + e.getTownUUID(), ""));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(TownAddResidentEvent e) {
		plugin.add(new TownyEntry("$" + e.getResident().getUUID().toString(), EntryAction.TOWNJOIN, true,
				e.getResident().getPlayer().getLocation(), TownyManager.getLabel(e.getTown()), ""));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(TownRemoveResidentEvent e) {
		plugin.add(new TownyEntry("$" + e.getResident().getUUID().toString(), EntryAction.TOWNJOIN, false,
				e.getResident().getPlayer().getLocation(), TownyManager.getLabel(e.getTown()), ""));
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

	private static Location toLoc(Coord coord) {
		if (coord instanceof WorldCoord c) {
			return new Location(c.getBukkitWorld(), c.getX() * 16, 128, c.getZ() * 16);
		}
		return null;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(TownMergeEvent e) {
		plugin.add(new TownyEntry("$t" + e.getSuccumbingTownUUID(), EntryAction.TOWNCLAIM, false,
				TownyManager.getLabel(e.getRemainingTown()), ""));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(TownMayorChangeEvent e) {
		plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(e.getNewMayor().getPlayer()), EntryAction.TOWNMAYOR, false,
				e.getNewMayor().getPlayer().getLocation(), getLabel(e.getTown()),
				"Prior: " + e.getOldMayor().getName()));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(TownTransactionEvent e) {
		handleBank(e, e.getTown(), EntryAction.TOWNBANK);
	}

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

	// Nations

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(NewNationEvent e) {
		Player king = e.getNation().getKing().getPlayer();
		plugin.getSqlManager().getTownyManager().updateName(e.getNation());
		plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(king), EntryAction.TOWNCREATE, false, king.getLocation(),
				TownyManager.getLabel(e.getNation()), null));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(RenameNationEvent e) {
		plugin.getSqlManager().getTownyManager().updateName(e.getNation());
		Player mayor = e.getNation().getKing().getPlayer();
		plugin.add(new TownyEntry(AuxProtectSpigot.getLabel(mayor), EntryAction.TOWNRENAME, false, mayor.getLocation(),
				TownyManager.getLabel(e.getNation()), e.getOldName() + " -> " + e.getNation().getName()));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void on(DeleteNationEvent e) {
		plugin.getSqlManager().getTownyManager().updateName(e.getNationUUID(), e.getNationName());
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

	public static String getLabel(Government gov) {
		return "#" + (gov instanceof Nation ? "N" : "T") + gov.getUUID().toString();
	}
}