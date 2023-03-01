package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.utils.PosEncoder;
import org.bukkit.Location;

import java.util.Objects;

public class PosEntry extends DbEntry {
    private final double x;
    private final double y;
    private final double z;

    public PosEntry(String userUuid, EntryAction action, boolean state, Location location, String target) {
        super(userUuid, action, state, location, target, "");
        // This rounding is done to account for a player being at 7.5/8 of a block in the x/z axis or 3.5/4 in the Y axis.
        // Without this rounding, in the example above, the x/y/z values in the database would be 1 value too low
        this.x = Math.round(location.getX() * 8D) / 8D;
        this.y = Math.round(location.getY() * 4D) / 4D;
        this.z = Math.round(location.getZ() * 8D) / 8D;
    }

    protected PosEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z, byte increment, int pitch, int yaw, String target, int target_id, String data) {
        super(time, uid, action, state, world, x, y, z, pitch, yaw, target, target_id, data);
        double[] dInc = PosEncoder.byteToFractions(increment);
        this.x = x + dInc[0] * (x < 0 ? -1 : 1);
        this.y = y + dInc[1] * (y < 0 ? -1 : 1);
        this.z = z + dInc[2] * (z < 0 ? -1 : 1);
    }

    public PosEntry(long time, int uid, Location location) {
        super(time, uid, EntryAction.POS, false, Objects.requireNonNull(location.getWorld()).getName(),
                (int) Math.round(location.getX()), (int) Math.round(location.getY()), (int) Math.round(location.getZ()),
                Math.round(location.getPitch()), Math.round(location.getYaw()), "", -1, "");
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
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

    @Override
    public int getX() {
        return (int) x;
    }

    @Override
    public int getY() {
        return (int) y;
    }

    @Override
    public int getZ() {
        return (int) z;
    }

    public byte getIncrement() {
        return PosEncoder.getFractionalByte(x, y, z);
    }
}
