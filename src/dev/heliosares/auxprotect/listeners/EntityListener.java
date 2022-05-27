package dev.heliosares.auxprotect.listeners;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.spigotmc.event.entity.EntityDismountEvent;
import org.spigotmc.event.entity.EntityMountEvent;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.utils.InvSerialization;

public class EntityListener implements Listener {

	private AuxProtect plugin;
	ArrayList<DamageCause> blacklistedDamageCauses;

	public EntityListener(AuxProtect plugin) {
		this.plugin = plugin;
		this.blacklistedDamageCauses = new ArrayList<>();
		blacklistedDamageCauses.add(DamageCause.ENTITY_ATTACK);
		blacklistedDamageCauses.add(DamageCause.PROJECTILE);
		blacklistedDamageCauses.add(DamageCause.ENTITY_EXPLOSION);
		blacklistedDamageCauses.add(DamageCause.ENTITY_SWEEP_ATTACK);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityDismountEvent(EntityDismountEvent e) {
		DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getEntity()), EntryAction.MOUNT, false,
				e.getDismounted().getLocation(), AuxProtect.getLabel(e.getDismounted()), "");
		plugin.add(entry);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityMountEvent(EntityMountEvent e) {
		DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getEntity()), EntryAction.MOUNT, true,
				e.getMount().getLocation(), AuxProtect.getLabel(e.getMount()), "");
		plugin.add(entry);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void entityDamageByEntityEvent(EntityDamageByEntityEvent e) {
		if (e.getEntity() instanceof ItemFrame) {
			final ItemFrame item = (ItemFrame) e.getEntity();
			if (item.getItem() != null) {
				String data = "";
				if (InvSerialization.isCustom(item.getItem())) {
					data = InvSerialization.toBase64(item.getItem());
				}
				DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getDamager()), EntryAction.ITEMFRAME, false,
						item.getLocation(), item.getItem().getType().toString().toLowerCase(), data);
				plugin.add(entry);
				return;
			}
		}
		if (e.getEntity() instanceof LivingEntity) {
			if (((LivingEntity) e.getEntity()).isDead()) {
				return;
			}
		}
		String itemname = "";
		Entity source = e.getDamager();
		if (e.getDamager() instanceof Projectile) {
			Projectile projectile = (Projectile) e.getDamager();
			ProjectileSource projsource = projectile.getShooter();
			if (projsource instanceof LivingEntity) {
				source = (LivingEntity) projsource;
			}
			if (source instanceof Player)
				itemname = "shot with ";
		}
		String targetName = AuxProtect.getLabel(e.getEntity());
		String sourceName = source == null ? targetName : AuxProtect.getLabel(source);

		if (source instanceof Player) {
			Player sourcePl = (Player) source;
			itemname += sourcePl.getInventory().getItemInMainHand().getType().toString().toLowerCase();
		}

		if (!itemname.equals("")) {
			itemname += ", ";
		}
		itemname += (Math.round(e.getFinalDamage() * 10) / 10.0) + "HP";

		EntryAction action = EntryAction.HURT;
		if (e.getEntity() instanceof LivingEntity
				&& ((LivingEntity) e.getEntity()).getHealth() - e.getFinalDamage() <= 0) {
			boolean totem = false;
			LivingEntity livingEntity = (LivingEntity) e.getEntity();
			EntityEquipment equip = livingEntity.getEquipment();
			if (equip != null) {
				ItemStack hand = equip.getItem(EquipmentSlot.HAND);
				ItemStack offhand = equip.getItem(EquipmentSlot.OFF_HAND);
				if (hand != null && hand.getType() == Material.TOTEM_OF_UNDYING) {
					totem = true;
				} else if (offhand != null && offhand.getType() == Material.TOTEM_OF_UNDYING) {
					totem = true;
				}
			}
			if (!totem) {
				action = EntryAction.KILL;
			}
		}
		DbEntry entry = new DbEntry(sourceName, action, false, e.getEntity().getLocation(), targetName, itemname);
		plugin.add(entry);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onResurrect(EntityResurrectEvent e) {
		DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getEntity()), EntryAction.TOTEM, false,
				e.getEntity().getLocation(), "", "");
		plugin.add(entry);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHangingBreakEvent(HangingBreakEvent e) {
		if (e.getCause() == RemoveCause.ENTITY) {
			return;
		}
		if (e.getEntity() instanceof ItemFrame) {
			final ItemFrame item = (ItemFrame) e.getEntity();
			if (item.getItem() != null) {
				String data = "";
				if (InvSerialization.isCustom(item.getItem())) {
					data = InvSerialization.toBase64(item.getItem());
				}
				DbEntry entry = new DbEntry("#" + e.getCause().toString().toLowerCase(), EntryAction.ITEMFRAME, false,
						item.getLocation(), item.getItem().getType().toString().toLowerCase(), data);
				plugin.add(entry);
				return;
			}
		}

	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void entityDamageEvent(EntityDamageEvent e) {
		if (e.getEntity().isDead()) {
			return;
		}
		if (blacklistedDamageCauses.contains(e.getCause())) {
			return;
		}
		if (e.getCause() == DamageCause.SUFFOCATION && e.getEntity().getType() == EntityType.ARMOR_STAND) {
			return;
		}
		String targetName = AuxProtect.getLabel(e.getEntity());

		EntryAction reason = EntryAction.HURT;
		if (e.getEntity() instanceof LivingEntity
				&& ((LivingEntity) e.getEntity()).getHealth() - e.getFinalDamage() <= 0) {
			reason = EntryAction.KILL;
		}
		DbEntry entry = new DbEntry("#env", reason, false, e.getEntity().getLocation(), targetName,
				e.getCause().toString() + ", " + (Math.round(e.getFinalDamage() * 10) / 10.0) + "HP");
		plugin.add(entry);
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent e) {
		DbEntry entry1 = new DbEntry(AuxProtect.getLabel(e.getEntity()), EntryAction.INVENTORY, false,
				e.getEntity().getLocation(), "death", InvSerialization.playerToBase64(e.getEntity()));
		plugin.add(entry1);
		plugin.getAPPlayer(e.getEntity()).lastLoggedInventory = System.currentTimeMillis();
	}
}
