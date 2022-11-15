package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.*;
import dev.heliosares.auxprotect.database.ConnectionPool;
import dev.heliosares.auxprotect.database.InvDiffManager.DiffInventoryRecord;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.PlatformException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class InventoryCommand extends Command {

    public InventoryCommand(IAuxProtect plugin) {
        super(plugin, "inventory", APPermission.INV, true);
    }

    @Override
    public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
        if (args.length != 3) {
            throw new SyntaxException();
        }
        if (sender.getPlatform() != PlatformType.SPIGOT) {
            throw new PlatformException();
        }
        if (sender.getSender() instanceof Player player && plugin instanceof AuxProtectSpigot) {
            String target = args[1];
            String paramtime = args[2];

            long time_;
            try {
                time_ = TimeUtil.stringToMillis(paramtime);
                if (time_ < 0) {
                    sender.sendLang(Language.L.INVALID_PARAMETER, paramtime);
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendLang(Language.L.INVALID_PARAMETER, paramtime);
                return;
            }

            if (!paramtime.endsWith("e")) {
                time_ = System.currentTimeMillis() - time_;
            }

            final long time = time_;

            int uid;
            String uuid;
            try {
                uid = plugin.getSqlManager().getUserManager().getUIDFromUsername(target, false);
                uuid = plugin.getSqlManager().getUserManager().getUUIDFromUID(uid, false);
            } catch (ConnectionPool.BusyException e) {
                sender.sendLang(Language.L.DATABASE_BUSY);
                return;
            } catch (SQLException e) {
                sender.sendLang(Language.L.ERROR);
                return;
            }
            OfflinePlayer targetP = Bukkit.getOfflinePlayer(UUID.fromString(uuid.substring(1)));
            if (uid < 0) {
                sender.sendLang(Language.L.LOOKUP_PLAYERNOTFOUND, target);
                return;
            }
            DiffInventoryRecord inv = null;
            try {
                inv = plugin.getSqlManager().getInvDiffManager().getContentsAt(uid, time);
            } catch (ConnectionPool.BusyException e) {
                sender.sendLang(Language.L.DATABASE_BUSY);
                return;
            } catch (ClassNotFoundException | SQLException | IOException e) {
                plugin.print(e);
                sender.sendLang(Language.L.ERROR);
                return;
            }
            if (inv == null) {
                sender.sendLang(Language.L.COMMAND__LOOKUP__NORESULTS);
                return;
            }

            Inventory output = InvCommand.makeInventory(plugin, player, targetP, inv.inventory(), time);

            sender.sendMessageRaw(String.format("§fDisplaying inventory of §9%s§f from §9%s ago §7(%s)",
                    targetP.getName(), TimeUtil.millisToString(System.currentTimeMillis() - time), time + "e"));
            sender.sendMessageRaw(
                    String.format("§fBased on inventory from §9%s§f ago §7(%s)§f with §9%s§f differences",
                            TimeUtil.millisToString(System.currentTimeMillis() - inv.basetime()),
                            inv.basetime() + "e", inv.numdiff()));
            ;

            plugin.runSync(() -> player.openInventory(output));
        } else {
            throw new PlatformException();
        }
    }

    @Override
    public boolean exists() {
        return plugin.getPlatform() == PlatformType.SPIGOT;
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        return APCommand.tabCompletePlayerAndTime(plugin, sender, label, args);
    }
}
