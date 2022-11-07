package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import java.util.*;
import java.util.Map.Entry;

public class RetentionSolver {

    public static void showRetention(IAuxProtect plugin, SenderAdapter sender, ArrayList<DbEntry> entries,
                                     long startTime, long endTime) {
        long start = System.currentTimeMillis();
        Set<Integer> lookupUids = new HashSet<>();
        for (DbEntry entry : entries) {
            if (entry.getAction().id != 769) {
                continue;
            }
            lookupUids.add(entry.getUid());
        }

        HashMap<OfflinePlayer, Integer> playtimes = new HashMap<>();
        HashMap<Integer, Integer> thresholds = new HashMap<>();
        thresholds.put(1 * 60 * 20, 0);
        thresholds.put(5 * 60 * 20, 0);
        thresholds.put(10 * 60 * 20, 0);
        thresholds.put(30 * 60 * 20, 0);
        thresholds.put(60 * 60 * 20, 0);
        thresholds.put(3 * 60 * 60 * 20, 0);
        int onlinern = 0;
        for (int uid : lookupUids) {
            HashMap<Long, String> usernames = plugin.getSqlManager().getUserManager().getUsernamesFromUID(uid);
            boolean valid = true;
            for (long time : usernames.keySet()) {
                if (time < startTime) {
                    valid = false;
                    break;
                }
            }
            if (!valid) {
                continue;
            }
            OfflinePlayer player = Bukkit
                    .getOfflinePlayer(UUID.fromString(plugin.getSqlManager().getUserManager().getUUIDFromUID(uid).substring(1)));
            if (player == null) {
                sender.sendMessageRaw("§cPlayer not found. UID=" + uid);
                return;
            }
            int playtime = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            playtimes.put(player, playtime);
            for (int threshold : thresholds.keySet()) {
                if (playtime >= threshold) {
                    thresholds.put(threshold, thresholds.get(threshold) + 1);
                }
            }
            if (player.isOnline()) {
                onlinern++;
            }
        }
        for (Entry<Integer, Integer> entry : thresholds.entrySet()) {
            double val = (double) entry.getValue() / (double) playtimes.size() * 100;
            String time = "";
            int key = entry.getKey();
            key /= 60 * 20;
            if (key % 60 == 0) {
                time = (key / 60) + "h";
            } else {
                time = key + "m";
            }
            sender.sendMessageRaw(String.format("§7Above %s: §9%s §7(%d/%d)", time, Math.round(val) + "%",
                    entry.getValue(), playtimes.size()));
        }
        sender.sendMessageRaw(String.format("§7Online now: §9%s §7(%d/%d)",
                Math.round((double) onlinern / (double) playtimes.size() * 100) + "%", onlinern, playtimes.size()));
        sender.sendMessageRaw(String.format("§7(%sms)", "" + (System.currentTimeMillis() - start)));
    }
}