package dev.heliosares.auxprotect.listeners;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.scheduler.BukkitRunnable;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.PickupEntry;
import dev.heliosares.auxprotect.database.SQLiteManager.LookupException;
import dev.heliosares.auxprotect.utils.ChartRenderer;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.MyPermission;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class PlayerListener implements Listener {

	private AuxProtect plugin;
	private final ArrayList<Material> buckets;
	private final ArrayList<EntityType> mobs;

	public PlayerListener(AuxProtect plugin) {
		this.plugin = plugin;
		buckets = new ArrayList<>();
		buckets.add(Material.AXOLOTL_BUCKET);
		buckets.add(Material.COD_BUCKET);
		buckets.add(Material.SALMON_BUCKET);
		buckets.add(Material.TROPICAL_FISH_BUCKET);
		buckets.add(Material.PUFFERFISH_BUCKET);
		mobs = new ArrayList<>();
		mobs.add(EntityType.PUFFERFISH);
		mobs.add(EntityType.AXOLOTL);
		mobs.add(EntityType.TROPICAL_FISH);
		mobs.add(EntityType.COD);
		mobs.add(EntityType.SALMON);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent e) {
		if (e.isCancelled()) {
			return;
		}
		ItemStack mainhand = e.getPlayer().getInventory().getItemInMainHand();
		ItemStack offhand = e.getPlayer().getInventory().getItemInOffHand();
		if ((mainhand != null && mainhand.getType() == Material.WATER_BUCKET)
				|| (offhand != null && offhand.getType() == Material.WATER_BUCKET)) {
			DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.BUCKET, true,
					e.getRightClicked().getLocation(), AuxProtect.getLabel(e.getRightClicked()), "");
			plugin.dbRunnable.add(entry);
		}
		if (e.getRightClicked() instanceof ItemFrame) {
			final ItemFrame item = (ItemFrame) e.getRightClicked();
			if (item.getItem() == null || item.getItem().getType() == Material.AIR) {
				ItemStack added = e.getPlayer().getInventory().getItemInMainHand();
				if (added == null || added.getType() == Material.AIR) {
					added = e.getPlayer().getInventory().getItemInOffHand();
				}
				if (added != null && added.getType() != Material.AIR) {
					String data = "";
					if (InvSerialization.isCustom(added)) {
						data = InvSerialization.toBase64(added);
					}
					DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.ITEMFRAME, true,
							item.getLocation(), added.getType().toString().toLowerCase(), data);
					plugin.dbRunnable.add(entry);
				}
			}

		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerInteractEvent(PlayerInteractEvent e) {
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
			if ((e.getItem() != null && e.getItem().getType() != null && buckets.contains(e.getItem().getType()))) {
				DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.BUCKET, false,
						e.getClickedBlock().getLocation(), e.getItem().getType().toString().toLowerCase(), "");
				plugin.dbRunnable.add(entry);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onConsume(PlayerItemConsumeEvent e) {
		if (e.isCancelled()) {
			return;
		}
		String sup = "";
		if (e.getItem().getType() == Material.POTION && e.getItem().getItemMeta() instanceof PotionMeta) {
			PotionMeta pm = (PotionMeta) e.getItem().getItemMeta();
			sup = pm.getBasePotionData().getType().toString().toLowerCase();
		}
		DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.CONSUME, false,
				e.getPlayer().getLocation(), e.getItem().getType().toString().toLowerCase(), sup);
		plugin.dbRunnable.add(entry);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoinEvent(PlayerJoinEvent e) {
		logMoney(plugin, e.getPlayer(), "join");
		String ip = e.getPlayer().getAddress().getHostString();
		logSession(e.getPlayer(), true, "IP: " + ip);
		plugin.getSqlManager().updateUsername(e.getPlayer().getUniqueId().toString(), e.getPlayer().getName());
		new BukkitRunnable() {

			@Override
			public void run() {
				HashMap<String, String> params = new HashMap<>();
				params.put("user", "$" + e.getPlayer().getUniqueId());
				params.put("action", "ip,username");

				ArrayList<DbEntry> results = null;
				try {
					results = plugin.getSqlManager().lookup(params, null, false);
				} catch (LookupException e1) {
					plugin.warning(e1.toString());
					return;
				}
				if (results == null)
					return;
				boolean newip = true;
				String newestusername = "";
				long highestusername = 0;
				for (DbEntry entry : results) {
					if (entry.getAction() == EntryAction.IP) {
						if (!newip) {
							continue;
						}
						if (entry.getTarget().equals(ip)) {
							newip = false;
						}
					}
					if (entry.getAction() == EntryAction.USERNAME) {
						if (entry.getTime() > highestusername) {
							highestusername = entry.getTime();
							newestusername = entry.getTarget();
						}
					}
				}
				if (newip) {
					plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.IP, false, "", 0,
							0, 0, ip, ""));
				}
				if (!e.getPlayer().getName().equals(newestusername)) {
					plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.USERNAME, false,
							"", 0, 0, 0, e.getPlayer().getName(), ""));
				}
			}
		}.runTaskAsynchronously(plugin);

		plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.INVENTORY, false,
				e.getPlayer().getLocation(), "join", InvSerialization.playerToBase64(e.getPlayer())));
		plugin.lastLogOfInventoryForUUID.put(e.getPlayer().getUniqueId().toString(), System.currentTimeMillis());

		final String data = plugin.data.getData().getString("Recoverables." + e.getPlayer().getUniqueId().toString());
		if (data != null) {
			new BukkitRunnable() {

				@Override
				public void run() {
					e.getPlayer().sendMessage("§aYou have an inventory waiting to be claimed!");
					e.getPlayer().sendMessage("§7Ensure you have room in your inventory before claiming!");
					ComponentBuilder message = new ComponentBuilder();
					message.append("§f\n         ");
					message.append("§a[Claim]").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claiminv"))
							.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
									new Text("§aClick to claim your recovered inventory")));
					message.append("\n§f").event((ClickEvent) null).event((HoverEvent) null);
					e.getPlayer().spigot().sendMessage(message.create());
					e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
				}
			}.runTaskLater(plugin, 60);
		}

		if (plugin.update != null && MyPermission.ADMIN.hasPermission(e.getPlayer())) {
			new BukkitRunnable() {
				@Override
				public void run() {
					plugin.tellAboutUpdate(e.getPlayer());
				}
			}.runTaskLater(plugin, 20);
		}
	}

	@EventHandler
	public void onWorldChange(PlayerTeleportEvent e) {
		if (!plugin.config.inventoryOnWorldChange || e.getFrom().getWorld().equals(e.getTo().getWorld())) {
			return;
		}
		final String inventory = InvSerialization.playerToBase64(e.getPlayer());
		final Location oldLocation = e.getPlayer().getLocation().clone();

		new BukkitRunnable() {
			@Override
			public void run() {
				String newInventory = InvSerialization.playerToBase64(e.getPlayer());
				if (newInventory.equals(inventory)) {
					return;
				}
				plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.INVENTORY, false,
						oldLocation, "worldchange", inventory));
				plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.INVENTORY, false,
						e.getPlayer().getLocation(), "worldchange", newInventory));
			}

		}.runTaskLater(plugin, 5);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuitEvent(PlayerQuitEvent e) {
		logMoney(plugin, e.getPlayer(), "leave");
		logSession(e.getPlayer(), false, "");

		plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.INVENTORY, false,
				e.getPlayer().getLocation(), "quit", InvSerialization.playerToBase64(e.getPlayer())));

	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerKickEvent(PlayerKickEvent e) {
		if (e.isCancelled()) {
			return;
		}
		plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.KICK, false,
				e.getPlayer().getLocation(), "", e.getReason()));
	}

	public static void logMoney(AuxProtect plugin, Player player, String reason) {
		if (plugin.getEconomy() == null) {
			return;
		}
		plugin.lastLogOfMoneyForUUID.put(player.getUniqueId().toString(), System.currentTimeMillis());
		plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(player), EntryAction.MONEY, false, player.getLocation(),
				reason, plugin.formatMoney(plugin.getEconomy().getBalance(player))));
	}

	protected void logSession(Player player, boolean login, String supp) {
		plugin.dbRunnable.add(
				new DbEntry(AuxProtect.getLabel(player), EntryAction.SESSION, login, player.getLocation(), "", supp));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerLeashEntityEvent(PlayerLeashEntityEvent e) {
		if (e.isCancelled()) {
			return;
		}

		DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.LEASH, true,
				e.getEntity().getLocation(), AuxProtect.getLabel(e.getEntity()), "");
		plugin.dbRunnable.add(entry);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerUnleashEntityEvent(PlayerUnleashEntityEvent e) {
		if (e.isCancelled()) {
			return;
		}
		if (!(e.getEntity() instanceof LivingEntity)) {
			return;
		}
		LivingEntity entity = (LivingEntity) e.getEntity();
		if (!entity.isLeashed()) {
			return;
		}

		boolean tether = entity.getLeashHolder().getType() == EntityType.LEASH_HITCH;

		DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.LEASH, false,
				e.getEntity().getLocation(), AuxProtect.getLabel(e.getEntity()), tether ? "was tethered" : "");
		plugin.dbRunnable.add(entry);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerRespawnEvent(PlayerRespawnEvent e) {
		plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.RESPAWN, false,
				e.getRespawnLocation(), "", ""));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCommand(PlayerCommandPreprocessEvent e) {
		plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.COMMAND, false,
				e.getPlayer().getLocation(), e.getMessage(), ""));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDropEvent(PlayerDropItemEvent e) {
		if (e.isCancelled()) {
			return;
		}
		if (isChartMap(e.getItemDrop().getItemStack())) {
			e.getItemDrop().remove();
			return;
		}

		if (InvSerialization.isCustom(e.getItemDrop().getItemStack())) {
			plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.DROP, false,
					e.getPlayer().getLocation(), e.getItemDrop().getItemStack().getType().toString().toLowerCase(),
					InvSerialization.toBase64(e.getItemDrop().getItemStack())));
		} else {
			plugin.dbRunnable.addPickup(new PickupEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.DROP, false,
					e.getPlayer().getLocation(), e.getItemDrop().getItemStack().getType().toString().toLowerCase(),
					e.getItemDrop().getItemStack().getAmount()));
		}

	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPickupEvent(EntityPickupItemEvent e) {
		if (e.isCancelled()) {
			return;
		}
		if (e.getEntity() instanceof Player) {
			Player player = (Player) e.getEntity();
			if (isChartMap(e.getItem().getItemStack()) && !MyPermission.LOOKUP_MONEY.hasPermission(player)) {
				e.setCancelled(true);
				e.getItem().remove();
			}

			if (InvSerialization.isCustom(e.getItem().getItemStack())) {
				plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(player), EntryAction.PICKUP, false,
						e.getItem().getLocation(), e.getItem().getItemStack().getType().toString().toLowerCase(),
						InvSerialization.toBase64(e.getItem().getItemStack())));
			} else {
				plugin.dbRunnable.addPickup(new PickupEntry(AuxProtect.getLabel(player), EntryAction.PICKUP, false,
						e.getItem().getLocation(), e.getItem().getItemStack().getType().toString().toLowerCase(),
						e.getItem().getItemStack().getAmount()));
			}
		}
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
