package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.message.ClickEvent;
import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.spigot.VeinManager;
import dev.heliosares.auxprotect.spigot.commands.XrayCommand;
import jakarta.annotation.Nullable;
import org.bukkit.Location;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

public class XrayEntry extends SpigotDbEntry {

    private final ArrayList<XrayEntry> children = new ArrayList<>();
    public UUID viewer;
    public long viewingStarted;
    private short rating;

    public XrayEntry(String user, Location location, String block) {
        super(user, EntryAction.VEIN, false, location, block, "");
        this.rating = -1;
    }

    public XrayEntry(long time, int uid, String world, int x, int y, int z, int target_id, short rating, String data) {
        super(time, uid, EntryAction.VEIN, false, world, x, y, z, 0, 0, null, target_id, data, SQLManager.getInstance());
        this.rating = rating;
    }

    public boolean add(XrayEntry entry) {
        return this.add(entry, new ArrayList<>());
    }

    private boolean add(XrayEntry other, ArrayList<XrayEntry> visited) {
        if (!visited.add(this)) {
            return false;
        }

        if (Math.abs(other.getTime() - this.getTime()) < 3600000L && Math.abs(other.getX() - this.getX()) <= 2
                && Math.abs(other.getY() - this.getY()) <= 2 && Math.abs(other.getZ() - this.getZ()) <= 2
                && other.getWorld().equals(this.getWorld())) {
            children.add(other);
            return true;
        }
        for (XrayEntry child : children) {
            if (child.add(other, visited)) {
                return true;
            }
        }
        return false;
    }

    public short getRating() {
        return rating;
    }

    public void setRating(short rating, @Nullable String rater) {
        if (rater != null) {
            String data = getData();
            if (!data.isEmpty()) {
                data += "; ";
            }
            String ratedBy = LocalDateTime.now().format(XrayCommand.ratedByDateFormatter) + ": " + rater + " rated " + rating;
            data += ratedBy;
            setData(data);
        }
        this.rating = rating;
    }

    @Override
    public void appendData(GenericBuilder message, IAuxProtect plugin, SenderAdapter<?, ?> sender) {
        String rating;
        if (getRating() == -2) {
            rating = GenericTextColor.COLOR_CHAR + "5Ignored";
        } else if (getRating() == -1) {
            rating = GenericTextColor.COLOR_CHAR + "7Unrated";
        } else {
            rating = getRating() + "";
        }
        String color = VeinManager.getSeverityColor(getRating()).toString();
        message.append(String.format(" " + GenericTextColor.COLOR_CHAR + "8[%s%s" + GenericTextColor.COLOR_CHAR + "8]", color, rating)).event(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND, "/" + plugin.getCommandPrefix() + " xray rate " + getTime()));
        String hover = "";
        if (getRating() >= 0) {
            hover += color + VeinManager.getSeverityDescription(getRating()) + "\n\n";
        }
        hover += Language.translate(Language.L.XRAY_CLICK_TO_CHANGE);
        message.hover(hover);
    }
}
