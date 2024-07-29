package dev.heliosares.auxprotect.spigot.listeners;

import com.gamingmesh.jobs.api.JobsExpGainEvent;
import com.gamingmesh.jobs.api.JobsPrePaymentEvent;
import com.gamingmesh.jobs.container.CurrencyType;
import com.gamingmesh.jobs.container.Job;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.JobsEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class JobsListener implements Listener {
    private final AuxProtectSpigot plugin;

    public JobsListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPay(JobsPrePaymentEvent e) {
        logGain(e.getPlayer(), e.getJob(), CurrencyType.MONEY, e.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onXP(JobsExpGainEvent e) {
        logGain(e.getPlayer(), e.getJob(), CurrencyType.EXP, e.getExp());
    }

    private void logGain(OfflinePlayer oplayer, Job job, CurrencyType type, double value) {
        if (!EntryAction.JOBS.isEnabled()) return;
        if (value < 1E-4 || job == null) return;

        Location location = null;
//		double boost = 1;
        if (oplayer instanceof Player player) {
            location = player.getLocation();
        }
        char typechar = '?';
        switch (type) {
            case MONEY -> typechar = '$';
            case EXP -> typechar = 'x';
            case POINTS -> typechar = '*';
            default -> {
            }
        }

        if (location == null) {
            plugin.add(new JobsEntry(AuxProtectSpigot.getLabel(oplayer), job.getName(), typechar, value));
        } else {
            plugin.add(new JobsEntry(AuxProtectSpigot.getLabel(oplayer), location, job.getName(), typechar, value));
        }
    }

}
