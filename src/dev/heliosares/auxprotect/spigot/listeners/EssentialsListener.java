package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.ess3.api.IUser;
import net.essentialsx.api.v2.events.TransactionEvent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class EssentialsListener implements Listener {
    private final AuxProtectSpigot plugin;

    public EssentialsListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransactionEvent(TransactionEvent e) {
        String label;
        Location loc = null;
        if (e.getRequester().getPlayer() != null) {
            loc = e.getRequester().getPlayer().getLocation();
            label = AuxProtectSpigot.getLabel(e.getRequester().getPlayer());
        } else {
            label = "#server";
        }

        IUser target = e.getTarget();
        String amount = plugin.formatMoney(e.getAmount().doubleValue());
        if (loc == null) {
            plugin.add(new DbEntry(label, EntryAction.PAY, false, AuxProtectSpigot.getLabel(target.getBase()), amount));
        } else {
            plugin.add(new DbEntry(label, EntryAction.PAY, false, loc,
                    AuxProtectSpigot.getLabel(e.getTarget().getBase()), amount));
        }
    }
}
