package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.adapters.sender.SpigotSenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.PerPlayerManager;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;

public class VeinListener implements Listener {

    private static final ArrayList<Material> NETHER_CHECK = new ArrayList<>();// TODO config
    private static final int NETHER_MAXY = 128;

    private static final ArrayList<Material> OVERWORLD_CHECK = new ArrayList<>();// TODO config
    private static final int OVERWORLD_MAXY = 16;

    private static final int NON_ORE_RADIUS = 10;
    private static final int ORE_RADIUS = 5;
    private static final int NON_ORE_THRESHOLD = 2;
    final PerPlayerManager<BlockHistory> blockhistory = new PerPlayerManager<>(BlockHistory::new);
    private final AuxProtectSpigot plugin;

    public VeinListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;

        // Maybe overkill, just preventing memory leaks
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            synchronized (blockhistory) {
                blockhistory.entrySet().removeIf(entry -> entry.getValue().timeSinceUsed() > 300000L);
            }
        }, 20L * 60L * 5L, 20L * 60L * 5L);

        NETHER_CHECK.add(Material.ANCIENT_DEBRIS);

        OVERWORLD_CHECK.add(Material.DIAMOND_ORE);
        OVERWORLD_CHECK.add(Material.SPAWNER);
        if (plugin.getCompatabilityVersion() >= 17) {
            OVERWORLD_CHECK.add(Material.DEEPSLATE_DIAMOND_ORE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!EntryAction.VEIN.isEnabled()) {
            return;
        }
        if (APPermission.XRAY_EXEMPT.hasPermission(new SpigotSenderAdapter(plugin, e.getPlayer()))) {
            return;
        }

        boolean ore;
        switch (e.getBlock().getWorld().getEnvironment()) {
            case NETHER:
                if (e.getBlock().getY() > NETHER_MAXY) {
                    return;
                }
                ore = NETHER_CHECK.contains(e.getBlock().getType());
                break;
            case NORMAL:
                if (e.getBlock().getY() > OVERWORLD_MAXY && e.getBlock().getType() != Material.SPAWNER) {
                    return;
                }
                ore = OVERWORLD_CHECK.contains(e.getBlock().getType());
                break;
            default:
                return;
        }
        BlockHistory hist = blockhistory.get(e.getPlayer());

        boolean anykindofore = e.getBlock().getType().toString().endsWith("_ORE");

        // Any kind of ore that is not being checked by the checker for the given world.
        // This completely ignores redstone ore and such so when players dig through
        // those
        // ores and happen to find valuables, it is not logged.
        if (!ore && anykindofore) {
            // TODO test
            return;
        }

        if (!ore) {
            // Make sure we aren't actively logging a vein here.
            boolean nearbyOres = false;
            for (Block b : hist.oreBlock) {
                if (comparedistance(e.getBlock(), b, ORE_RADIUS)) {
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
        int nearbyNonOres = 0;
        for (Block b : hist.nonOreBlock) {
            if (comparedistance(e.getBlock(), b, NON_ORE_RADIUS)) {
                if (nearbyNonOres++ >= NON_ORE_THRESHOLD) {
                    break;
                }
            }
        }

        hist.addBlock(e.getBlock(), true);
        final XrayEntry entry = new XrayEntry(AuxProtectSpigot.getLabel(e.getPlayer()), e.getBlock().getLocation(),
                AuxProtectSpigot.getLabel(e.getBlock().getType()));
        if (nearbyNonOres < NON_ORE_THRESHOLD) {
            entry.setRating((short) -2, null);
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

    public static class BlockHistory {
        public final long birth = System.currentTimeMillis();
        public final Block[] nonOreBlock = new Block[20];
        public final Block[] oreBlock = new Block[20];
        private int nonOreBlockIndex = 0;
        private int oreBlockIndex = 0;
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
}
