package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SpigotDbEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.InvSerialization;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import su.nightexpress.excellentcrates.api.event.CrateObtainRewardEvent;
import su.nightexpress.excellentcrates.api.event.CrateOpenEvent;
import su.nightexpress.excellentcrates.crate.reward.impl.ItemReward;

import java.io.IOException;

public class ExcellentCratesListener implements Listener {
    private final AuxProtectSpigot plugin;

    public ExcellentCratesListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void on(CrateOpenEvent e) {
        SpigotDbEntry entry = new SpigotDbEntry(
                AuxProtectSpigot.getLabel(e.getPlayer()),
                EntryAction.CRATEOPEN,
                false,
                e.getPlayer().getLocation(),
                e.getCrate().getId(),
                ""
        );
        plugin.add(entry);
    }

    @EventHandler
    public void on(CrateObtainRewardEvent e) {
        SpigotDbEntry entry = new SpigotDbEntry(
                AuxProtectSpigot.getLabel(e.getPlayer()),
                EntryAction.CRATEREWARD,
                false,
                e.getPlayer().getLocation(),
                e.getReward().getId(),
                "crate: " + e.getCrate().getId()
        );
        if (e.getReward() instanceof ItemReward itemReward) {
            ItemStack[] items = new ItemStack[itemReward.getItems().size()];
            StringBuilder debug = new StringBuilder();
            for (int i = 0; i < itemReward.getItems().size(); i++) {
                items[i] = itemReward.getItems().get(i).getItemStack();
                debug.append(items[i]).append(", ");
            }
            try {
                entry.setBlob(InvSerialization.toByteArray(items));
            } catch (IOException ex) {
                plugin.warning("Failed to serialize crate reward: " + debug);
            }
        }
        plugin.add(entry);
    }
}
