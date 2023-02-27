package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.utils.PosEncoder;
import org.bukkit.Location;

public class PosEntry extends DbEntry {
    private final double x;
    private final double y;
    private final double z;

    public PosEntry(String userUuid, EntryAction action, boolean state, Location location, String target) {
        super(userUuid, action, state, location, target, "");
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
    }

    protected PosEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z, byte increment, int pitch, int yaw, String target, int target_id, String data) {
        super(time, uid, action, state, world, x, y, z, pitch, yaw, target, target_id, data);
        double[] dInc = PosEncoder.byteToFractions(increment);
        this.x = x + dInc[0];
        this.y = y + dInc[1];
        this.z = z + dInc[2];
    }

    public double getDoubleX() {
        return x;
    }

    public double getDoubleY() {
        return y;
    }

    public double getDoubleZ() {
        return z;
    }

    public byte getIncrement() {
        return PosEncoder.getFractionalByte(x, y, z);
    }
}
