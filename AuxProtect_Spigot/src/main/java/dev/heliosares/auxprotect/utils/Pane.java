package dev.heliosares.auxprotect.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Pane implements InventoryHolder {
    private static final List<Pane> openPanes = new ArrayList<>();
    public final Type type;
    private final Player player;
    private final List<Consumer<Pane>> onClose = new ArrayList<>();
    private Inventory inventory;
    private ArrayList<Button> buttons;
    private boolean cancelled;

    public Pane(Type type, Player player) {
        this.type = type;
        this.player = player;
        openPanes.add(this);
    }

    public static void shutdown() {
        openPanes.forEach((p) -> {
            p.cancelled = true;
            p.getPlayer().closeInventory();
        });
    }

    @Nonnull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
        buttons = new ArrayList<>();
    }

    public void addButton(int slot, Material type, Runnable action, String name) {
        addButton(slot, type, action, name, null);
    }

    public void addButton(int slot, Material type, Runnable action, @Nullable String name, @Nullable List<String> lore) {
        if (inventory == null) throw new IllegalStateException("Inventory not set before adding button");

        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("Null meta on specified item");
        if (name != null) {
            meta.setDisplayName(name);
        }
        if (lore != null) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        buttons.add(new Button(action, slot));
    }

    public boolean click(int slot) {
        if (buttons == null || cancelled) {
            return false;
        }
        for (Button button : buttons) {
            if (slot == button.index) {
                if (button.run != null) {
                    button.run.run();
                }
                return true;
            }
        }
        return false;
    }

    public void onClose(Consumer<Pane> run) {
        this.onClose.add(run);
    }

    public void close() {
        if (cancelled) {
            return;
        }
        for (Consumer<Pane> run : onClose) {
            run.accept(this);
        }
        cancelled = true;
        openPanes.remove(this);
    }

    public void cancel() {
        openPanes.remove(this);
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public Player getPlayer() {
        return player;
    }

    public enum Type {
        CLAIM, SHOW
    }

    private record Button(Runnable run, int index) {
    }
}