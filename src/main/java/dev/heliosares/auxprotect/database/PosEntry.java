package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.APPermission;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
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
        super(time, uid, action, state, world, x, y, z, pitch, yaw, target, target_id, data, SQLManager.getInstance());
        double[] dInc = byteToFractions(increment);
        this.x = x + dInc[0];
        this.y = y + dInc[1];
        this.z = z + dInc[2];
    }

    public PosEntry(long time, int uid, Location location) {
        super(time, uid, EntryAction.POS, false, Objects.requireNonNull(location.getWorld()).getName(),
                (int) Math.round(location.getX()), (int) Math.round(location.getY()), (int) Math.round(location.getZ()),
                Math.round(location.getPitch()), Math.round(location.getYaw()), "", -1, "", SQLManager.getInstance());
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
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
     * Retrieves the fractional values from the increment byte generated in {@link #getFractionalByte(double, double, double)}
     *
     * @return An array of doubles of length 3, containing the x, y, and z fractions respectively.
     */
    public static double[] byteToFractions(byte b) {
        int x = (b >> 5) & 0b111;
        int y = (b >> 3) & 0b11;
        int z = b & 0b111;

        return new double[]{x / 8D, y / 4D, z / 8D};
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
        return (int) Math.floor(x);
    }

    @Override
    public int getY() {
        return (int) Math.floor(y);
    }

    @Override
    public int getZ() {
        return (int) Math.floor(z);
    }

    public byte getIncrement() {
        return getFractionalByte(x, y, z);
    }

    @Override
    public void appendCoordinates(SenderAdapter senderAdapter, ComponentBuilder message) {
        String tpCommand = "/" + AuxProtectAPI.getInstance().getCommandPrefix() + " tp ";
        tpCommand += String.format("%s %s %s ", getDoubleX(), getDoubleY(), getDoubleZ());
        tpCommand += getWorld();
        tpCommand += String.format(" %d %d", getPitch(), getYaw());

        message.append("\n" + " ".repeat(17)).event((HoverEvent) null).event((ClickEvent) null);
        message.append(String.format(ChatColor.COLOR_CHAR + "7(x%d/y%d/z%d/%s)", getX(), getY(), getZ(), getWorld()));

        if (senderAdapter == null || APPermission.TP.hasPermission(senderAdapter)) {
            message.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.COLOR_CHAR + "7" + tpCommand)));
        }

        message.append(String.format(ChatColor.COLOR_CHAR + "7 (p%s/y%d)", getPitch(), getYaw()));
    }
}
