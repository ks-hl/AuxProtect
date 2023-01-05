package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.DbEntryBukkit;
import dev.heliosares.auxprotect.database.EntryAction;
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
    private final List<PosPoint> points;
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
        realReferenceTime = System.currentTimeMillis();
        points = getLocations(plugin, entries, startTime);
        long min = points.stream().map(PosPoint::time).min(Long::compare).orElse(0L);
        this.startTime = Math.max(min - 250, startTime);
    }

    public record PosPoint(long time, String name, int uid, Location location, boolean inc) {
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
                LivingEntity actor = actors.get(point.name());
                if (actor == null || actor.isDead()) {
                    Location loc = point.location;
                    assert loc.getWorld() != null;
                    actor = (LivingEntity) loc.getWorld().spawnEntity(point.location(), EntityType.VILLAGER);
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

    public static List<PosPoint> getLocations(IAuxProtect plugin, List<DbEntry> entries, long startTime) throws SQLException, IOException {
        if (plugin.getPlatform() != PlatformType.SPIGOT) throw new UnsupportedOperationException();
        long min = Long.MAX_VALUE;
        Map<String, DbEntry> lastEntries = new HashMap<>();
        entries.sort(Comparator.comparingLong(DbEntry::getTime));
        List<PosPoint> points = new ArrayList<>();
        for (DbEntry entry : entries) {
            DbEntry lastEntry = lastEntries.get(entry.getUser());
            if (lastEntry != null && entry.getBlob() != null) {
                List<PosEncoder.DecodedPositionIncrement> decoded = PosEncoder.decode(entry.getBlob());
                Location lastLoc = DbEntryBukkit.getLocation(lastEntry);
                final long incrementBy = (entry.getTime() - lastEntry.getTime()) / (decoded.size() + 1);
                for (int i = 0; i < decoded.size(); i++) {
                    PosEncoder.DecodedPositionIncrement inc = decoded.get(i);
                    long time = lastEntry.getTime() + (i + 1) * incrementBy;
                    if (time < startTime) continue;
                    org.bukkit.util.Vector add = new org.bukkit.util.Vector(inc.x(), inc.y(), inc.z());
                    Location incLoc = lastLoc.clone().add(add);
                    if (inc.hasPitch()) incLoc.setPitch(inc.pitch());
                    if (inc.hasYaw()) incLoc.setYaw(inc.yaw());
                    lastLoc = incLoc;
                    PosPoint point = new PosPoint(time, entry.getUser(), entry.getUid(), incLoc, true);
                    plugin.debug("Adding point " + point, 3);
                    points.add(point);
                    if (time < min) min = time;
                }
            }
            Location entryLoc = DbEntryBukkit.getLocation(entry);
            entryLoc.setYaw(entry.yaw);
            entryLoc.setPitch(entry.pitch);
            PosPoint point = new PosPoint(entry.getTime(), entry.getUser(), entry.getUid(), entryLoc, false);
            plugin.debug("Adding point " + point, 3);
            points.add(point);
            if (entry.getTime() < min) min = entry.getTime();
            lastEntries.put(entry.getUser(), entry);
        }
        points.sort(Comparator.comparingLong(a -> a.time));
        return points;
    }

    public static class PosEntry extends DbEntry {
        public PosEntry(long time, int uid, Location location) {
            super(time, uid, EntryAction.POS, false, Objects.requireNonNull(location.getWorld()).getName(),
                    (int) Math.round(location.getX()), (int) Math.round(location.getY()), (int) Math.round(location.getZ()),
                    Math.round(location.getPitch()), Math.round(location.getYaw()), "", -1, "");
        }
    }
}
