package dev.heliosares.auxprotect.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class PosEncoder {

    record Simpl(byte[] array, boolean fine) {
    }

    public static byte[] encode(Player player, Location lastLoc) {
        Simpl diffX = simplify(player.getLocation().getX() - lastLoc.getX());
        Simpl diffY = simplify(player.getLocation().getY() - lastLoc.getY());
        Simpl diffZ = simplify(player.getLocation().getZ() - lastLoc.getZ());
        byte pitch = (byte) player.getLocation().getPitch();
        boolean doPitch = player.getLocation().getPitch() != lastLoc.getPitch();
        byte yaw = (byte) ((player.getLocation().getYaw() / 180.0) * 127);
        boolean doYaw = player.getLocation().getYaw() != lastLoc.getYaw();
        byte hdr = 0;

        hdr |= diffX.fine ? 3 : diffX.array.length;
        hdr |= (diffY.fine ? 3 : diffY.array.length) << 2;
        hdr |= (diffZ.fine ? 3 : diffZ.array.length) << 4;
        if (doPitch) hdr |= 1 << 6;
        if (doYaw) hdr -= 128;

        int len = 1 + diffX.array.length + diffY.array.length + diffZ.array.length;
        if (doPitch) len++;
        if (doYaw) len++;
        ByteBuffer bb = ByteBuffer.allocate(len);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(hdr);
        bb.put(diffX.array);
        bb.put(diffY.array);
        bb.put(diffZ.array);
        if (doPitch) bb.put(pitch);
        if (doYaw) bb.put(yaw);

        return bb.array();
    }

    public static List<DecodedPositionIncrement> decode(byte[] bytes) {
        List<DecodedPositionIncrement> out = new ArrayList<>();
        for (int i = 0, safety = 0; i < bytes.length && safety < bytes.length; safety++) {
            DecodedPositionIncrement decod = decodeSingle(bytes, i);
            i += decod.bytes;
            out.add(decod);
        }
        return out;
    }

    public static DecodedPositionIncrement decodeSingle(byte[] bytes, int offset) {
        double[] out = new double[5];
        byte hdr = bytes[offset];

        boolean yaw = hdr < 0;
        if (yaw) hdr += 128;

        int xlen = hdr & 0b11;
        int ylen = (hdr >> 2) & 0b11;
        int zlen = (hdr >> 4) & 0b11;

        if (xlen > 0) out[0] = toDouble(bytes, offset + 1, xlen);
        if (xlen == 3) xlen = 1;
        if (ylen > 0) out[1] = toDouble(bytes, offset + 1 + xlen, ylen);
        if (ylen == 3) ylen = 1;
        if (zlen > 0) out[2] = toDouble(bytes, offset + 1 + xlen + ylen, zlen);
        if (zlen == 3) zlen = 1;

        boolean pitch = (hdr >> 6 & 1) == 1;
        if (pitch) out[3] = bytes[offset + 1 + xlen + ylen + zlen];
        if (yaw) out[4] = (double) bytes[offset + 1 + xlen + ylen + zlen + (pitch ? 1 : 0)] / 127.0 * 180;

        return new DecodedPositionIncrement(xlen > 0, out[0], ylen > 0, out[1], zlen > 0, out[2], pitch, (float) out[3], yaw, (float) out[4], 1 + xlen + ylen + zlen + (yaw ? 1 : 0) + (pitch ? 1 : 0));
    }

    public record DecodedPositionIncrement(boolean hasx, double x, boolean hasy, double y, boolean hasz, double z,
                                           boolean hasPitch, float pitch, boolean hasYaw, float yaw, int bytes) {
        @Override
        public String toString() {
            return "X=" + x + " Y=" + y + " Z=" + z + " Pitch=" + pitch + " Yaw=" + yaw;
        }
    }

    private static double toDouble(byte[] bytes, int index, int hdr) {
        double sig;
        if (hdr == 3) {
            hdr = 1;
            sig = 100.0;
        } else sig = 10.0;
        if (hdr == 0) return 0;
        if (hdr == 1) return (double) bytes[index] / sig;
        return Math.round((((int) bytes[index]) << 8) | (bytes[index + 1] & 0xff)) / sig;
    }


    private static Simpl simplify(double d) {
        boolean fine = Math.abs(d) < 1;
        d *= 10;
        if (fine) d *= 10;
        short s = (short) Math.round(d);

        //0
        if (s == 0) return new Simpl(new byte[0], false);

        //1
        byte lower = (byte) s;
        if (s == lower) return new Simpl(new byte[]{lower}, fine);

        //2
        if (d > Short.MAX_VALUE) s = Short.MAX_VALUE;
        else if (d < Short.MIN_VALUE) s = Short.MIN_VALUE;

        return new Simpl(new byte[]{(byte) (s >> 8), lower}, false);
    }
}
