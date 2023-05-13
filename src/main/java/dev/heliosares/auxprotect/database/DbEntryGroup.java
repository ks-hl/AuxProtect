package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.exceptions.ParseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DbEntryGroup extends DbEntry {
    private final List<DbEntry> entries = new ArrayList<>();
    private final double range;
    private final Parameters params;
    private long firstTime;
    private long lastTime;

    protected DbEntryGroup(DbEntry root, Parameters params) {
        super(0, 0, EntryAction.GROUPING, false, root.getWorld(), root.getX(), root.getY(), root.getZ(), root.getPitch(), root.getYaw(), null, 0, "");
        if (root instanceof DbEntryGroup) throw new IllegalArgumentException("Tried to add a group to a group!");
        this.range = Math.pow(params.getGroupRange(), 2);
        try {
            this.params = params.clone().group(0).clearRadius().addRadius((int) Math.ceil(range), false).setLocation(root.getWorld(), root.getX(), root.getY(), root.getZ());
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
        entries.add(root);
        firstTime = lastTime = root.getTime();
    }

    public boolean add(DbEntry other) {
        if (getDistanceSq(other) <= range) {
            entries.add(other);
            firstTime = Math.min(firstTime, other.getTime());
            lastTime = Math.max(lastTime, other.getTime());
            return true;
        }
        return false;
    }

    public long hash() {
        long hash = 1;

        for (DbEntry entry : entries) hash = 31 * hash + entry.getTime();

        return hash;
    }

    public Parameters getParams() {
        return params;
    }

    public List<DbEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int getNumEntries() {
        return entries.size();
    }

    public long getFirstTime() {
        return firstTime;
    }

    public long getLastTime() {
        return lastTime;
    }

    public String getFormattedEpoch() {
        return getFirstTime() + "e-" + getLastTime() + "e";
    }
}
