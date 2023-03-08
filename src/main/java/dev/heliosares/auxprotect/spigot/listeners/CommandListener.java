package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class CommandListener implements Listener {

    private final AuxProtectSpigot plugin;

    public CommandListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreProcess(PlayerCommandPreprocessEvent e) {
        if (!plugin.getAPConfig().isOverrideCommands()) {
            return;
        }
        String[] args1 = e.getMessage().substring(1).split(" ");
        String label = args1[0];
        boolean auxprotect = label.equalsIgnoreCase("auxprotect") || label.equalsIgnoreCase("ap");
        boolean claiminv = label.equalsIgnoreCase("claiminv");
        if (auxprotect || claiminv) {
            String[] args = new String[args1.length - 1];
            System.arraycopy(args1, 1, args, 0, args.length);
            if (auxprotect) {
                plugin.getApcommand().onCommand(e.getPlayer(), null, label, args);
            } else if (claiminv) {
                plugin.getClaiminvcommand().onCommand(e.getPlayer(), null, label, args);
            }
            e.setCancelled(true);
        }
    }
}
