package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;
import dev.heliosares.auxprotect.utils.TimeUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PurgeCommand <S, P extends IAuxProtect, SA extends SenderAdapter<S, P>> extends Command<S,P,SA> {

    public PurgeCommand(P plugin) {
        super(plugin, "purge", APPermission.PURGE, true);
    }

    public void onCommand(SA sender, String label, String[] args) throws CommandException {
        if (args.length != 3) throw new SyntaxException();
        if (!plugin.getSqlManager().isConnected()) {
            sender.sendLang(Language.L.DATABASE_BUSY);
            return;
        }

        Table table_ = null;
        if (!args[1].equalsIgnoreCase("all")) {
            try {
                table_ = Table.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
            if (table_ == null || !table_.exists(plugin)) {
                sender.sendLang(Language.L.COMMAND__PURGE__TABLE);
                return;
            }
        }
        if (table_ != null && !table_.canPurge()) {
            sender.sendLang(Language.L.COMMAND__PURGE__NOPURGE);
            return;
        }
        final Table table = table_;
        long time_;
        try {
            time_ = TimeUtil.stringToMillis(args[2]);
        } catch (NumberFormatException e) {
            throw new SyntaxException();
        }

        if (time_ < Table.MIN_PURGE_INTERVAL) {
            sender.sendLang(Language.L.COMMAND__PURGE__TIME);
            return;
        }

        final long time = time_;
        sender.sendLang(Language.L.COMMAND__PURGE__PURGING, table == null ? "all" : table.toString());

        int count = 0;
        try {
            count += plugin.getSqlManager().purge(table, time);
            sender.sendLang(Language.L.COMMAND__PURGE__UIDS);
            count += plugin.getSqlManager().purgeUIDs();

            if (!plugin.getSqlManager().isMySQL()) {
                plugin.getSqlManager().execute(plugin.getSqlManager()::vacuum, 30000L);
            }
        } catch (SQLException | BusyException e) {
            plugin.print(e);
            sender.sendLang(Language.L.COMMAND__PURGE__ERROR);
            return;
        }
        sender.sendLang(Language.L.COMMAND__PURGE__COMPLETE_COUNT, count);

    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public List<String> onTabComplete(SA sender, String label, String[] args) {
        List<String> possible = new ArrayList<>();

        if (args.length == 2) {
            possible.addAll(Arrays.stream(Table.values()).filter(t -> t.exists(plugin) && t.canPurge()).map(Table::getName).toList());
            possible.add("all");
        }
        if (args.length == 3) {
            possible.add("<time>");
        }

        return possible;
    }
}
