package dev.heliosares.auxprotect.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import jakarta.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class PosEncoder {

    /**
     * Coverts a change in position to an encoded byte array of the incremental difference
     *
     * @return The incremental byte array representing:<br>Bit mask indicating the presence/length of values below<br>0-2 bytes representing dX<br>0-2 bytes representing dY<br>0-2 bytes representing dZ<br>1 byte representing pitch<br>1 byte representing yaw
     */
    public static byte[] encode(Location from, Location to, Posture posture, @Nullable Posture lastPosture) {
        return encode(
                to.getX() - from.getX(),
                to.getY() - from.getY(),
                to.getZ() - from.getZ(),
                to.getPitch() != from.getPitch() || to.getYaw() != from.getYaw(), to.getPitch(), to.getYaw(),
                posture.equals(lastPosture) ? null : posture);
    }

    private static byte[] encode(double diffX_, double diffY_, double diffZ_, boolean doLook, float pitch_, float yaw_, @Nullable Posture posture) {
        IncrementalByte diffX = simplify(diffX_);
        IncrementalByte diffY = simplify(diffY_);
        IncrementalByte diffZ = simplify(diffZ_);
        byte pitch = (byte) Math.round(pitch_);
        byte yaw = (byte) Math.round((yaw_ / 180.0) * 127);

        // bitMask indicates the presence of various values
        // 0-1 represent number of bytes (0-2) representing X. Value of 0b11 indicates fine
        // 2-3 represent number of bytes (0-2) representing Y. Value of 0b11 indicates fine
        // 4-5 represent number of bytes (0-2) representing Z. Value of 0b11 indicates fine
        // 6 represents whether there is look (pitch/yaw)
        // 7 represents whether there is posture data (sneak, gliding, etc.)
        byte bitMask = 0;
        int len = 1 + diffX.array.length + diffY.array.length + diffZ.array.length;
        bitMask |= (byte) diffX.getBytesNeeded();
        bitMask |= (byte) (diffY.getBytesNeeded() << 2);
        bitMask |= (byte) (diffZ.getBytesNeeded() << 4);

        if (doLook) {
            bitMask = setBit(bitMask, 6, true);
            len += 2;
        }
        if (posture != null) {
            bitMask = setBit(bitMask, 7, true);
            len++;
        }


        ByteBuffer bb = ByteBuffer.allocate(len);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(bitMask);
        bb.put(diffX.array);
        bb.put(diffY.array);
        bb.put(diffZ.array);
        if (doLook) {
            bb.put(pitch);
            bb.put(yaw);
        }
        if (posture != null) {
            bb.put(posture.getID());
        }

        return bb.array();
    }

    /**
     * Decodes all components with an incremental byte array according to {@link PosEncoder#decodeSingle(byte[], int)}
     *
     * @param bytes The incremental byte array
     * @return A list of records representing the presence and value of each component of position.
     */
    public static List<PositionIncrement> decode(byte[] bytes) {
        List<PositionIncrement> out = new ArrayList<>();
        for (int i = 0, safety = 0; i < bytes.length && safety < bytes.length; safety++) {
            PositionIncrement decoded = decodeSingle(bytes, i);
            i += decoded.bytes;
            out.add(decoded);
        }
        return out;
    }

    /**
     * Coverts a byte array, offset by a value, into a decoded position increment.
     *
     * @param bytes  The data
     * @param offset Where to start looking in the data
     * @return A record representing the presence and value of each component of position
     */
    private static PositionIncrement decodeSingle(byte[] bytes, int offset) {
        double[] out = new double[5];
        byte bitMask = bytes[offset];

        int index = 1;
        int xLen = bitMask & 0b11;
        if (xLen > 0) out[0] = toDouble(bytes, offset + index, xLen);
        if (xLen == 3) xLen = 1;
        index += xLen;

        int yLen = (bitMask >> 2) & 0b11;
        if (yLen > 0) out[1] = toDouble(bytes, offset + index, yLen);
        if (yLen == 3) yLen = 1;
        index += yLen;

        int zLen = (bitMask >> 4) & 0b11;
        if (zLen > 0) out[2] = toDouble(bytes, offset + index, zLen);
        if (zLen == 3) zLen = 1;
        index += zLen;


        boolean look = getBit(bitMask, 6);
        boolean hasPosture = getBit(bitMask, 7);

        if (look) {
            out[3] = bytes[offset + index++];
            out[4] = (double) bytes[offset + index++] / 127.0 * 180;
        }

        Posture posture = null;
        if (hasPosture) {
            posture = Posture.fromID(bytes[offset + index++]);
        }


        return new PositionIncrement(
                xLen > 0, out[0],
                yLen > 0, out[1],
                zLen > 0, out[2],
                look, (float) out[3], (float) out[4],
                hasPosture, posture,
                index
        );
    }

    /**
     * Decodes a byte sequence
     *
     * @param bytes   The data
     * @param index   The starting point
     * @param bitMask The mask indicating which values are present
     * @return The double retrieved from the byte array
     */
    private static double toDouble(byte[] bytes, int index, int bitMask) {
        if (bytes.length == 0) return 0;
        double sig;
        if (bitMask == 3) {
            bitMask = 1;
            sig = 100.0;
        } else sig = 10.0;
        if (bitMask == 0) return 0;
        if (bitMask == 1) return (double) bytes[index] / sig;
        return ((((int) bytes[index]) << 8) | (bytes[index + 1] & 0xff)) / sig;
    }

    /**
     * @param value The value to be reduced
     * @return A record containing a byte array of the value
     */
    private static IncrementalByte simplify(double value) {
        // Determines the resolution of this value. If the value is less than 1 in magnitude, it will be stored in hundredths, otherwise it will be stored in tenths
        boolean fine = Math.abs(value) < 1;
        value *= 10;
        if (fine) value *= 10;

        // Convert the value to a short, so it is 2 bytes max.
        short s = (short) (value = Math.round(value));

        // Ensures there was no overflow
        if (value > Short.MAX_VALUE) s = Short.MAX_VALUE;
        else if (value < Short.MIN_VALUE) s = Short.MIN_VALUE;

        // 0
        if (s == 0) return new IncrementalByte(new byte[0], false);

        byte lower = (byte) s;
        // If true, the value can be represented with only 1 byte.
        if (s == lower) return new IncrementalByte(new byte[]{lower}, fine);

        // The value needs 2 bytes to represent it.
        return new IncrementalByte(new byte[]{(byte) (s >> 8), lower}, false);
    }

    /**
     * @param array The data
     * @param fine  Whether the value is stored in hundredths or tenths. true indicates hundredths.
     */
    record IncrementalByte(byte[] array, boolean fine) {
        public int getBytesNeeded() {
            return fine ? 3 : array.length;
        }
    }

    public enum Posture {
        STANDING(0), SNEAKING(1), SWIMMING(2), GLIDING(3), SITTING(4), CRAWLING(5), SLEEPING(6);
        private final byte id;

        Posture(int id) {
            this.id = (byte) id;
        }

        public byte getID() {
            return id;
        }

        public static Posture fromPlayer(Player player) {
            if (player.isSwimming()) return SWIMMING;
            if (player.isGliding()) return GLIDING;
            if (player.isInsideVehicle()) return SITTING;
            if (player.isSleeping()) return SLEEPING;
            if (player.isSneaking() && !player.isFlying()) return SNEAKING;
            if (player.getBoundingBox().getHeight() < 1) return CRAWLING;
            return STANDING;
        }

        public static Posture fromID(byte id) {
            for (Posture posture : values()) if (posture.id == id) return posture;
            throw new IllegalArgumentException("Unknown posture: " + id);
        }
    }

    public record PositionIncrement(boolean hasX, double x, boolean hasY, double y, boolean hasZ, double z,
                                    boolean hasLook, float pitch, float yaw, boolean hasPosture, Posture posture,
                                    int bytes) {
        @Override
        public String toString() {
            return "X=" + (hasX ? x : "none") + " Y=" + (hasY ? y : "none") + " Z=" + (hasZ ? z : "none") + " Pitch=" + pitch + " Yaw=" + yaw + " Posture=" + posture;
        }
    }

    public static byte setBit(byte b, int index, boolean value) {
        if (index > 7 || index < 0) throw new IndexOutOfBoundsException(index + " is not a valid byte index.");
        byte val = (byte) (1 << index);
        if (value) b |= val;
        else b &= (byte) ~val;
        return b;
    }

    public static boolean getBit(byte b, int index) {
        return ((b >> index) & 1) == 1;
    }

    public static List<PosEncoder.PositionIncrement> decodeLegacy(byte[] bytes) {
        List<PosEncoder.PositionIncrement> out = new ArrayList<>();
        for (int offset = 0, safety = 0; offset < bytes.length && safety < bytes.length; safety++) {
            double[] doubles = new double[5];
            byte hdr = bytes[offset];

            boolean yaw = hdr < 0;
            if (yaw) hdr += (byte) 128;

            int xlen = hdr & 0b11;
            int ylen = (hdr >> 2) & 0b11;
            int zlen = (hdr >> 4) & 0b11;

            if (xlen > 0) doubles[0] = toDouble(bytes, offset + 1, xlen);
            if (xlen == 3) xlen = 1;
            if (ylen > 0) doubles[1] = toDouble(bytes, offset + 1 + xlen, ylen);
            if (ylen == 3) ylen = 1;
            if (zlen > 0) doubles[2] = toDouble(bytes, offset + 1 + xlen + ylen, zlen);
            if (zlen == 3) zlen = 1;

            boolean pitch = (hdr >> 6 & 1) == 1;
            if (pitch) doubles[3] = bytes[offset + 1 + xlen + ylen + zlen];
            if (yaw) doubles[4] = (double) bytes[offset + 1 + xlen + ylen + zlen + (pitch ? 1 : 0)] / 127.0 * 180;

            PosEncoder.PositionIncrement decod = new PosEncoder.PositionIncrement(
                    xlen > 0, doubles[0],
                    ylen > 0, doubles[1],
                    zlen > 0, doubles[2], pitch && yaw, (float) doubles[3], (float) doubles[4],
                    false, null,
                    1 + xlen + ylen + zlen + (yaw ? 1 : 0) + (pitch ? 1 : 0));
            offset += decod.bytes();
            out.add(decod);
        }
        return out;
    }
}
