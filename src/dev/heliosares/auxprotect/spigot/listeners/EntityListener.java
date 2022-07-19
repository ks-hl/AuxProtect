package dev.heliosares.auxprotect.spigot.listeners;

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
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.spigotmc.event.entity.EntityDismountEvent;
import org.spigotmc.event.entity.EntityMountEvent;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.PickupEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.ChartRenderer;
import dev.heliosares.auxprotect.utils.InvSerialization;

public class EntityListener implements Listener {

	private AuxProtectSpigot plugin;
	ArrayList<DamageCause> blacklistedDamageCauses;

	public EntityListener(AuxProtectSpigot plugin) {
		this.plugin = plugin;
		this.blacklistedDamageCauses = new ArrayList<>();
		blacklistedDamageCauses.add(DamageCause.ENTITY_ATTACK);
		blacklistedDamageCauses.add(DamageCause.PROJECTILE);
		blacklistedDamageCauses.add(DamageCause.ENTITY_EXPLOSION);
		blacklistedDamageCauses.add(DamageCause.ENTITY_SWEEP_ATTACK);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityDismountEvent(EntityDismountEvent e) {
		DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getEntity()), EntryAction.MOUNT, false,
				e.getDismounted().getLocation(), AuxProtectSpigot.getLabel(e.getDismounted()), "");
		plugin.add(entry);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityMountEvent(EntityMountEvent e) {
		DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getEntity()), EntryAction.MOUNT, true,
				e.getMount().getLocation(), AuxProtectSpigot.getLabel(e.getMount()), "");
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
				DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getDamager()), EntryAction.ITEMFRAME, false,
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
		String targetName = AuxProtectSpigot.getLabel(e.getEntity());
		String sourceName = source == null ? targetName : AuxProtectSpigot.getLabel(source);

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
		DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getEntity()), EntryAction.TOTEM, false,
				e.getEntity().getLocation(), "", "");
		plugin.add(entry);
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
		String targetName = AuxProtectSpigot.getLabel(e.getEntity());

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
		DbEntry entry1 = new DbEntry(AuxProtectSpigot.getLabel(e.getEntity()), EntryAction.INVENTORY, false,
				e.getEntity().getLocation(), "death", InvSerialization.playerToBase64(e.getEntity()));
		plugin.add(entry1);
		plugin.getAPPlayer(e.getEntity()).lastLoggedInventory = System.currentTimeMillis();
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDropEvent(EntityDropItemEvent e) {
		if (e.getEntity() instanceof Player) {
			plugin.getAPPlayer((Player) e.getEntity()).addActivity(1);

			if (isChartMap(e.getItemDrop().getItemStack())) {
				e.getItemDrop().remove();
				return;
			}
		}

		if (InvSerialization.isCustom(e.getItemDrop().getItemStack())) {
			plugin.add(new DbEntry(AuxProtectSpigot.getLabel(e.getEntity()), EntryAction.DROP, false,
					e.getEntity().getLocation(), e.getItemDrop().getItemStack().getType().toString().toLowerCase(),
					InvSerialization.toBase64(e.getItemDrop().getItemStack())));
		} else {
			plugin.add(new PickupEntry(AuxProtectSpigot.getLabel(e.getEntity()), EntryAction.DROP, false,
					e.getEntity().getLocation(), e.getItemDrop().getItemStack().getType().toString().toLowerCase(),
					e.getItemDrop().getItemStack().getAmount()));
		}

	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPickupEvent(EntityPickupItemEvent e) {
		if (e.getEntity() instanceof Player) {
			Player player = (Player) e.getEntity();

			plugin.getAPPlayer(player).addActivity(1);

			if (isChartMap(e.getItem().getItemStack()) && !APPermission.LOOKUP_MONEY.hasPermission(player)) {
				e.setCancelled(true);
				e.getItem().remove();
			}
		}

		if (InvSerialization.isCustom(e.getItem().getItemStack())) {
			plugin.add(new DbEntry(AuxProtectSpigot.getLabel(e.getEntity()), EntryAction.PICKUP, false,
					e.getItem().getLocation(), e.getItem().getItemStack().getType().toString().toLowerCase(),
					InvSerialization.toBase64(e.getItem().getItemStack())));
		} else {
			plugin.add(new PickupEntry(AuxProtectSpigot.getLabel(e.getEntity()), EntryAction.PICKUP, false,
					e.getItem().getLocation(), e.getItem().getItemStack().getType().toString().toLowerCase(),
					e.getItem().getItemStack().getAmount()));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onTame(EntityTameEvent e) {
		DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getOwner()), EntryAction.TAME, false,
				e.getEntity().getLocation(), AuxProtectSpigot.getLabel(e.getEntity()), "");
		plugin.add(entry);
	}

	public static boolean isChartMap(ItemStack item) {
		if (item.getType() == Material.FILLED_MAP && item.hasItemMeta()) {
			if (item.getItemMeta() instanceof MapMeta) {
				MapMeta meta = (MapMeta) item.getItemMeta();
				for (MapRenderer renderer : meta.getMapView().getRenderers()) {
					if (renderer instanceof ChartRenderer) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
