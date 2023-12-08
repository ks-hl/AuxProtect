package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class SaveInvCommand extends Command {

    public SaveInvCommand(IAuxProtect plugin) {
        super(plugin, "saveinv", APPermission.INV_SAVE, false);
    }

    @Override
    public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
        if (args.length != 2) {
            throw new SyntaxException();
        }
        Player target = Bukkit.getPlayer(args[1]);
        APPlayer apTarget = null;
        if (target != null) {
            apTarget = plugin.getAPPlayer(sender);
        }
        if (apTarget == null) {
            sender.sendLang(Language.L.LOOKUP_PLAYERNOTFOUND, args[1]);
            return;
        }
        if (!APPermission.ADMIN.hasPermission(sender)
                && System.currentTimeMillis() - apTarget.lastLoggedInventory < 10000L) {
            sender.sendLang(Language.L.COMMAND__SAVEINV__TOOSOON);
            return;
        }
        long time = apTarget.logInventory("manual");
        sender.sendLang(Language.L.COMMAND__SAVEINV__SUCCESS, target.getName(), target.getName().endsWith("s") ? "" : "s",
                time + "e");
    }

    @Override
    public boolean exists() {
        return plugin.getPlatform() == PlatformType.SPIGOT;
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        if (args.length == 2 && plugin instanceof AuxProtectSpigot spigot) {
            return spigot.getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return null;
    }

}
