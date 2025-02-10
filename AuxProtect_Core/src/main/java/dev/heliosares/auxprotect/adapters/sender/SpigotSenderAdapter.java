package dev.heliosares.auxprotect.adapters.sender;

import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;

import static io.papermc.lib.PaperLib.teleportAsync;

public class SpigotSenderAdapter extends SenderAdapter {

    private final AuxProtectSpigot plugin;
    private final CommandSender sender;
    int tries;

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
        if (sender instanceof ConsoleCommandSender) {
            AuxProtectSpigot.getMorePaperLib().scheduling().globalRegionalScheduler().run(() -> plugin.getServer().dispatchCommand(sender, command));
        } else {
            AuxProtectSpigot.getMorePaperLib().scheduling().entitySpecificScheduler((Entity) sender).run(() -> plugin.getServer().dispatchCommand(sender, command), null);
        }
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
            teleportAsync(player, target);
            if (player.getGameMode() == GameMode.SPECTATOR) {
                AuxProtectSpigot.getMorePaperLib().scheduling().entitySpecificScheduler(player).runAtFixedRate(task -> {
                    if (tries++ >= 5 || (player.getWorld().equals(target.getWorld())
                            && player.getLocation().distance(target) < 2)) {
                        task.cancel();
                        return;
                    }
                    teleportAsync(player, target);
                }, null, 2, 1);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }
}