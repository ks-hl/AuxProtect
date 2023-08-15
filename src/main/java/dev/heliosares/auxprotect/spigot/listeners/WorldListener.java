package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SingleItemEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.raid.RaidSpawnWaveEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.weather.LightningStrikeEvent;

public class WorldListener implements Listener {

    private final AuxProtectSpigot plugin;

    public WorldListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightningStrikeEvent(LightningStrikeEvent e) {
        plugin.add(new DbEntry("#env", EntryAction.LIGHTNING, false, e.getLightning().getLocation(), "", e.getLightning().isEffect() ? "effect" : ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreakEvent(HangingBreakEvent e) {
        if (e.getCause() == RemoveCause.ENTITY) {
            return;
        }
        if (e.getEntity() instanceof final ItemFrame item) {
            DbEntry entry = new SingleItemEntry("#" + e.getCause().toString().toLowerCase(), EntryAction.ITEMFRAME, false, item.getLocation(), item.getItem().getType().toString().toLowerCase(), "", item.getItem());
            plugin.add(entry);
        }

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(RaidTriggerEvent e) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.add(new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.RAIDTRIGGER, false, e.getPlayer().getLocation(), "", "")), 3);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(RaidSpawnWaveEvent e) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> e.getRaiders().forEach(raider -> plugin.add(new DbEntry("#raid", EntryAction.RAIDSPAWN, false, raider.getLocation(), AuxProtectSpigot.getLabel(raider), ""))), 1);
    }
}
