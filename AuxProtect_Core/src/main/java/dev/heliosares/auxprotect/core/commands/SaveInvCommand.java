package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;
import dev.heliosares.auxprotect.spigot.APPlayerSpigot;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SaveInvCommand<S, P extends IAuxProtect, SA extends SenderAdapter<S, P>> extends Command<S, P, SA> {

    public SaveInvCommand(P plugin) {
        super(plugin, "saveinv", APPermission.INV_SAVE, false);
    }

    @Override
    public void onCommand(SA sender, String label, String[] args) throws CommandException {
        if (args.length != 2) {
            throw new SyntaxException();
        }
        final List<Player> targets = new ArrayList<>();
        if (args[1].equals("*") && APPermission.INV_SAVE_ALL.hasPermission(sender)) {
            // TODO generify
            targets.addAll(Bukkit.getOnlinePlayers());
        } else {
            // TODO generify
            targets.add(Bukkit.getPlayer(args[1]));
        }
        for (Player target : targets) {
            APPlayerSpigot apTarget = null;
            if (target != null) {
                apTarget = (APPlayerSpigot) plugin.getAPPlayer(plugin.getSenderAdapter(target.getName()));
            }
            if (apTarget == null) {
                sender.sendLang(Language.L.LOOKUP_PLAYERNOTFOUND, args[1]);
                return;
            }
            if (!APPermission.ADMIN.hasPermission(sender) && System.currentTimeMillis() - apTarget.lastLoggedInventory < 10000L) {
                sender.sendLang(Language.L.COMMAND__SAVEINV__TOOSOON);
                return;
            }
            long time = apTarget.logInventory("manual");
            sender.sendLang(Language.L.COMMAND__SAVEINV__SUCCESS, target.getName(), target.getName().endsWith("s") ? "" : "s", time + "e");
        }
    }

    @Override
    public boolean exists() {
        return plugin.getPlatform().getLevel() == PlatformType.Level.SERVER;
    }

    @Override
    public List<String> onTabComplete(SA sender, String label, String[] args) {
        if (args.length == 2 && plugin instanceof AuxProtectSpigot spigot) {
            List<String> out = new ArrayList<>(spigot.getServer().getOnlinePlayers().stream().map(Player::getName).toList());
            if (APPermission.INV_SAVE_ALL.hasPermission(sender)) out.add("*");
            return out;
        }
        return null;
    }

}
