package dev.heliosares.auxprotect.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.UUID;

public class FakePlayer {
    private final int id;
    private final UUID uuid;
    private final String name;
    private final ProtocolManager protocol;
    private final Player audience;

    private Location loc;

    private long lastMoved;

    public FakePlayer(String name, ProtocolManager protocol, Player audience) {
        this.uuid = UUID.randomUUID();
        this.name = name;
        this.protocol = protocol;
        this.audience = audience;
        this.id = uuid.hashCode();
    }

    public void spawn(Location loc_) {
        this.loc = loc_;

        // Sends player info, creates the player

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
        packet.getPlayerInfoActions().modify(0, set -> {
            set.add(EnumWrappers.PlayerInfoAction.ADD_PLAYER);
            return set;
        });
        packet.getPlayerInfoDataLists().write(1, Collections.singletonList(
                new PlayerInfoData(new WrappedGameProfile(uuid, name), 0, EnumWrappers.NativeGameMode.SURVIVAL, WrappedChatComponent.fromLegacyText(name))
        ));
        protocol.sendServerPacket(audience, packet);


        // Set initial location

        packet = new PacketContainer(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        packet.getIntegers().write(0, id);
        packet.getUUIDs().write(0, uuid);
        packet.getDoubles().write(0, loc.getX());
        packet.getDoubles().write(1, loc.getY());
        packet.getDoubles().write(2, loc.getZ());
        packet.getBytes().write(0, (byte) (loc.getYaw() * 256f / 360f));
        packet.getBytes().write(1, (byte) (loc.getPitch() * 256f / 360f));
        protocol.sendServerPacket(audience, packet);
    }

    public void setLocation(Location loc) {

        // Move entity

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
        packet.getIntegers().write(0, id);
        packet.getShorts().write(0, (short) ((loc.getX() - this.loc.getX()) * 4096));
        packet.getShorts().write(1, (short) ((loc.getY() - this.loc.getY()) * 4096));
        packet.getShorts().write(2, (short) ((loc.getZ() - this.loc.getZ()) * 4096));
        packet.getBytes().write(0, (byte) (loc.getYaw() * 256f / 360f));
        packet.getBytes().write(1, (byte) (loc.getPitch() * 256f / 360f));
        protocol.sendServerPacket(audience, packet);

        // Update head

        packet = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        packet.getIntegers().write(0, id);
        packet.getBytes().write(0, (byte) (loc.getYaw() * 256f / 360f));
        protocol.sendServerPacket(audience, packet);

        lastMoved = System.currentTimeMillis();
        this.loc = loc;
    }


    public void remove() {

        // Removes player info

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        packet.getUUIDLists().modify(0, list -> {
            list.add(uuid);
            return list;
        });
        protocol.sendServerPacket(audience, packet);

        // Removes player entity itself

        packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        packet.getIntLists().modify(0, list -> {
            list.add(id);
            return list;
        });
        protocol.sendServerPacket(audience, packet);
    }

    public long getLastMoved() {
        return lastMoved;
    }
}
