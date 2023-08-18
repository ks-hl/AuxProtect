package dev.heliosares.auxprotect.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.google.common.collect.Lists;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

public class FakePlayer {
    private final int id;
    private final UUID uuid;
    private final String name;
    private final ProtocolManager protocol;
    private final Player audience;
    private Location loc;
    private long lastMoved;
    private PosEncoder.Posture currentPosture = PosEncoder.Posture.STANDING;

    public FakePlayer(String name, ProtocolManager protocol, Player audience) {
        this.uuid = generateNPCUUID();
        this.name = name;
        this.protocol = protocol;
        this.audience = audience;
        this.id = uuid.hashCode();
    }

    public static UUID generateNPCUUID() {
        UUID uuid = UUID.randomUUID();
        return UUID.fromString(uuid.toString().substring(0, 15) + '2' + uuid.toString().substring(16));
    }

    public static Skin getSkin(UUID uuid) throws ParseException, IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Response Code: " + response.statusCode() + ", " + response.body());
        }

        JSONObject json2 = (JSONObject) new JSONParser().parse(response.body());
        Object props = ((JSONArray) json2.get("properties")).get(0);
        JSONObject propsObj = (JSONObject) props;
        return new Skin(uuid, (String) propsObj.get("value"), (String) propsObj.get("signature"));
    }

    public void spawn(Location loc_, @Nullable Skin skin) {
        this.loc = loc_;
        // Sends player info, creates the player

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
        packet.getPlayerInfoActions().modify(0, set -> {
            set.add(EnumWrappers.PlayerInfoAction.ADD_PLAYER);
            set.add(EnumWrappers.PlayerInfoAction.UPDATE_LISTED);
            return set;
        });
        WrappedGameProfile profile = new WrappedGameProfile(uuid, name);
        if (skin != null) profile.getProperties().put("textures", skin.wrap());
        // TODO fake player still being added to list
        packet.getPlayerInfoDataLists().write(1, List.of(
                new PlayerInfoData(uuid, 0, false, EnumWrappers.NativeGameMode.SURVIVAL, profile, WrappedChatComponent.fromLegacyText(name), (WrappedRemoteChatSessionData) null)
        ));
        protocol.sendServerPacket(audience, packet);


        // Set initial location

        packet = new PacketContainer(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        setIdInPacket(packet);
        packet.getUUIDs().write(0, uuid);
        packet.getDoubles().write(0, loc.getX());
        packet.getDoubles().write(1, loc.getY());
        packet.getDoubles().write(2, loc.getZ());
        packet.getBytes().write(0, (byte) (loc.getYaw() * 256f / 360f));
        packet.getBytes().write(1, (byte) (loc.getPitch() * 256f / 360f));
        protocol.sendServerPacket(audience, packet);
    }

    public void setLocation(Location loc, boolean onGround) {
        // Move entity

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
        setIdInPacket(packet);
        packet.getShorts().write(0, (short) ((loc.getX() - this.loc.getX()) * 4096));
        packet.getShorts().write(1, (short) ((loc.getY() - this.loc.getY()) * 4096));
        packet.getShorts().write(2, (short) ((loc.getZ() - this.loc.getZ()) * 4096));
        packet.getBytes().write(0, (byte) (loc.getYaw() * 256f / 360f));
        packet.getBytes().write(1, (byte) (loc.getPitch() * 256f / 360f));
        packet.getBooleans().write(0, onGround);

        protocol.sendServerPacket(audience, packet);

        // Update head

        packet = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        setIdInPacket(packet);
        packet.getBytes().write(0, (byte) (loc.getYaw() * 256f / 360f));
        protocol.sendServerPacket(audience, packet);

        lastMoved = System.currentTimeMillis();
        this.loc = loc;
    }

    public void setPosture(PosEncoder.Posture posture) {
        if (currentPosture == posture) return;
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);

        setIdInPacket(packet);

        final List<WrappedDataValue> wrappedDataValueList = Lists.newArrayList();
        wrappedDataValueList.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), (byte) switch (posture) {
            case STANDING, SITTING, SLEEPING -> 0;
            case SNEAKING -> 0x02;
            case SWIMMING, CRAWLING -> 0x10;
            case GLIDING -> 0x80;
        }));
        wrappedDataValueList.add(new WrappedDataValue(6, WrappedDataWatcher.Registry.get(EnumWrappers.getEntityPoseClass()), switch (posture) {
            case STANDING -> EnumWrappers.EntityPose.STANDING;
            case SNEAKING -> EnumWrappers.EntityPose.CROUCHING;
            case SWIMMING, CRAWLING -> EnumWrappers.EntityPose.SWIMMING;
            case GLIDING -> EnumWrappers.EntityPose.FALL_FLYING;
            case SITTING -> EnumWrappers.EntityPose.SITTING;
            case SLEEPING -> EnumWrappers.EntityPose.SLEEPING;
        }));
        packet.getDataValueCollectionModifier().write(0, wrappedDataValueList);

        protocol.sendServerPacket(audience, packet);

        if (posture == PosEncoder.Posture.GLIDING) {
            setEquipment(EnumWrappers.ItemSlot.CHEST, new ItemStack(Material.ELYTRA));
        } else if (currentPosture == PosEncoder.Posture.GLIDING) {
            setEquipment(EnumWrappers.ItemSlot.CHEST, new ItemStack(Material.AIR));
        }

        currentPosture = posture;
    }

    public void setEquipment(EnumWrappers.ItemSlot slot, ItemStack item) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);

        setIdInPacket(packet);

        packet.getSlotStackPairLists().write(0, List.of(new Pair<>(slot, item)));

        protocol.sendServerPacket(audience, packet);
    }

    public void swingArm() {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ANIMATION);

        setIdInPacket(packet);
        packet.getIntegers().write(1, 0);

        protocol.sendServerPacket(audience, packet);
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

    private void setIdInPacket(PacketContainer packet) {
        packet.getIntegers().write(0, id);
    }

    public long getLastMoved() {
        return lastMoved;
    }

    public record Skin(UUID uuid, String skin, String signature) {
        public WrappedSignedProperty wrap() {
            return new WrappedSignedProperty("textures", skin, signature);
        }
    }
}
