package dev.heliosares.auxprotect.utils;

import org.bukkit.Location;

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
    public static byte[] encode(Location from, Location to) {
        IncrementalByte diffX = simplify(to.getX() - from.getX());
        IncrementalByte diffY = simplify(to.getY() - from.getY());
        IncrementalByte diffZ = simplify(to.getZ() - from.getZ());
        byte pitch = (byte) to.getPitch();
        boolean doPitch = to.getPitch() != from.getPitch();
        byte yaw = (byte) ((to.getYaw() / 180.0) * 127);
        boolean doYaw = to.getYaw() != from.getYaw();

        // bitMask indicates the presence of various values
        // 0-1 represent number of bytes (0-2) representing X. Value of 0b11 indicates fine
        // 2-3 represent number of bytes (0-2) representing Y. Value of 0b11 indicates fine
        // 4-5 represent number of bytes (0-2) representing Z. Value of 0b11 indicates fine
        // 6 represents whether there is pitch
        // 7 represents whether there is yaw
        byte bitMask = 0;
        bitMask |= diffX.getBytesNeeded();
        bitMask |= diffY.getBytesNeeded() << 2;
        bitMask |= diffZ.getBytesNeeded() << 4;
        if (doPitch) bitMask |= 1 << 6;
        if (doYaw) bitMask -= 128;

        int len = 1 + diffX.array.length + diffY.array.length + diffZ.array.length;
        if (doPitch) len++;
        if (doYaw) len++;
        ByteBuffer bb = ByteBuffer.allocate(len);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(bitMask);
        bb.put(diffX.array);
        bb.put(diffY.array);
        bb.put(diffZ.array);
        if (doPitch) bb.put(pitch);
        if (doYaw) bb.put(yaw);

        return bb.array();
    }

    /**
     * Decodes all components with an incremental byte array according to {@link PosEncoder#decodeSingle(byte[], int)}
     *
     * @param bytes The incremental byte array
     * @return A list of records representing the presence and value of each component of position.
     */
    public static List<DecodedPositionIncrement> decode(byte[] bytes) {
        List<DecodedPositionIncrement> out = new ArrayList<>();
        for (int i = 0, safety = 0; i < bytes.length && safety < bytes.length; safety++) {
            DecodedPositionIncrement decoded = decodeSingle(bytes, i);
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
    public static DecodedPositionIncrement decodeSingle(byte[] bytes, int offset) {
        double[] out = new double[5];
        byte bitMask = bytes[offset];

        boolean yaw = bitMask < 0;
        if (yaw) bitMask += 128;

        int xLen = bitMask & 0b11;
        int yLen = (bitMask >> 2) & 0b11;
        int zLen = (bitMask >> 4) & 0b11;

        if (xLen > 0) out[0] = toDouble(bytes, offset + 1, xLen);
        if (xLen == 3) xLen = 1;
        if (yLen > 0) out[1] = toDouble(bytes, offset + 1 + xLen, yLen);
        if (yLen == 3) yLen = 1;
        if (zLen > 0) out[2] = toDouble(bytes, offset + 1 + xLen + yLen, zLen);
        if (zLen == 3) zLen = 1;

        boolean pitch = (bitMask >> 6 & 1) == 1;
        if (pitch) out[3] = bytes[offset + 1 + xLen + yLen + zLen];
        if (yaw) out[4] = (double) bytes[offset + 1 + xLen + yLen + zLen + (pitch ? 1 : 0)] / 127.0 * 180;

        return new DecodedPositionIncrement(
                xLen > 0, out[0],
                yLen > 0, out[1],
                zLen > 0, out[2],
                pitch, (float) out[3],
                yaw, (float) out[4],
                1 + xLen + yLen + zLen + (yaw ? 1 : 0) + (pitch ? 1 : 0)
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
        double sig;
        if (bitMask == 3) {
            bitMask = 1;
            sig = 100.0;
        } else sig = 10.0;
        if (bitMask == 0) return 0;
        if (bitMask == 1) return (double) bytes[index] / sig;
        return Math.round((((int) bytes[index]) << 8) | (bytes[index + 1] & 0xff)) / sig;
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
        short s = (short) Math.round(value);

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

    public record DecodedPositionIncrement(boolean hasX, double x, boolean hasY, double y, boolean hasZ, double z,
                                           boolean hasPitch, float pitch, boolean hasYaw, float yaw, int bytes) {
        @Override
        public String toString() {
            return "X=" + x + " Y=" + y + " Z=" + z + " Pitch=" + pitch + " Yaw=" + yaw;
        }
    }

    /**
     * Stores the fraction of the x/y/z values into a single byte. The structure is as follows
     * 0b X X X Y Y Z Z Z
     * X and Z are stored in 8ths, Y is stored in 4ths.
     */
    public static byte getFractionalByte(double dx, double dy, double dz) {
        dx %= 1;
        dy %= 1;
        dz %= 1;
        if (dx < 0) dx++;
        if (dy < 0) dy++;
        if (dz < 0) dz++;
        int x = (int) Math.min(Math.round(dx * 8), 7) << 5;
        int y = (int) Math.min(Math.round(dy * 4), 3) << 3;
        int z = (int) Math.min(Math.round(dz * 8), 7);

        return (byte) (x | y | z);
    }

    /**
     * Retrieves the fractional values from the increment byte generated in {@link PosEncoder#getFractionalByte(double, double, double)}
     *
     * @return An array of doubles of length 3, containing the x, y, and z fractions respectively.
     */
    public static double[] byteToFractions(byte b) {
        int x = (b >> 5) & 0b111;
        int y = (b >> 3) & 0b11;
        int z = b & 0b111;

        return new double[]{x / 8D, y / 4D, z / 8D};
    }
}
