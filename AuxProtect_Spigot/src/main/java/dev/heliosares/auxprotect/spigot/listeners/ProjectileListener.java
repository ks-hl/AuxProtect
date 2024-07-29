package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SingleItemEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.BlockProjectileSource;

import java.util.ArrayList;

public class ProjectileListener implements Listener {

    final ArrayList<EntityType> whitelist;
    private final AuxProtectSpigot plugin;

    public ProjectileListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
        this.whitelist = new ArrayList<>();
        whitelist.add(EntityType.ENDER_PEARL);
        whitelist.add(EntityType.TRIDENT);
        whitelist.add(EntityType.SNOWBALL);
        whitelist.add(EntityType.EGG);
        whitelist.add(EntityType.ARROW);
        for (EntityType type : EntityType.values()) {
            if (type.toString().contains("POTION") || type.toString().contains("FISHING_")) {
                whitelist.add(type);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunchEvent(ProjectileLaunchEvent e) {
        logEntity(null, e.getEntity(), EntryAction.LAUNCH, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getHitBlock() == null) {
            return;
        }
        logEntity(null, e.getEntity(), EntryAction.LAND, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupArrowEvent(PlayerPickupArrowEvent e) {
        logEntity(e.getPlayer(), e.getArrow(), EntryAction.GRAB, false);
    }

    private void logEntity(LivingEntity actor, Projectile entity, EntryAction action, boolean logData) {
        if (entity == null || !whitelist.contains(entity.getType())) {
            return;
        }
        String actorLabel = null;
        if (actor == null) {
            if (entity.getShooter() == null) {
                return;
            }
            if (entity.getShooter() instanceof BlockProjectileSource shooter) {
                actorLabel = AuxProtectSpigot.getLabel(shooter.getBlock());
            } else if (entity.getShooter() instanceof LivingEntity shooter) {
                actorLabel = AuxProtectSpigot.getLabel(shooter);
            } else {
                return;
            }
        } else {
            actorLabel = AuxProtectSpigot.getLabel(actor);
        }
        ItemStack item = null;
        if (entity instanceof ThrowableProjectile) {
            item = ((ThrowableProjectile) entity).getItem();
        }

        DbEntry entry = new SingleItemEntry(actorLabel, action, false, entity.getLocation(), AuxProtectSpigot.getLabel(entity), "", logData ? item : null);
        plugin.add(entry);
    }
}
