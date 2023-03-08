package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Parameters;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;

import java.sql.SQLException;
import java.util.List;

public class TransactionResults extends Results {
    public TransactionResults(IAuxProtect plugin, List<DbEntry> entries, SenderAdapter player, Parameters params) {
        super(plugin, entries, player, params);
        setPerPage(10);
    }

    @Override
    public void sendEntry(DbEntry entry, int index) throws SQLException {
        if (!(entry instanceof TransactionEntry transactionEntry)) return;

        if (entry.getUser(false) == null) SQLManager.getInstance().execute(c -> entry.getUser(), 3000L);
        if (entry.getTarget(false) == null) SQLManager.getInstance().execute(c -> entry.getTarget(), 3000L);
        if (transactionEntry.getItemType(false) == null) {
            SQLManager.getInstance().execute(c -> transactionEntry.getItemType(true), 3000L);
        }

        ComponentBuilder message = new ComponentBuilder();
        message.append(getTime(entry.getTime()));

        String actionColor = ChatColor.GRAY + "-";
        if (entry.getAction().hasDual) {
            actionColor = entry.getState() ? ChatColor.GREEN + "+" : ChatColor.RED + "-";
        }

        message.append(" " + actionColor + " ").event((HoverEvent) null);

        // 1d ago +$5 -
    }
}
