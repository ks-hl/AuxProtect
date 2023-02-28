package dev.heliosares.auxprotect.utils;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.DbEntryBukkit;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.*;

public class PlaybackSolver extends BukkitRunnable {
    private static final Map<UUID, PlaybackSolver> instances = new HashMap<>();
    private final List<PosPoint> points;
    private final long startTime;
    private final long realReferenceTime;

    private final Map<String, FakePlayer> actors = new HashMap<>();
    private boolean closed;

    private final ProtocolManager protocol;
    private final Player audience;

    public PlaybackSolver(IAuxProtect plugin, SenderAdapter sender, List<DbEntry> entries, long startTime) throws SQLException {
        if (plugin.getPlatform() != PlatformType.SPIGOT) throw new UnsupportedOperationException();
        PlaybackSolver instance = instances.get(sender.getUniqueId());
        if (instance != null) {
            instance.close();
        }
        this.audience = (Player) sender.getSender();
        instances.put(sender.getUniqueId(), this);
        realReferenceTime = System.currentTimeMillis();
        points = getLocations(plugin, entries, startTime);
        long min = points.stream().map(PosPoint::time).min(Long::compare).orElse(0L);
        this.startTime = Math.max(min - 250, startTime);
        this.protocol = ProtocolLibrary.getProtocolManager();

        runTaskTimer((AuxProtectSpigot) plugin, 1, 1);
    }

    public static List<PosPoint> getLocations(IAuxProtect plugin, List<DbEntry> entries, long startTime) throws SQLException {
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
                    PosPoint point = new PosPoint(time, UUID.fromString(entry.getUserUUID().substring(1)), entry.getUser(), entry.getUid(), incLoc, true);
                    plugin.debug("Adding point " + point, 3);
                    points.add(point);
                    if (time < min) min = time;
                }
            }
            Location entryLoc = DbEntryBukkit.getLocation(entry);
            entryLoc.setYaw(entry.getYaw());
            entryLoc.setPitch(entry.getPitch());
            PosPoint point = new PosPoint(entry.getTime(), UUID.fromString(entry.getUserUUID().substring(1)), entry.getUser(), entry.getUid(), entryLoc, false);
            plugin.debug("Adding point " + point, 3);
            points.add(point);
            if (entry.getTime() < min) min = entry.getTime();
            lastEntries.put(entry.getUser(), entry);
        }
        points.sort(Comparator.comparingLong(a -> a.time));
        return points;
    }

    @Override
    public void run() {
        if (closed || isCancelled()) {
            actors.values().forEach(FakePlayer::remove);
            actors.clear();
            cancel();
            return;
        }
        final long timeNow = System.currentTimeMillis() - realReferenceTime + startTime;


        for (Iterator<PosPoint> it = points.iterator(); it.hasNext(); ) {
            PosPoint point = it.next();
            if (timeNow > point.time()) {
                FakePlayer actor = actors.get(point.name());
                Location loc = point.location.clone();
                assert loc.getWorld() != null;

                if (actor == null) {
                    actor = new FakePlayer(point.name(), protocol, audience);
                    actor.spawn(point.location());
                }
                actors.put(point.name(), actor);
                actor.setLocation(loc);

                it.remove();
            } else break;
        }
        Iterator<FakePlayer> it = actors.values().iterator();
        while (it.hasNext()) {
            FakePlayer pl = it.next();
            if (System.currentTimeMillis() - pl.getLastMoved() > 1000) {
                pl.remove();
                it.remove();
            }
        }
        if (points.isEmpty()) close();
    }


    public void close() {
        if (closed) return;
        closed = true;
    }

    public record PosPoint(long time, UUID uuid, String name, int uid, Location location, boolean inc) {
    }

    public static class PosEntry extends DbEntry {
        public PosEntry(long time, int uid, Location location) {
            super(time, uid, EntryAction.POS, false, Objects.requireNonNull(location.getWorld()).getName(),
                    (int) Math.round(location.getX()), (int) Math.round(location.getY()), (int) Math.round(location.getZ()),
                    Math.round(location.getPitch()), Math.round(location.getYaw()), "", -1, "");
        }
    }
}
