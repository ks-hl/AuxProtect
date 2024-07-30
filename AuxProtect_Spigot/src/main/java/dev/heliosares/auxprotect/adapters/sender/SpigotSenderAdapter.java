package dev.heliosares.auxprotect.adapters.sender;

import dev.heliosares.auxprotect.exceptions.NotPlayerException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Optional;
import java.util.UUID;

public class SpigotSenderAdapter extends SenderAdapter<CommandSender, AuxProtectSpigot> implements PositionedSender, BungeeComponentSender {

    public SpigotSenderAdapter(AuxProtectSpigot plugin, CommandSender sender) {
        super(sender, plugin);
    }

    public void sendMessageRaw_(String message) {
        sender.sendMessage(message);
    }

    public boolean hasPermission(String node) {
        return sender.hasPermission(node);
    }

    @Override
    public void sendMessage(BaseComponent... component) {
        getSender().spigot().sendMessage(component);
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
    public void executeCommand(String command) {
        plugin.runSync(() -> plugin.getServer().dispatchCommand(sender, command));
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

    private Location getLocation() throws NotPlayerException {
        if (!(getSender() instanceof Player player)) throw new NotPlayerException();

        return player.getLocation();
    }

    @Override
    public int getBlockX() throws NotPlayerException {
        return getLocation().getBlockX();
    }

    @Override
    public int getBlockY() throws NotPlayerException {
        return getLocation().getBlockY();
    }

    @Override
    public int getBlockZ() throws NotPlayerException {
        return getLocation().getBlockZ();
    }

    @Override
    public double getX() throws NotPlayerException {
        return getLocation().getX();
    }

    @Override
    public double getY() throws NotPlayerException {
        return getLocation().getY();
    }

    @Override
    public double getZ() throws NotPlayerException {
        return getLocation().getZ();
    }

    @Override
    public String getWorldName() throws NotPlayerException {
        return Optional.ofNullable(getLocation().getWorld()).map(World::getName).orElse(null);
    }
}