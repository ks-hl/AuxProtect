package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.utils.PosEncoder.Posture;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestPosEncoder {
    @Test
    public void testEncodeAndDecode() {
        final double min = Short.MIN_VALUE / 10d;
        final double max = Short.MAX_VALUE / 10d;
        addTest(new Vector(max, max, max), 127.0f, 180.0f, Posture.STANDING); // max values
        addTest(new Vector(min, min, min), -128.0f, -180.0f, Posture.SNEAKING); // min values
        addTest(new Vector(0, 0, 0), 0.0f, 0.0f, Posture.SWIMMING); // zero movement
        addTest(new Vector(0.001, 0.001, 0.001), 0.1f, 0.1f, Posture.GLIDING); // small increments
        addTest(new Vector(0.001, 0.001, -0.001), 45.0f, 90.0f, Posture.SNEAKING); // small increments

        // Add tests for each posture
        for (Posture posture : Posture.values()) {
            addTest(new Vector(10, 10, 10), -45.0f, 90.0f, posture); // test each posture
        }

        // Test positive overflow
        var decoded = getEncodeDecode(new Vector(max + 3, 0, 0), 0, 0, Posture.STANDING);
        assertEquals(decoded.x(), max, 0.1);

        // Test negative overflow
        decoded = getEncodeDecode(new Vector(min - 3, 0, 0), 0, 0, Posture.STANDING);
        assertEquals(decoded.x(), min, 0.1);
    }

    private void addTest(Vector add, float pitch, float yaw, Posture posture) {
        addTest(add, pitch, yaw, posture, getEncodeDecode(add, pitch, yaw, posture));
    }

    private PosEncoder.PositionIncrement getEncodeDecode(Vector add, float pitch, float yaw, Posture posture) {
        Location from = new Location(null, 0, 0, 0);
        Location to = from.clone().add(add);
        to.setPitch(pitch);
        to.setYaw(yaw);

        // Encode the position change
        byte[] encoded = PosEncoder.encode(from, to, posture, null);

        // Decode the position change
        var decodedList = PosEncoder.decode(encoded);
        // Assertions to verify encoding and decoding
        assert decodedList.size() == 1;

        return decodedList.get(0);
    }

    private void addTest(Vector add, float pitch, float yaw, Posture posture, PosEncoder.PositionIncrement decoded) {
        assertEquals(add.getX(), decoded.x(), add.getX() > 1 ? 0.1 : 0.01);
        assertEquals(add.getY(), decoded.y(), add.getY() > 1 ? 0.1 : 0.01);
        assertEquals(add.getZ(), decoded.z(), add.getZ() > 1 ? 0.1 : 0.01);
        assertEquals(pitch, decoded.pitch(), 0.5);
        assertEquals(yaw, decoded.yaw(), 1.42);
        assertEquals(posture, decoded.posture());
    }

    @Test
    public void testBitManipulation() {
        byte testByte = 0;

        // Set and check each bit
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 8; i++) {
                assert !PosEncoder.getBit(testByte, i);
                testByte = PosEncoder.setBit(testByte, i, true);
                assert PosEncoder.getBit(testByte, i);
                testByte = PosEncoder.setBit(testByte, i, false);
                assert !PosEncoder.getBit(testByte, i);
            }
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetBitWithInvalidIndex() {
        //noinspection ResultOfMethodCallIgnored
        PosEncoder.setBit((byte) 0, 8, true);
    }
}
