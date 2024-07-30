package dev.heliosares.auxprotect.spigot.commands;

import dev.heliosares.auxprotect.adapters.sender.SpigotSenderAdapter;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.core.commands.LookupCommand;
import dev.heliosares.auxprotect.database.ActivityResults;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.PosEntry;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.TransactionEntry;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.MoneySolver;
import dev.heliosares.auxprotect.utils.PlaybackSolver;
import dev.heliosares.auxprotect.utils.RetentionSolver;
import dev.heliosares.auxprotect.utils.XraySolver;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
        if (params_.hasFlag(Parameters.Flag.ACTIVITY)) {
            Results result = new ActivityResults(plugin, rs, sender, params_);
            result.showPage(result.getNumPages(4), 4);
            putResults(sender.getUniqueId(), result);
            return;
        } else if (params_.hasFlag(Parameters.Flag.XRAY)) {
            Set<Integer> users = new HashSet<>();
            for (DbEntry entry : rs) {
                users.add(entry.getUid());
            }
            if (users.size() == 1) {
                Iterator<DbEntry> it = rs.iterator();
                while (it.hasNext()) {
                    XrayEntry entry = (XrayEntry) it.next();
                    if (entry.getRating() < 0) {
                        it.remove();
                    }
                }
            }
            XraySolver.solve(plugin, rs).send(sender);
            return;
        } else if (params_.hasFlag(Parameters.Flag.MONEY) && sender.getPlatform().getLevel() == PlatformType.Level.SERVER) {
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
        } else if (params_.hasFlag(Parameters.Flag.INCREMENTAL_POS) && plugin.getPlatform().getLevel() == PlatformType.Level.SERVER) {
            List<PlaybackSolver.PosPoint> points = PlaybackSolver.getLocations(plugin, rs, 0);
            List<DbEntry> newResults = new ArrayList<>();
            for (PlaybackSolver.PosPoint point : points) {
                newResults.add(new PosEntry(point.time(), point.uid(), point.location()));
            }
            Collections.reverse(newResults);
            rs = newResults;
        } else if (params_.hasFlag(Parameters.Flag.PLAYBACK) && plugin.getPlatform().getLevel() == PlatformType.Level.SERVER) {
            List<PlaybackSolver.BlockAction> actions = null;
            try {
                Class.forName("net.coreprotect.CoreProtectAPI");
                CoreProtectAPI api = CoreProtect.getInstance().getAPI();
                List<String[]> lookup = api.performLookup(
                        (int) ((System.currentTimeMillis() - params_.getAfter()) / 1000L),
                        new ArrayList<>(params_.getUsers()),
                        null,
                        null,
                        null,
                        new ArrayList<>(List.of(0, 1)),
                        params_.getRadius().entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).max(Integer::compare).orElse(0),
                        new org.bukkit.Location(Bukkit.getWorld(plugin.getSqlManager().getWorld(params_.getWorldID())), params_.getX(), params_.getY(), params_.getZ()));
                if (lookup != null) {
                    List<CoreProtectAPI.ParseResult> results = lookup
                            .stream()
                            .map(api::parseResult)
                            .toList();
                    actions = results.stream().filter(r -> r.getTimestamp() < params_.getBefore())
                            .map(result -> new PlaybackSolver.BlockAction(result.getTimestamp(), result.getPlayer(), result.getX(), result.getY(), result.getZ(), result.getType(), result.getActionId() == 1))
                            .toList();
                }
            } catch (ClassNotFoundException ignored) {
            }
            new PlaybackSolver(plugin, sender, rs, params_.getAfter(), actions);
            return;
        } else if (params_.hasFlag(Parameters.Flag.RETENTION)) {
            RetentionSolver.showRetention(plugin, sender, rs, params_.getAfter(), params_.getBefore());
            return;
        }

        super.handleResults(params_, rs, sender);
    }
}
