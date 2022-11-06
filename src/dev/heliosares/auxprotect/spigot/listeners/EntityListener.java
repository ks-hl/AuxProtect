package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.PickupEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.ChartRenderer;
import dev.heliosares.auxprotect.utils.InvSerialization;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.projectiles.ProjectileSource;
import org.spigotmc.event.entity.EntityDismountEvent;
import org.spigotmc.event.entity.EntityMountEvent;

import java.util.ArrayList;

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
        if (e.getEntity() instanceof ItemFrame) {
            final ItemFrame item = (ItemFrame) e.getEntity();
            if (item.getItem() != null) {
                DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getDamager()), EntryAction.ITEMFRAME, false,
                        item.getLocation(), item.getItem().getType().toString().toLowerCase(), "");
                if (InvSerialization.isCustom(item.getItem())) {
                    try {
                        entry.setBlob(InvSerialization.toByteArray(item.getItem()));
                    } catch (Exception e1) {
                        plugin.warning("Error serializing itemframe");
                        plugin.print(e1);
                    }
                }
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
            plugin.getAPPlayer(sourcePl).addActivity(0.25);
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
        if (e.getEntity() instanceof Item) {
            ItemStack item = ((Item) e.getEntity()).getItemStack();
            if (item != null && item.getType() != Material.AIR) {
                DbEntry entry = new DbEntry(sourceName, EntryAction.BREAKITEM, false, e.getEntity().getLocation(),
                        AuxProtectSpigot.getLabel(item.getType()), "");

                if (InvSerialization.isCustom(item)) {
                    try {
                        entry.setBlob(InvSerialization.toByteArray(item));
                    } catch (Exception e1) {
                        plugin.warning("Error serializing broken item");
                        plugin.print(e1);
                    }
                } else if (item.getAmount() > 1) {
                    entry.setData("x" + item.getAmount());
                }
                plugin.add(entry);
                // Going to log both for now. Repetitive, but it seems more intuitive.
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
        if (e.getEntity() instanceof Player) {
            Player player = (Player) e.getEntity();

            plugin.getAPPlayer(player).addActivity(1);

            if (isChartMap(e.getItem().getItemStack()) && !APPermission.LOOKUP_MONEY.hasPermission(player)) {
                e.setCancelled(true);
                e.getItem().remove();
            }
        }

        drop(e.getEntity(), e.getItem().getLocation(), e.getItem().getItemStack(), false);

    }

    private void drop(Entity entity, Location loc, ItemStack item, boolean drop) {
        DbEntry entry = null;
        if (InvSerialization.isCustom(item)) {
            entry = new DbEntry(AuxProtectSpigot.getLabel(entity), drop ? EntryAction.DROP : EntryAction.PICKUP, false,
                    loc, item.getType().toString().toLowerCase(), "");
            try {
                entry.setBlob(InvSerialization.toByteArray(item));
            } catch (Exception e1) {
                plugin.warning("Error serializing item drop/pickup");
                plugin.print(e1);
            }

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
