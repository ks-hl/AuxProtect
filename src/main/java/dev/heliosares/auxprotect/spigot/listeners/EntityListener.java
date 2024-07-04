package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.PickupEntry;
import dev.heliosares.auxprotect.database.SingleItemEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.ChartRenderer;
import dev.heliosares.auxprotect.utils.InvSerialization;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;

public class EntityListener implements Listener {

    final ArrayList<DamageCause> blacklistedDamageCauses;
    private final AuxProtectSpigot plugin;

    public EntityListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
        this.blacklistedDamageCauses = new ArrayList<>();
        blacklistedDamageCauses.add(DamageCause.ENTITY_ATTACK);
        blacklistedDamageCauses.add(DamageCause.PROJECTILE);
        blacklistedDamageCauses.add(DamageCause.ENTITY_EXPLOSION);
        blacklistedDamageCauses.add(DamageCause.ENTITY_SWEEP_ATTACK);
    }

    protected static void itemBreak(IAuxProtect plugin, String cause, ItemStack item, Location location) {
        DbEntry entry = new SingleItemEntry(cause, EntryAction.BREAKITEM, false, location,
                AuxProtectSpigot.getLabel(item.getType()), "", item);
        plugin.add(entry);
    }

    public static boolean isChartMap(ItemStack item) {
        if (item.getType() == Material.FILLED_MAP && item.hasItemMeta()) {
            if (item.getItemMeta() instanceof MapMeta meta && meta.hasMapView()) {
                for (MapRenderer renderer : meta.getMapView().getRenderers()) {
                    if (renderer instanceof ChartRenderer) {
                        return true;
                    }
                }
            }
        }
        return false;
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
    public void onEntityExplodeEvent(EntityExplodeEvent e) {
        String cause;
        if (e.getEntity() instanceof TNTPrimed tnt && tnt.getSource() != null) {
            if (tnt.getSource() instanceof Projectile proj && proj.getShooter() != null) {
                cause = AuxProtectSpigot.getLabel(proj.getShooter());
            } else {
                cause = AuxProtectSpigot.getLabel(tnt.getSource());
            }
        } else if (e.getEntity() instanceof LivingEntity) {
            if (e.getEntity() instanceof Monster monster && monster.getTarget() != null) {
                cause = AuxProtectSpigot.getLabel(monster.getTarget());
            } else {
                cause = AuxProtectSpigot.getLabel(e.getEntity());
            }
        } else if (e.getEntity() instanceof Projectile proj && proj.getShooter() != null) {
            cause = AuxProtectSpigot.getLabel(proj.getShooter());
        } else {
            cause = "#env";
        }
        DbEntry entry = new DbEntry(cause, EntryAction.EXPLODE, true, e.getEntity().getLocation(),
                AuxProtectSpigot.getLabel(e.getEntity()), "");
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void entityDamageByEntityEvent(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof final ItemFrame item) {
            DbEntry entry = new SingleItemEntry(AuxProtectSpigot.getLabel(e.getDamager()), EntryAction.ITEMFRAME, false,
                    item.getLocation(), item.getItem().getType().toString().toLowerCase(), "", item.getItem());
            plugin.add(entry);
            return;
        }
        if (e.getEntity() instanceof LivingEntity) {
            if (e.getEntity().isDead()) {
                return;
            }
        }
        String itemname = "";
        Entity source = e.getDamager();
        if (e.getDamager() instanceof Projectile projectile) {
            ProjectileSource projsource = projectile.getShooter();
            if (projsource instanceof LivingEntity) {
                source = (LivingEntity) projsource;
            }
            if (source instanceof Player)
                itemname = "shot with ";
        }
        String targetName = AuxProtectSpigot.getLabel(e.getEntity());
        String sourceName = AuxProtectSpigot.getLabel(source);

        if (e.getCause() == DamageCause.THORNS) {
            itemname += "THORNS";
        } else if (source instanceof Player sourcePl) {
            plugin.getAPPlayer(sourcePl).addActivity(0.25);
            itemname += sourcePl.getInventory().getItemInMainHand().getType().toString().toLowerCase();
        }

        if (!itemname.isEmpty()) {
            itemname += ", ";
        }
        itemname += (Math.round(e.getFinalDamage() * 10) / 10.0) + "HP";

        EntryAction action = EntryAction.HURT;
        if (e.getEntity() instanceof LivingEntity livingEntity
                && ((LivingEntity) e.getEntity()).getHealth() - e.getFinalDamage() <= 0) {
            boolean totem = false;
            EntityEquipment equip = livingEntity.getEquipment();
            if (equip != null) {
                ItemStack hand = equip.getItem(EquipmentSlot.HAND);
                ItemStack offhand = equip.getItem(EquipmentSlot.OFF_HAND);
                if (hand.getType() == Material.TOTEM_OF_UNDYING) {
                    totem = true;
                } else if (offhand.getType() == Material.TOTEM_OF_UNDYING) {
                    totem = true;
                }
            }
            if (!totem) {
                action = EntryAction.KILL;
            }
        }
        if (e.getEntity() instanceof Item) {
            ItemStack item = ((Item) e.getEntity()).getItemStack();
            if (item.getType() != Material.AIR) {
                itemBreak(plugin, sourceName, item, e.getEntity().getLocation());
                // Going to log both for now. Repetitive, but it seems more intuitive.
            }
        }
        DbEntry entry = new DbEntry(sourceName, action, false, e.getEntity().getLocation(), targetName, itemname);
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent e) {
        itemBreak(plugin, "#despawn", e.getEntity().getItemStack(), e.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void entityDamageEvent(EntityDamageEvent e) {
        if (e.getEntity() instanceof Item item) {
            new BukkitRunnable() {

                @Override
                public void run() {
                    if (e.getEntity().isDead() || !e.getEntity().isValid()) {
                        itemBreak(plugin, "#" + e.getCause(), item.getItemStack(), item.getLocation());
                    }
                }
            }.runTaskLater(plugin, 1);
        }
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
                e.getCause() + ", " + (Math.round(e.getFinalDamage() * 10) / 10.0) + "HP");
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent e) {
        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getEntity()), EntryAction.TOTEM, false,
                e.getEntity().getLocation(), "", "");
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDeathLowest(PlayerDeathEvent e) {
        if (EntryAction.INVENTORY.isLowestpriority()) {
            plugin.getAPPlayer(e.getEntity()).logInventory("death");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeathMonitor(PlayerDeathEvent e) {
        if (!EntryAction.INVENTORY.isLowestpriority()) {
            plugin.getAPPlayer(e.getEntity()).logInventory("death");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDropEvent(EntityDropItemEvent e) {
        drop(e.getEntity(), e.getItemDrop().getLocation(), e.getItemDrop().getItemStack(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropEvent(PlayerDropItemEvent e) {
        plugin.getAPPlayer(e.getPlayer()).addActivity(1);

        if (isChartMap(e.getItemDrop().getItemStack())) {
            e.getItemDrop().remove();
            return;
        }

        drop(e.getPlayer(), e.getItemDrop().getLocation(), e.getItemDrop().getItemStack(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupEvent(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player player) {

            plugin.getAPPlayer(player).addActivity(1);

            if (isChartMap(e.getItem().getItemStack()) && !APPermission.LOOKUP_MONEY.hasPermission(player)) {
                e.setCancelled(true);
                e.getItem().remove();
            }
        }

        drop(e.getEntity(), e.getItem().getLocation(), e.getItem().getItemStack(), false);

    }

    private void drop(Entity entity, Location loc, ItemStack item, boolean drop) {
        DbEntry entry;
        if (InvSerialization.isCustom(item)) {
            entry = new SingleItemEntry(AuxProtectSpigot.getLabel(entity), drop ? EntryAction.DROP : EntryAction.PICKUP, false,
                    loc, item.getType().toString().toLowerCase(), "", item);
        } else {
            entry = new PickupEntry(AuxProtectSpigot.getLabel(entity), drop ? EntryAction.DROP : EntryAction.PICKUP,
                    false, loc, item.getType().toString().toLowerCase(), item.getAmount());
        }
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getOwner()), EntryAction.TAME, false,
                e.getEntity().getLocation(), AuxProtectSpigot.getLabel(e.getEntity()), "");
        plugin.add(entry);
    }

}
