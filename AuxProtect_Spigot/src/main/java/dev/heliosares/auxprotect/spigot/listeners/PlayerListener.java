package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.adapters.message.ClickEvent;
import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.adapters.sender.SpigotSenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Activity;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SingleItemEntry;
import dev.heliosares.auxprotect.database.SpigotDbEntry;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.APPlayerSpigot;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.InvSerialization;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class PlayerListener implements Listener {

    private final Set<Material> buckets;
    private final Set<EntityType> mobs;
    private final AuxProtectSpigot plugin;

    public PlayerListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
        Set<Material> buckets = new HashSet<>();
        if (plugin.getCompatabilityVersion() >= 17) {
            buckets.add(Material.AXOLOTL_BUCKET);
        }
        buckets.add(Material.COD_BUCKET);
        buckets.add(Material.SALMON_BUCKET);
        buckets.add(Material.TROPICAL_FISH_BUCKET);
        buckets.add(Material.PUFFERFISH_BUCKET);
        this.buckets = Collections.unmodifiableSet(buckets);
        Set<EntityType> mobs = new HashSet<>();
        mobs.add(EntityType.PUFFERFISH);
        if (plugin.getCompatabilityVersion() >= 17) {
            mobs.add(EntityType.AXOLOTL);
        }
        if (plugin.getCompatabilityVersion() >= 19) {
            mobs.add(EntityType.TADPOLE);
        }
        mobs.add(EntityType.TROPICAL_FISH);
        mobs.add(EntityType.COD);
        mobs.add(EntityType.SALMON);
        this.mobs = Collections.unmodifiableSet(mobs);
    }

    public static void logMoney(AuxProtectSpigot plugin, Player player, String reason) {
        if (plugin.getEconomy() == null) {
            return;
        }
        plugin.getAPPlayer(player).lastLoggedMoney = System.currentTimeMillis();
        try {
            plugin.add(new SpigotDbEntry(AuxProtectSpigot.getLabel(player), EntryAction.MONEY, false, player.getLocation(),
                    reason, plugin.formatMoney(plugin.getEconomy().getBalance(player))));
        } catch (NullPointerException ignored) {
            // CMI producing a NullPointerException for an unknown reason, irrelevant to this plugin.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemDamageEvent(PlayerItemDamageEvent e) {
        ItemStack item = e.getItem();
        if (item.getItemMeta() instanceof Damageable meta) {
            if (item.getType().getMaxDurability() - meta.getDamage() - e.getDamage() <= 0) {
                EntityListener.itemBreak(plugin, AuxProtectSpigot.getLabel(e.getPlayer()), item,
                        e.getPlayer().getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent e) {
        plugin.getAPPlayer(e.getPlayer()).addActivity(Activity.INTERACT_ENTITY);

        ItemStack item = e.getPlayer().getInventory().getItem(e.getHand());

        if (item != null) {
            if (item.getType() == Material.NAME_TAG) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    plugin.add(new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.NAMETAG, true,
                            e.getRightClicked().getLocation(), AuxProtectSpigot.getLabel(e.getRightClicked()), meta.getDisplayName()));
                }
            }
            if (item.getType() == Material.WATER_BUCKET) {
                if (mobs.contains(e.getRightClicked().getType())) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        ItemStack newBucket = e.getPlayer().getInventory().getItem(e.getHand());
                        if (newBucket != null && !buckets.contains(newBucket.getType())) newBucket = null;
                        plugin.add(new SingleItemEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.BUCKET, true,
                                e.getRightClicked().getLocation(), AuxProtectSpigot.getLabel(e.getRightClicked()), "", newBucket));
                    }, 1);
                }
            }
        }
        if (e.getRightClicked() instanceof final ItemFrame itemFrame) {
            if (itemFrame.getItem().getType() == Material.AIR) {
                ItemStack added = e.getPlayer().getInventory().getItemInMainHand();
                if (added.getType() == Material.AIR) {
                    added = e.getPlayer().getInventory().getItemInOffHand();
                }
                if (added.getType() != Material.AIR) {
                    String data = "";
                    DbEntry entry = new SingleItemEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.ITEMFRAME, true,
                            itemFrame.getLocation(), added.getType().toString().toLowerCase(), data, added);
                    plugin.add(entry);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEvent(PlayerInteractEvent e) {
        plugin.getAPPlayer(e.getPlayer()).addActivity(e.getClickedBlock() == null ? Activity.INTERACT_AIR : Activity.INTERACT_BLOCK);

        if (e.useInteractedBlock() == Result.DENY) {
            return;
        }

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            if ((e.getItem() != null && buckets.contains(e.getItem().getType()))) {
                plugin.add(new SingleItemEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.BUCKET, false,
                        e.getClickedBlock().getLocation(), e.getItem().getType().toString().toLowerCase(), "", e.getItem()));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityToggleGlideEvent(EntityToggleGlideEvent e) {
        DbEntry entry = new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getEntity()), EntryAction.ELYTRA, e.isGliding(),
                e.getEntity().getLocation(), "", "");
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerGameModeChangeEvent(PlayerGameModeChangeEvent e) {
        plugin.add(new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.GAMEMODE, false,
                e.getPlayer().getLocation(), e.getNewGameMode().toString(), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        String sup = "";
        if (e.getItem().getType() == Material.POTION && e.getItem().getItemMeta() instanceof PotionMeta pm) {
            PotionType type = null;
            try {
                type = pm.getBasePotionType();
            } catch (Throwable error) {
                plugin.debug("Failed to get type of potion due to " + error.getClass().getName());
            }
            if (type != null) {
                sup = type.toString().toLowerCase();
            }
        }
        DbEntry entry = new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.CONSUME, false,
                e.getPlayer().getLocation(), e.getItem().getType().toString().toLowerCase(), sup);
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoinEvent(PlayerJoinEvent e) {
        SpigotSenderAdapter senderAdapter = new SpigotSenderAdapter(plugin, e.getPlayer());

        APPlayerSpigot apPlayer = plugin.getAPPlayer(e.getPlayer());
        apPlayer.lastMoved = System.currentTimeMillis();
        logMoney(plugin, e.getPlayer(), "join");
        if (e.getPlayer().getAddress() != null) {
            String ip = e.getPlayer().getAddress().getHostString();
            String data = "";
            if (plugin.getAPConfig().isSessionLogIP()) data = "IP: " + ip;
            logSession(e.getPlayer(), true, data);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.getSqlManager().getUserManager().updateUsernameAndIP(e.getPlayer().getUniqueId(),
                            e.getPlayer().getName(), ip);
                } catch (BusyException ex) {
                    plugin.warning("Database Busy: Unable to update username/ip for " + e.getPlayer().getName() + ", this may cause issues with lookups but will resolve when they relog and the database is not busy.");
                } catch (SQLException ex) {
                    plugin.print(ex);
                }
            });
            if (APPermission.LOOKUP.hasPermission(senderAdapter) || APPermission.CSLOGS.hasPermission(senderAdapter) && EntryAction.SHOP_CS.isEnabled()) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, apPlayer::getTimeZone);
            }
        }

        apPlayer.logInventory("join");

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                if (plugin.getSqlManager().getUserManager()
                        .getPendingInventory(plugin.getSqlManager().getUserManager()
                                .getUIDFromUUID("$" + e.getPlayer().getUniqueId(), false)) == null) {
                    return;
                }
            } catch (SQLException | BusyException e1) {
                return;
            }
            senderAdapter.sendLang(Language.L.COMMAND__INV__NOTIFY_PLAYER_WAITING);
            senderAdapter.sendLang(Language.L.COMMAND__INV__NOTIFY_PLAYER_ENSURE_ROOM);
            GenericBuilder message = new GenericBuilder(plugin);
            message.newLine();
            message.append("         ");
            if (e.getPlayer().getUniqueId().getMostSignificantBits() == 0) { // bedrock player
                message.append(Language.L.COMMAND__INV__NOTIFY_PLAYER_CLAIM_ALT.translate());
            } else {
                message.append(Language.L.COMMAND__INV__NOTIFY_PLAYER_CLAIM_BUTTON.translate()).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claiminv"))
                        .hover(Language.L.COMMAND__CLAIMINV__CLAIM_BUTTON__HOVER.translate());
            }
            message.newLine();
            message.send(new SpigotSenderAdapter(plugin, e.getPlayer()));
            e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
        }, 40);

        if (plugin.update != null && APPermission.ADMIN.hasPermission(senderAdapter)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.tellAboutUpdate(e.getPlayer()), 20);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMoveEvent(PlayerMoveEvent e) {
        APPlayerSpigot player = plugin.getAPPlayer(e.getPlayer());
        player.lastMoved = System.currentTimeMillis();
        player.hasMovedThisMinute = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null) return;
        APPlayerSpigot apPlayer = plugin.getAPPlayer(e.getPlayer());
        apPlayer.logPreTeleportPos(e.getFrom());
        apPlayer.logPostTeleportPos(e.getTo());
        apPlayer.lastLoggedPos = System.currentTimeMillis();
        boolean sameWorld = Objects.equals(e.getFrom().getWorld(), e.getTo().getWorld());

        if (!plugin.getAPConfig().isInventoryOnWorldChange() || sameWorld) return;

        byte[] inventory_ = null;
        try {
            inventory_ = InvSerialization.playerToByteArray(e.getPlayer());
        } catch (Exception e1) {
            plugin.warning("Error serializing inventory for teleport");
            plugin.print(e1);
        }
        final byte[] inventory = inventory_;

        new BukkitRunnable() {
            @Override
            public void run() {
                byte[] newInventory = null;
                try {
                    newInventory = InvSerialization.playerToByteArray(e.getPlayer());
                } catch (Exception e1) {
                    plugin.warning("Error serializing inventory for teleport");
                    plugin.print(e1);
                }
                if (Arrays.equals(inventory, newInventory)) {
                    return;
                }
                apPlayer.logInventory("worldchange", e.getFrom(), inventory);
                apPlayer.logInventory("worldchange", e.getTo(), newInventory);
            }

        }.runTaskLater(plugin, 3);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuitEvent(PlayerQuitEvent e) {
        logMoney(plugin, e.getPlayer(), "leave");
        logSession(e.getPlayer(), false, "");

        plugin.getAPPlayer(e.getPlayer()).logInventory("quit");

        plugin.removeAPPlayer(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKickEvent(PlayerKickEvent e) {
        plugin.add(new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.KICK, false,
                e.getPlayer().getLocation(), "", e.getReason()));
    }

    protected void logSession(Player player, boolean login, String supp) {
        plugin.add(new SpigotDbEntry(AuxProtectSpigot.getLabel(player), EntryAction.SESSION, login, player.getLocation(), "",
                supp));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLeashEntityEvent(PlayerLeashEntityEvent e) {
        DbEntry entry = new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.LEASH, true,
                e.getEntity().getLocation(), AuxProtectSpigot.getLabel(e.getEntity()), "");
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlaceEvent(EntityPlaceEvent e) {
        DbEntry entry = new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.ENTITY, true,
                e.getEntity().getLocation(), AuxProtectSpigot.getLabel(e.getEntity()), "");
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleDestroyEvent(VehicleDestroyEvent e) {
        DbEntry entry = new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getAttacker()), EntryAction.ENTITY, false,
                e.getVehicle().getLocation(), AuxProtectSpigot.getLabel(e.getVehicle()), "");
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerUnleashEntityEvent(PlayerUnleashEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (!entity.isLeashed()) {
            return;
        }

        boolean tether = entity.getLeashHolder().getType().toString().startsWith("LEASH_");

        DbEntry entry = new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.LEASH, false,
                e.getEntity().getLocation(), AuxProtectSpigot.getLabel(e.getEntity()), tether ? "was tethered" : "");
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawnEvent(PlayerRespawnEvent e) {
        plugin.add(new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.RESPAWN, false,
                e.getRespawnLocation(), "", ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        plugin.getAPPlayer(e.getPlayer()).addActivity(Activity.COMMAND);

        plugin.add(new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.COMMAND, false,
                e.getPlayer().getLocation(), e.getMessage(), ""));
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        plugin.getAPPlayer(e.getPlayer()).addActivity(Activity.CHAT);
        plugin.add(new SpigotDbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.CHAT, false, e.getPlayer().getLocation(), e.getMessage().trim(), ""));
        if (plugin.getAPConfig().isDemoMode()) {
            e.getPlayer().sendMessage(GenericTextColor.RED + "Chat is disabled.");
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent e) {
        plugin.getAPPlayer(e.getPlayer()).addActivity(Activity.BLOCK_PLACE);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent e) {
        plugin.getAPPlayer(e.getPlayer()).addActivity(Activity.BLOCK_BREAK);
    }
}
