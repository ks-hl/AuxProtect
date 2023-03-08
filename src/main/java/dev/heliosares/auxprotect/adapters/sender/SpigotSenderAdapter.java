package dev.heliosares.auxprotect.adapters.sender;

import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class SpigotSenderAdapter extends SenderAdapter {

    private final AuxProtectSpigot plugin;
    private final CommandSender sender;

    public SpigotSenderAdapter(AuxProtectSpigot plugin, CommandSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    public void sendMessageRaw(String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    public void sendMessage(BaseComponent... message) {
        sender.spigot().sendMessage(message);
    }

    public boolean hasPermission(String node) {
        return sender.hasPermission(node);
    }

    public String getName() {
        return sender.getName();
    }

    public UUID getUniqueId() {
        if (sender instanceof Player player) {
            return player.getUniqueId();
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    @Override
    public Object getSender() {
        return sender;
    }

    @Override
    public PlatformType getPlatform() {
        return PlatformType.SPIGOT;
    }

    @Override
    public void executeCommand(String command) {
        plugin.runSync(() -> {
            plugin.getServer().dispatchCommand(sender, command);
        });
    }

    @Override
    public boolean isConsole() {
        return sender.equals(plugin.getServer().getConsoleSender());
    }

    @Override
    public void teleport(String worldname, double x, double y, double z, int pitch, int yaw)
            throws NullPointerException, UnsupportedOperationException {
        if (sender instanceof Player player) {
            World world = plugin.getServer().getWorld(worldname);
            final Location target = new Location(world, x, y, z, yaw, pitch);
            player.teleport(target);
            if (player.getGameMode() == GameMode.SPECTATOR) {
                new BukkitRunnable() {
                    int tries;

                    @Override
                    public void run() {
                        if (tries++ >= 5 || (player.getWorld().equals(target.getWorld())
                                && player.getLocation().distance(target) < 2)) {
                            this.cancel();
                            return;
                        }
                        player.teleport(target);
                    }
                }.runTaskTimer(plugin, 2, 1);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }
}