package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Language.L;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.Pane;
import dev.heliosares.auxprotect.utils.Pane.Type;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class ClaimInvCommand implements CommandExecutor {

    private final AuxProtectSpigot plugin;

    public ClaimInvCommand(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@Nonnull CommandSender sender, @Nullable Command command, @Nonnull String label, @Nonnull String[] args) {
        plugin.runAsync(() -> {
            int uid;
            boolean other;
            OfflinePlayer target;
            try {
                other = (args.length == 1) && APPermission.INV_RECOVER.hasPermission(sender);
                if (other) {
                    uid = plugin.getSqlManager().getUserManager().getUIDFromUsername(args[0], false);
                    if (uid <= 0) {
                        sender.sendMessage(Language.L.PLAYERNOTFOUND.translate());
                        return;
                    }
                    target = Bukkit.getOfflinePlayer(UUID.fromString(
                            plugin.getSqlManager().getUserManager().getUUIDFromUID(uid, false).substring(1)));
                } else if (sender instanceof Player player) {
                    uid = plugin.getSqlManager().getUserManager().getUIDFromUUID("$" + player.getUniqueId(),
                            false);
                    target = player;
                } else {
                    sender.sendMessage(Language.L.NOTPLAYERERROR.translate());
                    return;
                }
                byte[] data = null;
                if (uid > 0) {
                    data = plugin.getSqlManager().getUserManager().getPendingInventory(uid);
                }
                out:
                if (data == null) {
                    if (other) {
                        if (target instanceof Player play
                                && play.getOpenInventory().getTopInventory().getHolder() instanceof Pane pane) {
                            pane.cancel();
                            AuxProtectSpigot.getMorePaperLib().scheduling().entitySpecificScheduler(play).run(play::closeInventory, null);
                            break out;
                        }
                        sender.sendMessage(L.COMMAND__CLAIMINV__OTHERHASNONE.translate());
                    } else {
                        sender.sendMessage(L.COMMAND__CLAIMINV__YOUHAVENONE.translate());
                    }
                    return;
                }
                if (other) {
                    plugin.getSqlManager().getUserManager().setPendingInventory(uid, null);
                    if (target.getPlayer() != null) { // Player is online
                        target.getPlayer().sendMessage(L.COMMAND__CLAIMINV__CANCELLED.translate());
                    }
                    sender.sendMessage(L.COMMAND__CLAIMINV__CANCELLED_OTHER.translate(target.getName(), Language.getOptionalS(target.getName())));
                } else if (target.getPlayer() != null) {
                    Inventory inv;
                    Pane pane = new Pane(Type.CLAIM, target.getPlayer());
                    pane.onClose(p -> {
                        ArrayList<ItemStack> leftover = new ArrayList<>();
                        for (int i = 0; i < p.getInventory().getSize(); i++) {
                            ItemStack item = p.getInventory().getItem(i);
                            if (item == null || item.getType() == Material.AIR)
                                continue;
                            HashMap<Integer, ItemStack> left = p.getPlayer().getInventory().addItem(item);
                            p.getInventory().setItem(i, new ItemStack(Material.AIR));
                            leftover.addAll(left.values());
                        }
                        for (ItemStack i : leftover) {
                            p.getPlayer().getWorld().dropItem(p.getPlayer().getLocation(), i);
                        }
                    });
                    try {
                        inv = InvSerialization.toInventory(data, pane, L.COMMAND__CLAIMINV__HEADER.translate());
                    } catch (Exception e1) {
                        plugin.warning("Error serializing inventory claim");
                        throw e1;
                    }
                    pane.setInventory(inv);
                    plugin.getSqlManager().getUserManager().setPendingInventory(uid, null);
                    InvCommand.openSync(plugin, target.getPlayer(), inv);
                    // Executed after clearing the pending
                    // inventory to prevent duplication
                }
            } catch (Exception e) {
                sender.sendMessage(Language.translate(Language.L.ERROR));
                plugin.print(e);
            }
        });
        return true;
    }
}
