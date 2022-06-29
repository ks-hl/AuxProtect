package dev.heliosares.auxprotect.spigot.listeners;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.gamingmesh.jobs.api.JobsExpGainEvent;
import com.gamingmesh.jobs.api.JobsPrePaymentEvent;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class JobsListener implements Listener {
	private final AuxProtectSpigot plugin;

	public JobsListener(AuxProtectSpigot plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPay(JobsPrePaymentEvent e) {
		logGain(e.getPlayer(), e.getJob().getName(), '$', e.getAmount());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onXP(JobsExpGainEvent e) {
		logGain(e.getPlayer(), e.getJob().getName(), 'x', e.getExp());
	}

	private void logGain(OfflinePlayer player, String job, char type, double value) {
		Location location = null;
		if (player instanceof Player) {
			location = ((Player) player).getLocation();
		}
		plugin.add(new JobsEntry(AuxProtectSpigot.getLabel(player), location, job, type, value));
	}

	public static class JobsEntry extends DbEntry {

		private double value;
		public final char type;

		public JobsEntry(String userLabel, Location location, String jobName, char type, double value) {
			super(userLabel, EntryAction.JOBS, false, location, jobName, "");
			this.value = value;
			this.type = type;
		}

		@Override
		public String getData() {
			return type + "" + Math.round(value * 100f) / 100f;
		}

		public boolean add(JobsEntry other) {
			if (userLabel.equals(other.userLabel) && type == other.type) {
				this.value += other.value;
				return true;
			}
			return false;
		}
	}
}
