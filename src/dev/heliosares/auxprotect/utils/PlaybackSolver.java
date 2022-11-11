package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.database.DbEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class PlaybackSolver extends BukkitRunnable {
    private static final Map<UUID, PlaybackSolver> instances = new HashMap<>();

    private final IAuxProtect plugin;
    private final SenderAdapter sender;
    private final List<DbEntry> entries;
    private final List<PosPoint> points = new ArrayList<>();
    private final long startTime;
    private final long realReferenceTime;

    private final Map<String, LivingEntity> actors = new HashMap<>();

    public PlaybackSolver(IAuxProtect plugin, SenderAdapter sender, List<DbEntry> entries, long startTime) throws SQLException, IOException {
        if (plugin.getPlatform() != PlatformType.SPIGOT) throw new UnsupportedOperationException();
        PlaybackSolver instance = instances.get(sender.getUniqueId());
        if (instance != null) {
            instance.close();
        }
        instances.put(sender.getUniqueId(), this);
        this.plugin = plugin;
        this.sender = sender;
        this.entries = entries;
        realReferenceTime = System.currentTimeMillis();
        long min = Long.MAX_VALUE;
        Location lastEntryLoc = null;
        long lastEntryTime = 0;
        entries.sort(Comparator.comparingLong(DbEntry::getTime));
        for (DbEntry entry : entries) {
            Location entryLoc = new Location(Bukkit.getWorld(entry.world), entry.x, entry.y, entry.z, entry.yaw, entry.pitch);
            if (lastEntryLoc != null && entry.getBlob() != null) {
                List<PosEncoder.DecodedPositionIncrement> decoded = PosEncoder.decode(entry.getBlob());
                Location lastLoc = lastEntryLoc;
                final long incrementBy = (entry.getTime() - lastEntryTime) / (decoded.size() + 1);
                for (int i = 0; i < decoded.size(); i++) {
                    PosEncoder.DecodedPositionIncrement inc = decoded.get(i);
                    long time = lastEntryTime + (i + 1) * incrementBy;
                    if (time < startTime) continue;
                    org.bukkit.util.Vector add = new org.bukkit.util.Vector(inc.x(), inc.y(), inc.z());
                    plugin.info("add " + add);
                    Location incLoc = lastLoc.clone().add(add);
                    incLoc.setPitch(inc.pitch());
                    incLoc.setYaw(inc.yaw());
                    lastLoc = incLoc;
                    points.add(new PosPoint(time, entry.getUser(), incLoc, true));
                    if (time < min) min = time;
                }
            }
            points.add(new PosPoint(entry.getTime(), entry.getUser(), entryLoc, false));
            if (entry.getTime() < min) min = entry.getTime();
            lastEntryLoc = entryLoc;
            lastEntryTime = entry.getTime();
        }
        points.sort(Comparator.comparingLong(a -> a.time));
        this.startTime = Math.max(min - 250, startTime);
    }

    record PosPoint(long time, String name, Location location, boolean inc) {
    }

    @Override
    public void run() {
        if (closed || isCancelled()) {
            actors.values().forEach(Entity::remove);
            actors.clear();
            cancel();
            return;
        }
        final long timeNow = System.currentTimeMillis() - realReferenceTime + startTime;

        for (Iterator<PosPoint> it = points.iterator(); it.hasNext(); ) {
            PosPoint point = it.next();
            if (timeNow > point.time()) {
                plugin.info((point.inc ? "inc: " : "main: ") + point.time + " (" + (timeNow - point.time) + "rem): " + point.location.toVector().toString());
                LivingEntity actor = actors.get(point.name());
                if (actor == null || actor.isDead()) {
                    actor = (LivingEntity) point.location().getWorld().spawnEntity(point.location(), EntityType.VILLAGER);
                    actor.setAI(false);
                    actor.setInvulnerable(true);
                    actor.setCustomName(point.name());
                    actor.setCustomNameVisible(true);
                }
                actors.put(point.name(), actor);
                actor.teleport(point.location());
                actor.setHealth(20);
                it.remove();
            } else break;
        }
        if (points.isEmpty()) close();
    }

    public void close() {
        if (closed) return;
        closed = true;
    }

    private boolean closed;
}
