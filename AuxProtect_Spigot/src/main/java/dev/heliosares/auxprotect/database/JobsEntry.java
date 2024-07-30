package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.spigot.listeners.JobsListener;
import org.bukkit.Location;

public class JobsEntry extends SpigotDbEntry {

    public final char type;
    private double value;

    public JobsEntry(String userLabel, Location location, String jobName, char type, double value) {
        super(userLabel, EntryAction.JOBS, false, location, jobName, "");
        this.value = value;
        this.type = type;
    }

    public JobsEntry(String userLabel, String jobName, char type, double value) {
        super(userLabel, EntryAction.JOBS, false, null, jobName, "");
        this.value = value;
        this.type = type;
    }

    @Override
    public String getData() {
        return type + "" + Math.round(value * 100f) / 100f;
    }

    public boolean add(JobsEntry other) {
        if (userLabel.equals(other.userLabel) && type == other.type) {
            this.value += other.value;
            return true;
        }
        return false;
    }
}
