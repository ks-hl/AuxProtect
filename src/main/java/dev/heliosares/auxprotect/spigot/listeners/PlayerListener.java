package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SingleItemEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.PlaybackSolver;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
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
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class PlayerListener implements Listener {

    private final ArrayList<Material> buckets;
    private final ArrayList<EntityType> mobs;
    private final AuxProtectSpigot plugin;

    public PlayerListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
        buckets = new ArrayList<>();
        if (plugin.getCompatabilityVersion() >= 17) {
            buckets.add(Material.AXOLOTL_BUCKET);
        }
        buckets.add(Material.COD_BUCKET);
        buckets.add(Material.SALMON_BUCKET);
        buckets.add(Material.TROPICAL_FISH_BUCKET);
        buckets.add(Material.PUFFERFISH_BUCKET);
        mobs = new ArrayList<>();
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
    }

    public static void logMoney(AuxProtectSpigot plugin, Player player, String reason) {
        if (plugin.getEconomy() == null) {
            return;
        }
        plugin.getAPPlayer(player).lastLoggedMoney = System.currentTimeMillis();
        plugin.add(new DbEntry(AuxProtectSpigot.getLabel(player), EntryAction.MONEY, false, player.getLocation(),
                reason, plugin.formatMoney(plugin.getEconomy().getBalance(player))));
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
        plugin.getAPPlayer(e.getPlayer()).addActivity(1);

        ItemStack mainhand = e.getPlayer().getInventory().getItemInMainHand();
        ItemStack offhand = e.getPlayer().getInventory().getItemInOffHand();
        if (mainhand.getType() == Material.WATER_BUCKET || offhand.getType() == Material.WATER_BUCKET) {
            if (mobs.contains(e.getRightClicked().getType())) {
                DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.BUCKET, true,
                        e.getRightClicked().getLocation(), AuxProtectSpigot.getLabel(e.getRightClicked()), "");
                plugin.add(entry);
            }
        }
        if (e.getRightClicked() instanceof final ItemFrame item) {
            if (item.getItem().getType() == Material.AIR) {
                ItemStack added = e.getPlayer().getInventory().getItemInMainHand();
                if (added.getType() == Material.AIR) {
                    added = e.getPlayer().getInventory().getItemInOffHand();
                }
                if (added.getType() != Material.AIR) {
                    String data = "";
                    DbEntry entry = new SingleItemEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.ITEMFRAME, true,
                            item.getLocation(), added.getType().toString().toLowerCase(), data, added);
                    plugin.add(entry);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEvent(PlayerInteractEvent e) {
        plugin.getAPPlayer(e.getPlayer()).addActivity(1);

        if (e.useInteractedBlock() == Result.DENY) {
            return;
        }

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            if ((e.getItem() != null && buckets.contains(e.getItem().getType()))) {
                DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.BUCKET, false,
                        e.getClickedBlock().getLocation(), e.getItem().getType().toString().toLowerCase(), "");
                plugin.add(entry);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityToggleGlideEvent(EntityToggleGlideEvent e) {
        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getEntity()), EntryAction.ELYTRA, e.isGliding(),
                e.getEntity().getLocation(), "", "");
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerGameModeChangeEvent(PlayerGameModeChangeEvent e) {
        plugin.add(new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.GAMEMODE, false,
                e.getPlayer().getLocation(), e.getNewGameMode().toString(), ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        String sup = "";
        if (e.getItem().getType() == Material.POTION && e.getItem().getItemMeta() instanceof PotionMeta pm) {
            sup = pm.getBasePotionData().getType().toString().toLowerCase();
        }
        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.CONSUME, false,
                e.getPlayer().getLocation(), e.getItem().getType().toString().toLowerCase(), sup);
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoinEvent(PlayerJoinEvent e) {
        APPlayer apPlayer = plugin.getAPPlayer(e.getPlayer());
        apPlayer.lastMoved = System.currentTimeMillis();
        logMoney(plugin, e.getPlayer(), "join");
        String ip = e.getPlayer().getAddress().getHostString();
        logSession(e.getPlayer(), true, "IP: " + ip);
        new BukkitRunnable() {

            @Override
            public void run() {
                try {
                    plugin.getSqlManager().getUserManager().updateUsernameAndIP(e.getPlayer().getUniqueId(),
                            e.getPlayer().getName(), ip);
                } catch (SQLException ex) {
                    plugin.print(ex);
                }
            }
        }.runTaskAsynchronously(plugin);

        apPlayer.logInventory("join");

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (plugin.getSqlManager().getUserManager()
                            .getPendingInventory(plugin.getSqlManager().getUserManager()
                                    .getUIDFromUUID("$" + e.getPlayer().getUniqueId(), false)) == null) {
                        return;
                    }
                } catch (SQLException e1) {
                    return;
                }
                e.getPlayer().sendMessage(ChatColor.COLOR_CHAR + "aYou have an inventory waiting to be claimed!");
                e.getPlayer().sendMessage(ChatColor.COLOR_CHAR + "7Ensure you have room in your inventory before claiming!");
                ComponentBuilder message = new ComponentBuilder();
                message.append(ChatColor.COLOR_CHAR + "f\n         ");
                message.append(ChatColor.COLOR_CHAR + "a[Claim]").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claiminv"))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new Text(ChatColor.COLOR_CHAR + "aClick to claim your recovered inventory")));
                message.append("\n" + ChatColor.COLOR_CHAR + "f").event((ClickEvent) null).event((HoverEvent) null);
                e.getPlayer().spigot().sendMessage(message.create());
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
            }
        }.runTaskLaterAsynchronously(plugin, 40);

        if (plugin.update != null && APPermission.ADMIN.hasPermission(e.getPlayer())) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.tellAboutUpdate(e.getPlayer());
                }
            }.runTaskLater(plugin, 20);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMoveEvent(PlayerMoveEvent e) {
        APPlayer player = plugin.getAPPlayer(e.getPlayer());
        player.lastMoved = System.currentTimeMillis();
        player.hasMovedThisMinute = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null) return;
        APPlayer apPlayer = plugin.getAPPlayer(e.getPlayer());
        apPlayer.logPreTeleportPos(e.getFrom());
        apPlayer.logPostTeleportPos(e.getTo());
        apPlayer.lastLoggedPos = System.currentTimeMillis();
        boolean sameWorld = Objects.equals(e.getFrom().getWorld(), e.getTo().getWorld());
        if (!sameWorld || e.getFrom().distance(e.getTo()) > 64) PlaybackSolver.close(e.getPlayer().getUniqueId());

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
        plugin.add(new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.KICK, false,
                e.getPlayer().getLocation(), "", e.getReason()));
    }

    protected void logSession(Player player, boolean login, String supp) {
        plugin.add(new DbEntry(AuxProtectSpigot.getLabel(player), EntryAction.SESSION, login, player.getLocation(), "",
                supp));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLeashEntityEvent(PlayerLeashEntityEvent e) {
        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.LEASH, true,
                e.getEntity().getLocation(), AuxProtectSpigot.getLabel(e.getEntity()), "");
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

        boolean tether = entity.getLeashHolder().getType() == EntityType.LEASH_HITCH;

        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.LEASH, false,
                e.getEntity().getLocation(), AuxProtectSpigot.getLabel(e.getEntity()), tether ? "was tethered" : "");
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawnEvent(PlayerRespawnEvent e) {
        plugin.add(new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.RESPAWN, false,
                e.getRespawnLocation(), "", ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        plugin.getAPPlayer(e.getPlayer()).addActivity(5);

        plugin.add(new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.COMMAND, false,
                e.getPlayer().getLocation(), e.getMessage(), ""));
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        plugin.getAPPlayer(e.getPlayer()).addActivity(5);
        plugin.add(new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.CHAT, false, e.getPlayer().getLocation(), e.getMessage().trim(), ""));
        if (plugin.getAPConfig().isDemoMode()) {
            e.getPlayer().sendMessage(ChatColor.COLOR_CHAR + "cChat is disabled.");
            e.setCancelled(true);
        }
    }
}
