package dev.heliosares.auxprotect.utils;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

public class BidiMap<K, V> {

    public BidiMap() {
        set = new HashMap<>();
        reverse = new HashMap<>();
    }

    private HashMap<K, V> set;
    private HashMap<V, K> reverse;

    public void clear() {
        set.clear();
        reverse.clear();
    }

    public boolean containsKey(K key) {
        return set.containsKey(key);
    }

    public boolean containsValue(V value) {
        return reverse.containsKey(value);
    }

    public V get(K key) {
        return set.get(key);
    }

    public K getKey(V value) {
        return reverse.get(value);
    }

    public void put(K key, V value) {
        set.put(key, value);
        reverse.put(value, key);
    }

    public Set<Entry<K, V>> entrySet() {
        return set.entrySet();
    }
}
