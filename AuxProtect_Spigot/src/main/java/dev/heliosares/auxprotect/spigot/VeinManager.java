package dev.heliosares.auxprotect.spigot;

import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.exceptions.BusyException;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

public class VeinManager {
    private final ArrayList<XrayEntry> entries = new ArrayList<>();
    private final ArrayList<XrayEntry> ignoredentries = new ArrayList<>();
    private final HashMap<UUID, ArrayList<Long>> skipped = new HashMap<>();

    public static String getSeverityDescription(int severity) {
        return switch (severity) {// TODO lang
            case -2 -> "ignored";
            case -1 -> "unrated";
            case 0 -> "no concern";
            case 1 -> "slightly suspicious";
            case 2 -> "suspicious, not certain";
            case 3 -> "almost certain or entirely certain";
            default -> "unknown severity";
        };
    }

    public static GenericTextColor getSeverityColor(int severity) {
        return switch (severity) {
            case -2, -1 -> GenericTextColor.DARK_PURPLE;
            case 0 -> GenericTextColor.GREEN;
            case 1 -> GenericTextColor.YELLOW;
            case 2 -> GenericTextColor.RED;
            case 3 -> GenericTextColor.DARK_RED;
            default -> GenericTextColor.WHITE;
        };
    }

    /**
     * @return true if already part of a vein
     */
    public boolean add(XrayEntry entry) {
        synchronized (entries) {
            for (XrayEntry other : entries) {
                if (other.add(entry)) {
                    return true;
                }
            }
            for (XrayEntry other : ignoredentries) {
                if (other.add(entry)) {
                    return true;
                }
            }
            if (entry.getRating() == -2) {
                ignoredentries.add(entry);
                return false;
            }
            entries.add(entry);
            entries.sort(Comparator.comparing(DbEntry::getTime));
            entries.sort((o1, o2) -> {
                try {
                    if (o1.getUser(false) == null) SQLManager.getInstance().execute(c -> o1.getUser(), 3000L);
                    if (o2.getUser(false) == null) SQLManager.getInstance().execute(c -> o2.getUser(), 3000L);
                    return o1.getUser().compareTo(o2.getUser());
                } catch (SQLException | BusyException e) {
                    AuxProtectAPI.getInstance().print(e);
                    return 0;
                }
            });
        }
        return false;
    }

    public XrayEntry next(UUID uuid) {
        XrayEntry current = current(uuid);
        if (current != null) {
            remove(current);
        }
        synchronized (entries) {
            ArrayList<Long> skipped = this.skipped.get(uuid);
            for (XrayEntry entry : entries) {
                if (skipped != null && skipped.contains(entry.getTime())) {
                    continue;
                }
                if (entry.viewer != null) {
                    if (System.currentTimeMillis() - entry.viewingStarted < 10 * 60 * 1000L) {
                        continue;
                    }
                }
                entry.viewer = uuid;
                entry.viewingStarted = System.currentTimeMillis();
                return entry;
            }

            this.skipped.remove(uuid);
        }
        return null;
    }

    public boolean skip(UUID uuid, long time) {
        synchronized (entries) {
            ArrayList<Long> skipped = this.skipped.get(uuid);
            if (skipped == null) {
                skipped = new ArrayList<>();
            }
            skipped.add(time);
            this.skipped.put(uuid, skipped);
            for (XrayEntry entry : entries) {
                if (entry.getTime() == time) {
                    release(entry);
                    return true;
                }
            }
        }
        return false;
    }

    public void remove(XrayEntry entry) {
        synchronized (entries) {
            entries.remove(entry);
        }
    }

    public XrayEntry current(UUID uuid) {
        XrayEntry current = null;
        for (XrayEntry entry : entries) {
            if (uuid.equals(entry.viewer)) {
                if (current == null) {
                    current = entry;
                    continue;
                }
                release(current);
            }
        }
        return current;
    }

    public void release(XrayEntry entry) {
        entry.viewer = null;
        entry.viewingStarted = 0;
    }

    public int size() {
        return entries.size();
    }

    public void iterator(Consumer<Iterator<XrayEntry>> consumer) {
        synchronized (entries) {
            Iterator<XrayEntry> it = entries.iterator();
            consumer.accept(it);
        }
    }
}
