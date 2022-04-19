package dev.heliosares.auxprotect.listeners;

import java.util.ArrayList;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.ItemStack;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.utils.InvSerialization;

public class ProjectileListener implements Listener {

	private AuxProtect plugin;
	ArrayList<EntityType> whitelist;

	public ProjectileListener(AuxProtect plugin) {
		this.plugin = plugin;
		this.whitelist = new ArrayList<>();
		whitelist.add(EntityType.ENDER_PEARL);
		whitelist.add(EntityType.TRIDENT);
		whitelist.add(EntityType.FISHING_HOOK);
		whitelist.add(EntityType.SNOWBALL);
		whitelist.add(EntityType.EGG);
		whitelist.add(EntityType.SPLASH_POTION);
		whitelist.add(EntityType.ARROW);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onProjectileLaunchEvent(ProjectileLaunchEvent e) {
		if (e.isCancelled()) {
			return;
		}
		if (!(e.getEntity().getShooter() instanceof Player)) {
			return;
		}
		if (!whitelist.contains(e.getEntity().getType())) {
			return;
		}
		logEntity(e.getEntity(), EntryAction.LAUNCH, true);
	}

	@EventHandler
	public void onProjectileHit(ProjectileHitEvent e) {
		if (e.getHitBlock() == null) {
			return;
		}
		if (!(e.getEntity().getShooter() instanceof Player)) {
			return;
		}
		if (!whitelist.contains(e.getEntity().getType())) {
			return;
		}
		logEntity(e.getEntity(), EntryAction.LAND, false);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerPickupArrowEvent(PlayerPickupArrowEvent e) {
		logEntity(e.getArrow(), EntryAction.GRAB, false);
	}

	private void logEntity(Projectile entity, EntryAction action, boolean logData) {
		Player shooter = (Player) entity.getShooter();
		String targetName = AuxProtect.getLabel(entity);
		ItemStack item = null;
		if (entity instanceof ThrowableProjectile) {
			item = ((ThrowableProjectile) entity).getItem();
		}
		if (entity instanceof ThrownPotion) {
			item = ((ThrownPotion) entity).getItem();
		}
		String data = null;
		if (item != null && logData && InvSerialization.isCustom(item)) {
			data = InvSerialization.toBase64(item);
		}

		DbEntry entry = new DbEntry(AuxProtect.getLabel(shooter), action, false, shooter.getLocation(), targetName,
				data == null ? "" : data);
		plugin.add(entry);
	}
}
