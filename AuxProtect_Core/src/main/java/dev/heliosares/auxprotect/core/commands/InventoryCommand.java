package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.*;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.InvDiffManager.DiffInventoryRecord;
import dev.heliosares.auxprotect.exceptions.*;
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
        if (args.length != 2 && args.length != 3) throw new SyntaxException();
        if (plugin.getPlatform() != PlatformType.SPIGOT) throw new PlatformException();
        if (!(sender.getSender() instanceof Player player)) throw new NotPlayerException();

        String target = args[1];
        String paramtime;
        if (args.length == 3) paramtime = args[2];
        else paramtime = "2w";

        if (!paramtime.startsWith("@")) {
            sender.executeCommand(String.format(plugin.getCommandPrefix() + " lookup user:%s action:" + EntryAction.INVENTORY + " time:%s", target, paramtime));
            return;
        }

        if (plugin.getAPConfig().getInventoryDiffInterval() == 0) {
            sender.sendLang(Language.L.ACTION_DISABLED);
            return;
        }

        paramtime = paramtime.substring(1);

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
        } catch (BusyException e) {
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
        DiffInventoryRecord inv;
        try {
            inv = plugin.getSqlManager().getInvDiffManager().getContentsAt(uid, time);
        } catch (BusyException e) {
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

        sender.sendMessageRaw(String.format("&fDisplaying inventory of &9%s&f from &9%s ago &7(%s)",
                targetP.getName(), TimeUtil.millisToString(System.currentTimeMillis() - time), time + "e"));
        sender.sendMessageRaw(
                String.format("&fBased on inventory from &9%s&f ago &7(%s)&f with &9%s&f differences",
                        TimeUtil.millisToString(System.currentTimeMillis() - inv.basetime()),
                        inv.basetime() + "e", inv.numdiff()));

        plugin.runSync(() -> player.openInventory(output));
    }

    @Override
    public boolean exists() {
        return plugin.getPlatform() == PlatformType.SPIGOT;
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        List<String> out = APCommand.tabCompletePlayerAndTime(plugin, sender, args);
        if (out != null && args.length == 3 && plugin.getAPConfig().getInventoryDiffInterval() > 0) {
            out.add("@");
        }
        return out;
    }
}
