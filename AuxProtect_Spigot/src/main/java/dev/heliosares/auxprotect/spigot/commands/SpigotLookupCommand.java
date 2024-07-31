package dev.heliosares.auxprotect.spigot.commands;

import dev.heliosares.auxprotect.adapters.sender.SpigotSenderAdapter;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.core.commands.LookupCommand;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.TransactionEntry;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.MoneySolver;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class SpigotLookupCommand extends LookupCommand<CommandSender, AuxProtectSpigot, SpigotSenderAdapter> {
    public SpigotLookupCommand(AuxProtectSpigot plugin) {
        super(plugin);
    }

    @Override
    protected void count(List<DbEntry> rs, CountResult countResult) throws SQLException, BusyException {
        super.count(rs, countResult);

        for (DbEntry entry : rs) {
            if (entry instanceof TransactionEntry transactionEntry) {
                countResult.totalMoney += transactionEntry.getCost() * (transactionEntry.getState() ? -1 : 1);
            }
        }
    }

    @Override
    protected void handleResults(Parameters params_, List<DbEntry> rs, SpigotSenderAdapter sender) throws SQLException, BusyException, LookupException {
        if (params_.hasFlag(Parameters.Flag.MONEY)) {
            Set<String> users = params_.getUsers();
            if (users.isEmpty()) {
                sender.sendLang(Language.L.COMMAND__LOOKUP__PLAYTIME__NOUSER);
                return;
            }
            if (users.size() > 1) {
                sender.sendLang(Language.L.COMMAND__LOOKUP__PLAYTIME__TOOMANYUSERS);
                return;
            }
            if (sender.getSender() instanceof org.bukkit.entity.Player player) {
                MoneySolver.showMoney(plugin, player, rs, (int) Math.round((double) params_.getAfter() / (1000 * 3600)),
                        users.stream().findAny().orElse(null));
            }
            return;
        }

        super.handleResults(params_, rs, sender);
    }
}
