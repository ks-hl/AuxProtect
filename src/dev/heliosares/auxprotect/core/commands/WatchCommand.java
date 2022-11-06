package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.*;
import dev.heliosares.auxprotect.core.Parameters.Flag;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WatchCommand extends Command {

    public WatchCommand(IAuxProtect plugin) {
        super(plugin, "watch", APPermission.WATCH, "w");
    }

    private static ConcurrentLinkedQueue<DbEntry> queue = new ConcurrentLinkedQueue<>();
    static final List<WatchRecord> records;

    private static int nextid = 0;

    private static class WatchRecord {
        public final int id;
        public final SenderAdapter sender;
        public final Parameters params;
        public final String originalCommand;

        public WatchRecord(SenderAdapter sender, Parameters params, String originalCommand) {
            this.id = nextid++;
            this.sender = sender;
            this.params = params;
            this.originalCommand = originalCommand;
        }
    }

    static {
        records = new ArrayList<>();
    }

    public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new SyntaxException();
        }
        plugin.runAsync(() -> {
            if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
                if (args.length != 3) {
                    sender.sendLang(Language.L.INVALID_SYNTAX);
                    return;
                }
                int id = -1;
                try {
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {

                }
                if (id < 0) {
                    sender.sendLang(Language.L.INVALID_SYNTAX);
                    return;
                }
                Iterator<WatchRecord> it = records.iterator();
                int count = 0;
                while (it.hasNext()) {
                    WatchRecord record = it.next();
                    if (sender.getUniqueId().equals(record.sender.getUniqueId()) && record.id == id) {
                        count++;
                        it.remove();
                    }
                }
                if (count == 0) {
                    sender.sendLang(Language.L.WATCH_NONE);
                } else {
                    sender.sendLang(Language.L.WATCH_REMOVED, count);
                }
                return;
            } else if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
                Iterator<WatchRecord> it = records.iterator();
                int count = 0;
                while (it.hasNext()) {
                    WatchRecord record = it.next();
                    if (sender.getUniqueId().equals(record.sender.getUniqueId())) {
                        count++;
                        it.remove();
                    }
                }
                if (count == 0) {
                    sender.sendLang(Language.L.WATCH_NONE);
                } else {
                    sender.sendLang(Language.L.WATCH_REMOVED, count);
                }
                return;
            } else if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
                List<String> lines = new ArrayList<>();
                for (WatchRecord record : records) {
                    if (sender.getUniqueId().equals(record.sender.getUniqueId())) {
                        lines.add(record.id + ": " + record.originalCommand);
                    }
                }
                if (lines.isEmpty()) {
                    sender.sendLang(Language.L.WATCH_NONE);
                } else {
                    sender.sendLang(Language.L.WATCH_ING);
                    for (String line : lines) {
                        sender.sendMessageRaw(line);
                    }
                }
                return;
            }

            Parameters params = null;
            try {
                params = Parameters.parse(sender, args);
            } catch (Exception e) {
                sender.sendMessageRaw(e.getMessage());
                return;
            }
            String command = "/ap";
            for (String arg : args) {
                command += " " + arg;
            }
            WatchRecord record = new WatchRecord(sender, params, command);
            WatchCommand.records.add(record);
            sender.sendLang(Language.L.WATCH_NOW, record.id + ": " + command);
        });
    }

    public static void notify(DbEntry entry) {
        if (records.size() == 0) {
            return;
        }
        queue.add(entry);
    }

    public static void tick(IAuxProtect plugin) {
        if (queue.size() == 0) {
            return;
        }
        DbEntry entry = null;
        while ((entry = queue.poll()) != null) {
            HashSet<UUID> informed = new HashSet<>();
            for (WatchRecord record : records) {
                if (record.params.matches(entry)) {
                    if (informed.add(record.sender.getUniqueId())) {
                        Results.sendEntry(plugin, record.sender, entry, 0, true,
                                !record.params.getFlags().contains(Flag.HIDE_COORDS));
                    }
                }
            }
        }
    }

    @Override
    public boolean exists() {
        return plugin.getAPConfig().isPrivate();
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        return LookupCommand.onTabCompleteStatic(plugin, sender, label, args);
    }
}
