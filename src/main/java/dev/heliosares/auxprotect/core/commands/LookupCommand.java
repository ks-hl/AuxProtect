package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.*;
import dev.heliosares.auxprotect.core.Parameters.Flag;
import dev.heliosares.auxprotect.database.*;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.exceptions.ParseException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.*;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;

import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LookupCommand extends Command {

    static final HashMap<String, Results> results = new HashMap<>();

    public LookupCommand(IAuxProtect plugin) {
        super(plugin, "lookup", APPermission.LOOKUP, true, "l");
    }

    public static List<String> onTabCompleteStatic(IAuxProtect plugin, SenderAdapter sender, String label, String[] args) {
        List<String> possible = new ArrayList<>();
        String currentArg = args[args.length - 1];
        boolean lookup = args[0].equalsIgnoreCase("lookup") || args[0].equalsIgnoreCase("l");
        boolean watch = args[0].equalsIgnoreCase("watch") || args[0].equalsIgnoreCase("w");
        if (lookup && !APPermission.LOOKUP.hasPermission(sender)) {
            return null;
        }
        if (watch && !APPermission.WATCH.hasPermission(sender)) {
            return null;
        }
        if (args.length == 2) {
            if (lookup) {
                possible.add("next");
                possible.add("prev");
                possible.add("first");
                possible.add("last");
            }
            if (watch) {
                possible.add("remove");
                possible.add("clear");
                possible.add("list");
            }
        }

        possible.add("radius:");
        possible.add("time:");
        possible.add("target:");
        possible.add("action:");
        possible.add("world:");
        possible.add("user:");
        possible.add("data:");
        possible.add("before:");
        possible.add("after:");
        possible.add("group:");

        action_check:
        if (currentArg.startsWith("action:") || currentArg.startsWith("a:")) {
            Pattern pattern = Pattern.compile("([+-]?\\w+)[,:]");
            Matcher matcher = pattern.matcher(currentArg);

            if (!matcher.find()) break action_check;
            StringBuilder actionPrefix = new StringBuilder(matcher.group(1) + ":");

            Table table = null;
            while (matcher.find()) {
                final String name = matcher.group(1);
                EntryAction entryAction = EntryAction.getAction((name.startsWith("+") || name.startsWith("-")) ? name.substring(1) : name);
                if (entryAction == null) break action_check;
                if (table == null) table = entryAction.getTable();
                else if (entryAction.getTable() != table) break action_check;
                actionPrefix.append(name).append(",");
            }

            for (EntryAction eaction : EntryAction.values()) {
                if (eaction.exists() && eaction.isEnabled() && eaction.hasPermission(sender) && (table == null || eaction.getTable() == table)) {
                    String actString = eaction.toString().toLowerCase();
                    if (eaction.hasDual) {
                        possible.add(actionPrefix + "+" + actString);
                        possible.add(actionPrefix + "-" + actString);
                    }
                    possible.add(actionPrefix + actString);
                }
            }
        }
        if (currentArg.startsWith("user:") || currentArg.startsWith("u:") || currentArg.startsWith("target:")) {
            int cutIndex = 0;
            if (currentArg.contains(",")) {
                cutIndex = currentArg.lastIndexOf(",");
            } else {
                cutIndex = currentArg.indexOf(":");

            }
            String user = currentArg.substring(0, cutIndex + 1);

            possible.addAll(APCommand.allPlayers(plugin, true).stream().map(name -> user + name).toList());

            if (plugin.getPlatform() == PlatformType.SPIGOT) {
                for (org.bukkit.entity.EntityType et : org.bukkit.entity.EntityType.values()) {
                    possible.add(user + "#" + et.toString().toLowerCase());
                }
                if (currentArg.startsWith("target:")) {
                    for (org.bukkit.Material material : org.bukkit.Material.values()) {
                        possible.add("target:" + material.toString().toLowerCase());
                    }
                }
            }
            possible.add(user + "#env");
        }
        if (APPermission.ADMIN.hasPermission(sender)) {
            possible.add("db:");
            if (currentArg.startsWith("db:")) {
                for (Table table : Table.values()) {
                    possible.add("db:" + table.toString());
                }
            }
        }
        if (currentArg.matches("(t(ime)?|before|after):\\d+m?")) {
            possible.add(currentArg + "ms");
            possible.add(currentArg + "s");
            possible.add(currentArg + "m");
            possible.add(currentArg + "h");
            possible.add(currentArg + "d");
            possible.add(currentArg + "w");
        }
        if (currentArg.startsWith("world:")) {
            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                possible.add("world:" + world.getName());
            }
        }
        if (currentArg.startsWith("rat")) {
            possible.add("rating:");
            if (currentArg.matches("rating:-?")) {
                for (int i = -2; i <= 3; i++) {
                    possible.add("rating:" + i);
                }
            }
        }

        for (int i = 1; i < args.length - 1; i++) {
            String arg = args[i];
            if (!arg.contains(":"))
                continue;
            arg = arg.substring(0, arg.indexOf(":") + 1);
            possible.remove(arg);
        }

        if (currentArg.startsWith("#")) {
            for (Flag flag : Flag.values()) {
                if (flag.hasPermission(sender) && flag.isEnabled()) {
                    possible.add("#" + flag.toString().toLowerCase());
                }
            }
        }

        return possible;
    }

    @Override
    public void onCommand(SenderAdapter sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendLang(Language.L.INVALID_SYNTAX);
            return;
        }
        if (!plugin.getSqlManager().isConnected()) {
            sender.sendLang(Language.L.DATABASE_BUSY);
            return;
        }
        try {
            Parameters params_ = null;
            if (args.length == 2) {
                if (args[1].matches("\\d{1,19}g")) {
                    long hash = Long.parseLong(args[1].substring(0, args[1].length() - 1));
                    params_ = LookupManager.getParametersForGroup(hash);
                } else {
                    int page = -1;
                    int perpage = -1;
                    boolean isPageLookup = false;
                    boolean next = args[1].equalsIgnoreCase("next");
                    boolean prev = args[1].equalsIgnoreCase("prev");
                    boolean first = args[1].equalsIgnoreCase("first");
                    boolean last = args[1].equalsIgnoreCase("last");
                    if (!next && !prev && !first && !last) {
                        if (args[1].contains(":")) {
                            String[] split = args[1].split(":");
                            try {
                                page = Integer.parseInt(split[0]);
                                perpage = Integer.parseInt(split[1]);
                                isPageLookup = true;
                            } catch (NumberFormatException ignored) {
                            }
                        } else {
                            try {
                                page = Integer.parseInt(args[1]);
                                isPageLookup = true;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    if (isPageLookup || first || last || next || prev) {
                        Results result = null;
                        String uuid = sender.getUniqueId().toString();
                        if (results.containsKey(uuid)) {
                            result = results.get(uuid);
                        }
                        if (result == null) {
                            sender.sendLang(Language.L.COMMAND__LOOKUP__NO_RESULTS_SELECTED);
                            return;
                        }
                        if (perpage == -1) {
                            perpage = result.perPage;
                        }
                        if (first) {
                            page = 1;
                        } else if (last) {
                            page = result.getNumPages(result.perPage);
                        } else if (next) {
                            page = result.prevPage + 1;
                        } else if (prev) {
                            page = result.prevPage - 1;
                        }
                        if (perpage > 0) {
                            if (perpage > 100) {
                                perpage = 100;
                            }
                            result.showPage(page, perpage);
                        } else {
                            result.showPage(page);
                        }
                        return;
                    }
                }
            }
            if (params_ == null) params_ = Parameters.parse(sender, args);
            final Parameters params = params_;


            sender.sendLang(Language.L.COMMAND__LOOKUP__LOOKING);

            int count = plugin.getSqlManager().getLookupManager().count(params_);
            if (params_.hasFlag(Flag.COUNT_ONLY)) {
                sender.sendLang(Language.L.COMMAND__LOOKUP__COUNT, count);
                return;
            }
            if (count == 0) {
                sender.sendLang(Language.L.COMMAND__LOOKUP__NORESULTS);
                return;
            }
            if (count > SQLManager.MAX_LOOKUP_SIZE) {
                sender.sendLang(Language.L.COMMAND__LOOKUP__TOOMANY, count, SQLManager.MAX_LOOKUP_SIZE);
                return;
            }

            List<DbEntry> rs = plugin.getSqlManager().getLookupManager().lookup(params_);
            if (plugin.getAPConfig().isDemoMode() && !sender.isConsole()) {
                rs.removeIf(entry -> {
                    try {
                        return entry.getUserUUID().startsWith("$") && !entry.getUserUUID().equals("$" + sender.getUniqueId());
                    } catch (SQLException ignored) {
                        return true;
                    }
                });
            }
            if (rs == null || rs.size() == 0) {
                sender.sendLang(Language.L.COMMAND__LOOKUP__NORESULTS);
                return;
            }
            if (params_.hasFlag(Flag.COUNT)) {
                sender.sendLang(Language.L.COMMAND__LOOKUP__COUNT, rs.size());
                double totalMoney = 0;
                double totalExp = 0;
                int dropcount = 0;
                int pickupcount = 0;
                for (DbEntry entry : rs) {
                    if (entry.getAction().equals(EntryAction.SHOP)) {
                        String[] parts = entry.getData().split(", ");
                        if (parts.length >= 3) {
                            try {
                                String valueStr = parts[1];
                                double value = -1;
                                if (!valueStr.contains(" each")
                                        || entry.getTime() < 1648304226492L) { /* Fix for malformed entries */
                                    valueStr = valueStr.replaceAll(" each", "");
                                    value = Double.parseDouble(valueStr.substring(1));
                                } else {
                                    double each = Double.parseDouble(valueStr.split(" ")[0].substring(1));
                                    int qty = Integer.parseInt(parts[2].split(" ")[1]);
                                    value = each * qty;
                                }
                                if (value > 0) {
                                    if (entry.getState()) {
                                        value *= -1;
                                    }
                                    totalMoney += value;
                                }
                            } catch (Exception e) {
                                if (plugin.getAPConfig().getDebug() > 0) {
                                    plugin.print(e);
                                }
                            }
                        }
                    } else if (entry.getAction().equals(EntryAction.JOBS)) {
                        char type = entry.getData().charAt(0);
                        double value = Double.parseDouble(entry.getData().substring(1));
                        if (type == 'x') {
                            totalExp += value;
                        } else if (type == '$') {
                            totalMoney += value;
                        }
                    } else if (entry.getAction().equals(EntryAction.AUCTIONBUY)) {
                        String[] parts = entry.getData().split(" ");
                        try {
                            double each = Double.parseDouble(parts[parts.length - 1].substring(1));
                            totalMoney += each;
                        } catch (Exception ignored) {
                        }
                    } else if (entry.getAction().equals(EntryAction.DROP)
                            || entry.getAction().equals(EntryAction.PICKUP)) {
                        int quantity = -1;
                        try {
                            quantity = Integer.parseInt(entry.getData().substring(1));
                        } catch (Exception ignored) {
                        }
                        if (quantity > 0) {
                            if (entry.getAction().equals(EntryAction.DROP)) {
                                dropcount += quantity;
                            } else {
                                pickupcount += quantity;
                            }
                        }
                    }
                }
                if (totalMoney != 0 && plugin.getPlatform() == PlatformType.SPIGOT) {
                    boolean negative = totalMoney < 0;
                    totalMoney = Math.abs(totalMoney);
                    sender.sendMessageRaw("&fTotal Money: &9" + (negative ? "-" : "") + ((AuxProtectSpigot) plugin).formatMoney(totalMoney));
                }
                if (totalExp != 0) {
                    sender.sendMessageRaw("&fTotal Experience: &9" + Math.round(totalExp * 100f) / 100f);
                }
                String msg = "";
                if (pickupcount > 0) {
                    msg += "&fPicked up: &9" + pickupcount + "&7, ";
                }
                if (dropcount > 0) {
                    msg += "&fDropped: &9" + dropcount + "&7, ";
                }
                if (pickupcount > 0 && dropcount > 0) {
                    msg += "&fNet: &9" + (pickupcount - dropcount);
                }
                if (msg.length() > 0) {
                    sender.sendMessageRaw(msg);
                }
                return;
            } else if (params_.hasFlag(Flag.PLAYTIME)) {
                Set<String> users = params_.getUsers();
                if (users.size() == 0) {
                    sender.sendLang(Language.L.COMMAND__LOOKUP__PLAYTIME__NOUSER);
                    return;
                }
                if (users.size() > 1) {
                    sender.sendLang(Language.L.COMMAND__LOOKUP__PLAYTIME__TOOMANYUSERS);
                    return;
                }
                String name = users.stream().findAny().orElse(null);
                sender.sendMessage(PlayTimeSolver.solvePlaytime(rs,
                        params_.getAfter(),
                        params_.getBefore() == Long.MAX_VALUE ? System.currentTimeMillis() : params_.getBefore(),
                        name,
                        plugin.getSenderAdapter(name) != null));
                return;
            } else if (params_.hasFlag(Flag.ACTIVITY)) {
                String uuid = sender.getUniqueId().toString();
                Results result = new ActivityResults(plugin, rs, sender, params_);
                result.showPage(result.getNumPages(4), 4);
                results.put(uuid, result);
                return;
            } else if (params_.hasFlag(Flag.XRAY)) {
                Set<Integer> users = new HashSet<>();
                for (DbEntry entry : rs) {
                    users.add(entry.getUid());
                }
                if (users.size() > 1) {
                    sender.sendMessage(XraySolver.solve(rs));
                    return;
                }
                Iterator<DbEntry> it = rs.iterator();
                while (it.hasNext()) {
                    XrayEntry entry = (XrayEntry) it.next();
                    if (entry.getRating() < 0) {
                        it.remove();
                    }
                }
            } else if (params_.hasFlag(Flag.MONEY) && sender.getPlatform() == PlatformType.SPIGOT) {
                Set<String> users = params_.getUsers();
                if (users.size() == 0) {
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
            } else if (params_.hasFlag(Flag.RETENTION)) {
                RetentionSolver.showRetention(plugin, sender, rs, params_.getAfter(), params_.getBefore());
                return;
            } else if (params_.hasFlag(Flag.INCREMENTAL_POS) && plugin.getPlatform() == PlatformType.SPIGOT) {
                List<PlaybackSolver.PosPoint> points = PlaybackSolver.getLocations(plugin, rs, 0);
                List<DbEntry> newResults = new ArrayList<>();
                for (PlaybackSolver.PosPoint point : points) {
                    newResults.add(new PosEntry(point.time(), point.uid(), point.location()));
                }
                Collections.reverse(newResults);
                rs = newResults;
            } else if (params_.hasFlag(Flag.PLAYBACK) && plugin.getPlatform() == PlatformType.SPIGOT) {
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
                            new org.bukkit.Location(Bukkit.getWorld(plugin.getSqlManager().getWorld(params.getWorldID())), params.getX(), params.getY(), params.getZ()));
                    if (lookup != null) {
                        List<CoreProtectAPI.ParseResult> results = lookup
                                .stream()
                                .map(api::parseResult)
                                .toList();
                        actions = results.stream().filter(r -> r.getTimestamp() < params.getBefore())
                                .map(result -> new PlaybackSolver.BlockAction(result.getTimestamp(), result.getPlayer(), result.getX(), result.getY(), result.getZ(), result.getType(), result.getActionId() == 1))
                                .toList();
                    }
                } catch (ClassNotFoundException ignored) {
                }
                new PlaybackSolver(plugin, sender, rs, params_.getAfter(), actions);
                return;
            }
            String uuid = sender.getUniqueId().toString();
            Results result = new Results(plugin, rs, sender, params_);
            result.showPage(1, 4);
            results.put(uuid, result);

            if (params_.hasFlag(Flag.XRAY)) {
                sender.sendMessage(XraySolver.solve(rs));
            }
        } catch (LookupException | ParseException e) {
            sender.sendMessageRaw(e.getMessage());
        } catch (Exception e) {
            sender.sendLang(Language.L.ERROR);
            plugin.warning("Error during lookup:");
            plugin.print(e);
        }
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        return onTabCompleteStatic(plugin, sender, label, args);
    }
}
