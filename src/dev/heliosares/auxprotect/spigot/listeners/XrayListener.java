package dev.heliosares.auxprotect.spigot.listeners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitRunnable;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.PerPlayerManager;

public class XrayListener implements Listener {

	private AuxProtectSpigot plugin;

	public XrayListener(AuxProtectSpigot plugin) {
		this.plugin = plugin;

		// Maybe overkill, just preventing memory leaks
		new BukkitRunnable() {
			@Override
			public void run() {
				Iterator<Entry<UUID, BlockHistory>> it = blockhistory.entrySet().iterator();
				while (it.hasNext()) {
					Entry<UUID, BlockHistory> entry = it.next();
					if (entry.getValue().timeSinceUsed() > 300000L) {
						it.remove();
					}
				}
			}
		}.runTaskTimerAsynchronously(plugin, 20 * 60 * 5, 20 * 60 * 5);
	}

	PerPlayerManager<BlockHistory> blockhistory = new PerPlayerManager<>(() -> new BlockHistory());

	public static class BlockHistory {
		public Block[] nonOreBlock = new Block[20];
		private int nonOreBlockIndex = 0;
		public Block[] oreBlock = new Block[20];
		private int oreBlockIndex = 0;
		public final long birth = System.currentTimeMillis();

		private long lastused;
		{
			touch();
		}

		public void addBlock(Block b, boolean ore) {
			lastused = System.currentTimeMillis();
			if (ore) {
				oreBlockIndex++;
				if (oreBlockIndex >= oreBlock.length) {
					oreBlockIndex = 0;
				}
				oreBlock[oreBlockIndex] = b;
			} else {
				nonOreBlockIndex++;
				if (nonOreBlockIndex >= nonOreBlock.length) {
					nonOreBlockIndex = 0;
				}
				nonOreBlock[nonOreBlockIndex] = b;
			}
		}

		public void touch() {
			lastused = System.currentTimeMillis();
		}

		public long timeSinceUsed() {
			return System.currentTimeMillis() - lastused;
		}
	}

	private static final ArrayList<Material> NETHER_CHECK = new ArrayList<Material>(
			Arrays.asList(Material.ANCIENT_DEBRIS));// TODO config
	private static final int NETHER_MAXY = 128;

	private static final ArrayList<Material> OVERWORLD_CHECK = new ArrayList<Material>(
			Arrays.asList(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE));// TODO config
	private static final int OVERWORLD_MAXY = 16;

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent e) {
		if (!EntryAction.VEIN.isEnabled()) {
			return;
		}
		if (APPermission.XRAY_EXEMPT.hasPermission(e.getPlayer())) {
			return;
		}

		boolean ore = false;
		switch (e.getBlock().getWorld().getEnvironment()) {
		case NETHER:
			if (e.getBlock().getY() > NETHER_MAXY) {
				return;
			}
			ore = NETHER_CHECK.contains(e.getBlock().getType());
			break;
		case NORMAL:
			if (e.getBlock().getY() > OVERWORLD_MAXY) {
				return;
			}
			ore = OVERWORLD_CHECK.contains(e.getBlock().getType());
			break;
		default:
			return;
		}
		BlockHistory hist = blockhistory.get(e.getPlayer());

		if (!ore) {
			// Make sure we aren't actively logging a vein here.
			boolean nearbyOres = false;
			for (Block b : hist.oreBlock) {
				if (comparedistance(e.getBlock(), b, 5)) {
					nearbyOres = true;
					break;
				}
			}
			if (!nearbyOres) {
				hist.addBlock(e.getBlock(), false);
			}
			return;
		}

		// Don't log if a non-ore block hasn't been broken within 20 blocks
		boolean nearbyNonOres = false;
		for (Block b : hist.nonOreBlock) {
			if (comparedistance(e.getBlock(), b, 10)) {
				nearbyNonOres = true;
				break;
			}
		}

		hist.addBlock(e.getBlock(), true);
		final XrayEntry entry = new XrayEntry(AuxProtectSpigot.getLabel(e.getPlayer()), e.getBlock().getLocation(),
				AuxProtectSpigot.getLabel(e.getBlock().getType()));
		if (!nearbyNonOres) {
			entry.setRating((short) -2);
		}
		new BukkitRunnable() {
			@Override
			public void run() {
				plugin.add(entry);
			}
		}.runTaskAsynchronously(plugin);
	}

	/**
	 * @return true if the blocks are closer than the distance provided
	 */
	private boolean comparedistance(@Nullable Block a, @Nullable Block b, double distance) {
		if (a == null || b == null) {
			return false;
		}
		if (!a.getWorld().equals(b.getWorld())) {
			return false;
		}
		// Maybe this is faster than Location#distanceSquared, definitely not slower.
		double r = sq(a.getX() - b.getX()) + sq(a.getY() - b.getY()) + sq(a.getZ() - b.getZ());
		return r < sq(distance);
	}

	private double sq(double d) {
		return d * d;
	}
}
