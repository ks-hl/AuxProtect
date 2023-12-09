package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.adapters.sender.SpigotSenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.database.CSLogResults;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.exceptions.ParseException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;

public class CSLogsCommand implements CommandExecutor {

    private final AuxProtectSpigot plugin;
    private static final HashMap<UUID, Results> results = new HashMap<>();

    public CSLogsCommand(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@Nonnull CommandSender sender, @Nonnull Command command, @Nonnull String commandLabel, @Nonnull String[] args) {
        SenderAdapter senderAdapter = new SpigotSenderAdapter(plugin, sender);
        if (!APPermission.CSLOGS.hasPermission(senderAdapter)) {
            senderAdapter.sendLang(Language.L.ACTION_DISABLED);
            return true;
        }
        if (!EntryAction.SHOP_CS.isEnabled()) {
            senderAdapter.sendLang(Language.L.ACTION_DISABLED);
            return true;
        }
        plugin.runAsync(() -> {
            try {
                if (args.length == 1) {
                    int page = -1;
                    boolean next = args[0].equalsIgnoreCase("next");
                    boolean prev = args[0].equalsIgnoreCase("prev");
                    boolean first = args[0].equalsIgnoreCase("first");
                    boolean last = args[0].equalsIgnoreCase("last");
                    if (first || last || next || prev) {
                        Results result = results.get(senderAdapter.getUniqueId());
                        if (result == null) {
                            senderAdapter.sendLang(Language.L.COMMAND__LOOKUP__NO_RESULTS_SELECTED);
                            return;
                        }
                        if (first) {
                            page = 1;
                        } else if (last) {
                            page = result.getNumPages(result.getPerPage());
                        } else if (next) {
                            page = result.getCurrentPage() + 1;
                        } else {
                            page = result.getCurrentPage() - 1;
                        }
                        result.showPage(page);
                        return;
                    }
                } else if (args.length == 0) {
                    senderAdapter.sendLang(Language.L.COMMAND__LOOKUP__LOOKING);
                    Parameters params = new Parameters(Table.AUXPROTECT_TRANSACTIONS) //
                            .addAction(null, EntryAction.SHOP_CS, 0) //
                            .flag(Parameters.Flag.ONLY_USER2) //
                            .flag(Parameters.Flag.HIDE_DATA) //
                            .user(senderAdapter.getUniqueId(), false) //
                            .time(System.currentTimeMillis() - 14L * 24L * 3600000L, Long.MAX_VALUE);
                    var entries = plugin.getSqlManager().getLookupManager().lookup(params);
                    if (entries.isEmpty()) {
                        senderAdapter.sendLang(Language.L.COMMAND__LOOKUP__NORESULTS);
                        return;
                    }
                    for (DbEntry entry : entries) {
                        // This isn't really necessary and should never be possible to reach this, it's just a safety.
                        if (entry.getAction() != EntryAction.SHOP_CS) {
                            senderAdapter.sendLang(Language.L.ERROR);
                            plugin.warning("Attempted to send an action other than SHOP_CS during a /cslogs lookup: " + entry);
                            return;
                        }
                    }
                    Results result = new CSLogResults(plugin, entries, senderAdapter, params);
                    result.showPage(1, 4);
                    results.put(senderAdapter.getUniqueId(), result);

                    return;
                }
            } catch (ParseException | LookupException e) {
                senderAdapter.sendLang(e.getLang());
                return;
            } catch (SQLException e) {
                senderAdapter.sendLang(Language.L.ERROR);
                plugin.print(e);
                return;
            } catch (BusyException e) {
                senderAdapter.sendLang(Language.L.DATABASE_BUSY);
                return;
            }
            senderAdapter.sendLang(Language.L.INVALID_SYNTAX);
        });
        return true;
    }
}
