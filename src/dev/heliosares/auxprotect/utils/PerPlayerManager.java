package dev.heliosares.auxprotect.utils;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Supplier;

public class PerPlayerManager<V> extends HashMap<UUID, V> {

    private static final long serialVersionUID = 3368537437891349197L;

    private final Supplier<V> supplier;

    public PerPlayerManager(Supplier<V> supplier) {
        this.supplier = supplier;
    }

    public V get(Player player) {
        synchronized (this) {
            if (containsKey(player.getUniqueId())) {
                return get(player.getUniqueId());
            }
            V v = supplier.get();
            put(player.getUniqueId(), v);
            return v;
        }
    }

    public V remove(Player player) {
        synchronized (this) {
            return remove(player.getUniqueId());
        }
    }
}
