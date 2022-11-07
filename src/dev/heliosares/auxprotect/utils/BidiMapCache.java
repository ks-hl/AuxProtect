package dev.heliosares.auxprotect.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

public class BidiMapCache<K, V> {

    private final long timeToLive;
    private final long cleanupInterval;
    private final boolean updateWhenAccessed;
    private HashMap<K, V> set;
    private HashMap<V, K> reverse;
    private HashMap<K, Long> timeAdded;
    private long lastCleanup;
    public BidiMapCache(long timeToLive, long cleanupInterval, boolean updateWhenAccessed) {
        set = new HashMap<>();
        reverse = new HashMap<>();
        timeAdded = new HashMap<>();
        this.timeToLive = timeToLive;
        this.cleanupInterval = cleanupInterval;
        this.lastCleanup = System.currentTimeMillis();
        this.updateWhenAccessed = updateWhenAccessed;
    }

    public void clear() {
        set.clear();
        reverse.clear();
        timeAdded.clear();
        this.lastCleanup = System.currentTimeMillis();
    }

    public boolean containsKey(K key) {
        synchronized (set) {
            return set.containsKey(key);
        }
    }

    public boolean containsValue(V value) {
        synchronized (set) {
            return reverse.containsKey(value);
        }
    }

    public V get(K key) {
        synchronized (set) {
            V value = set.get(key);
            if (value == null) {
                return null;
            }
            if (updateWhenAccessed) {
                timeAdded.put(key, System.currentTimeMillis());
            }
            return value;
        }
    }

    public K getKey(V value) {
        synchronized (set) {
            K key = reverse.get(value);
            if (key == null) {
                return null;
            }
            if (updateWhenAccessed) {
                timeAdded.put(key, System.currentTimeMillis());
            }
            return key;
        }
    }

    public void put(K key, V value) {
        synchronized (set) {
            set.put(key, value);
            reverse.put(value, key);
            timeAdded.put(key, System.currentTimeMillis());
        }
    }

    public Set<Entry<K, V>> entrySet() {
        return set.entrySet();
    }

    public Collection<V> values() {
        return set.values();
    }

    public void cleanup() {
        if (System.currentTimeMillis() - lastCleanup > cleanupInterval) {
            actuallyCleanup();
            lastCleanup = System.currentTimeMillis();
        }
    }

    private void actuallyCleanup() {
        final long cutoff = System.currentTimeMillis() - timeToLive;
        synchronized (set) {
            ArrayList<K> cleanup = new ArrayList<>();
            for (Entry<K, Long> entry : timeAdded.entrySet()) {
                if (entry.getValue().longValue() < cutoff) {
                    cleanup.add(entry.getKey());
                }
            }
            for (K key : cleanup) {
                timeAdded.remove(key);
                V value = set.remove(key);
                if (value != null) {
                    reverse.remove(value);
                }
            }
        }
    }
}
