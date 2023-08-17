package dev.heliosares.auxprotect.utils;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.DbEntryBukkit;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.parser.ParseException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class PlaybackSolver extends BukkitRunnable {
    private static final Map<UUID, PlaybackSolver> instances = new HashMap<>();
    private final List<PosPoint> points;
    private final long startTime;
    private final long realReferenceTime;

    private final Map<String, FakePlayer> actors = new HashMap<>();
    private final ProtocolManager protocol;
    private final Player audience;
    private final Map<UUID, FakePlayer.Skin> skins = new HashMap<>();
    private boolean closed;
    private final List<BlockAction> blockActions;

    private final Set<Location> modified = new HashSet<>();

    public PlaybackSolver(IAuxProtect plugin, SenderAdapter sender, List<DbEntry> entries, long startTime, @Nullable List<BlockAction> blockActions) throws SQLException, LookupException, BusyException {
        if (plugin.getPlatform() != PlatformType.SPIGOT) throw new UnsupportedOperationException();
        this.audience = (Player) sender.getSender();
        this.realReferenceTime = System.currentTimeMillis();
        this.points = getLocations(plugin, entries, startTime).stream()
                // Ensures the entries are in the same world
                .filter(point -> audience.getWorld().equals(point.location.getWorld()))
                // Ensures the entries are close enough to the player
                .filter(point -> audience.getLocation().distance(point.location) < 250)
                .collect(Collectors.toList());

        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
        } catch (ClassNotFoundException e) {
            throw new LookupException(Language.L.PROTOCOLLIB_NOT_LOADED);
        }

        this.protocol = ProtocolLibrary.getProtocolManager();

        if (points.size() == 0) {
            throw new LookupException(Language.L.COMMAND__LOOKUP__NORESULTS);
        }

        long min = points.stream().map(PosPoint::time).min(Long::compare).orElse(0L);
        long max = points.stream().map(PosPoint::time).max(Long::compare).orElse(System.currentTimeMillis());

        if (max - min > 5L * 60L * 1000L) {
            throw new LookupException(Language.L.COMMAND__LOOKUP__PLAYBACK__TOOLONG, "5 minutes");
        }

        Set<String> names = new HashSet<>();
        for (PosPoint point : points) {
            names.add(point.name());
            if (skins.containsKey(point.uuid)) continue;
            // Limit of 5 skins to cautiously avoid API rate limiting
            if (skins.size() >= 5) break;
            try {
                skins.put(point.uuid, FakePlayer.getSkin(point.uuid));
            } catch (ParseException | IOException | InterruptedException e) {
                plugin.warning("Failed to get skin for " + point.name());
                plugin.print(e);
            }
        }

        if (blockActions == null) {
            this.blockActions = new ArrayList<>();
        } else {
            this.blockActions = new ArrayList<>(blockActions.stream().filter(action -> names.contains(action.name())).filter(action -> action.time() >= min && action.time() <= max).toList());
            this.blockActions.sort(Comparator.comparingLong(a -> a.time));
        }
        this.startTime = Math.max(min - 250, startTime);

        close(sender.getUniqueId());
        synchronized (instances) {
            instances.put(sender.getUniqueId(), this);
        }

        sender.sendLang(Language.L.COMMAND__LOOKUP__PLAYBACK__STARTING);

        // Basically does a rollback preview
        for (int i = this.blockActions.size() - 1; i >= 0; i--) {
            BlockAction action = this.blockActions.get(i);
            modified.add(action.sendChange(audience, true));
        }

        runTaskTimer((AuxProtectSpigot) plugin, 1, 1);
    }

    public static void shutdown() {
        synchronized (instances) {
            instances.values().forEach(PlaybackSolver::close);
            instances.clear();
        }
    }

    public static void cleanup() {
        synchronized (instances) {
            instances.values().removeIf(PlaybackSolver::isClosed);
        }
    }

    public static void close(UUID uuid) {
        synchronized (instances) {
            PlaybackSolver instance = instances.get(uuid);
            if (instance != null) instance.close();
        }
    }


    public static List<PosPoint> getLocations(IAuxProtect plugin, List<DbEntry> entries, long startTime) throws SQLException, BusyException {
        if (plugin.getPlatform() != PlatformType.SPIGOT) throw new UnsupportedOperationException();
        Map<String, DbEntry> lastEntries = new HashMap<>();
        entries.sort(Comparator.comparingLong(DbEntry::getTime));
        List<PosPoint> points = new ArrayList<>();
        for (DbEntry entry : entries) {
            DbEntry lastEntry = lastEntries.get(entry.getUser());
            if (lastEntry != null && entry.getBlob() != null) {
                List<PosEncoder.PositionIncrement> decoded;
                if (entry.getTime() < plugin.getSqlManager().getLast(SQLManager.LastKeys.LEGACY_POSITIONS)) {
                    decoded = PosEncoder.decodeLegacy(entry.getBlob());
                } else {
                    decoded = PosEncoder.decode(entry.getBlob());
                }
                Location lastLoc = DbEntryBukkit.getLocation(lastEntry);
                final long incrementBy = (entry.getTime() - lastEntry.getTime()) / (decoded.size() + 1);
                for (int i = 0; i < decoded.size(); i++) {
                    PosEncoder.PositionIncrement inc = decoded.get(i);
                    long time = lastEntry.getTime() + (i + 1) * incrementBy;
                    if (time < startTime) continue;
                    org.bukkit.util.Vector add = new org.bukkit.util.Vector(inc.x(), inc.y(), inc.z());
                    Location incLoc = lastLoc.clone().add(add);
                    if (inc.hasLook()) {
                        incLoc.setPitch(inc.pitch());
                        incLoc.setYaw(inc.yaw());
                    }
                    lastLoc = incLoc.clone();
                    PosPoint point = new PosPoint(time, UUID.fromString(entry.getUserUUID().substring(1)), entry.getUser(), entry.getUid(), incLoc.clone(), true, inc.posture());
                    points.add(point);
                }
            }
            Location entryLoc = DbEntryBukkit.getLocation(entry);
            entryLoc.setYaw(entry.getYaw());
            entryLoc.setPitch(entry.getPitch());
            PosPoint point = new PosPoint(entry.getTime(), UUID.fromString(entry.getUserUUID().substring(1)), entry.getUser(), entry.getUid(), entryLoc, false, null);
            points.add(point);
            lastEntries.put(entry.getUser(), entry);
        }
        points.sort(Comparator.comparingLong(a -> a.time));
        return points;
    }

    @Override
    public void run() {
        if (closed) return;
        if (!audience.isOnline()) {
            close();
            return;
        }
        final long timeNow = System.currentTimeMillis() - realReferenceTime + startTime;

        audience.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(TimeUtil.format(timeNow, TimeUtil.entryTimeFormat) + "  " + ChatColor.COLOR_CHAR + "7-  " + TimeUtil.millisToString(System.currentTimeMillis() - timeNow) + " ago"));


        for (Iterator<PosPoint> it = points.iterator(); it.hasNext(); ) {
            if (closed) return;
            PosPoint point = it.next();
            if (timeNow > point.time()) {
                FakePlayer actor = actors.get(point.name());
                Location loc = point.location.clone();
                assert loc.getWorld() != null;

                if (actor == null) {
                    String name = "~" + point.name;
                    if (name.length() > 16) name = name.substring(0, 16);
                    actor = new FakePlayer(name, protocol, audience);
                    actor.spawn(point.location(), skins.get(point.uuid));
                }
                actors.put(point.name(), actor);
                actor.setLocation(loc, false);
                if (point.posture != null) actor.setPosture(point.posture);

                it.remove();
            } else break;
        }

        Set<FakePlayer> swing = new HashSet<>();
        for (Iterator<BlockAction> it = blockActions.iterator(); it.hasNext(); ) {
            if (closed) return;
            BlockAction action = it.next();
            if (timeNow > action.time()) {
                it.remove();
                FakePlayer actor = actors.get(action.name());
                if (actor != null) {
                    swing.add(actor);
                    action.sendChange(audience, false);
                }
            }
        }
        swing.forEach(FakePlayer::swingArm);



        for (Iterator<FakePlayer> it = actors.values().iterator(); it.hasNext(); ) {
            if (closed) return;
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
        cancel();
        closed = true;
        if (audience.isOnline()) {
            audience.sendMessage(Language.translate(Language.L.COMMAND__LOOKUP__PLAYBACK__STOPPED));
            audience.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(Language.translate(Language.L.COMMAND__LOOKUP__PLAYBACK__STOPPED)));
            actors.values().forEach(FakePlayer::remove);
            actors.clear();
            int countBlocks = 0;
            for (Location location : modified) {
                if (audience.getWorld().equals(location.getWorld()) && location.distance(audience.getLocation()) < 250) {
                    audience.sendBlockChange(location, location.getBlock().getBlockData());
                }
                if (++countBlocks > 10000) break;
            }
        }
        cleanup();
    }

    public boolean isClosed() {
        return closed;
    }

    public record PosPoint(long time, UUID uuid, String name, int uid, Location location, boolean inc,
                           @Nullable PosEncoder.Posture posture) {
    }

    public record BlockAction(long time, String name, int x, int y, int z, Material material, boolean place) {
        public Location sendChange(Player audience, boolean undo) {
            Material material = place() == undo ? Material.AIR : material();
            Location loc = new Location(audience.getWorld(), x(), y(), z());
            audience.sendBlockChange(loc, material.createBlockData());
            return loc;
        }
    }
}
