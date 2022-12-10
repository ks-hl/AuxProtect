package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.database.ConnectionPool;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.utils.HasteBinAPI;
import dev.heliosares.auxprotect.utils.StackUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DumpCommand extends Command {

    public DumpCommand(IAuxProtect plugin) {
        super(plugin, "dump", APPermission.ADMIN, "stats");
    }

    public static String dump(IAuxProtect plugin, boolean verbose, boolean chat, boolean file, boolean config,
                              boolean stats) throws Exception {
        StringBuilder trace = new StringBuilder();
        if (!stats) {
            trace.append("Generated: ").append(LocalDateTime.now().format(Results.dateFormatter)).append(" (").append(System.currentTimeMillis()).append(")\n");
        }
        trace.append("Plugin version: ").append(plugin.getPluginVersion()).append("\n");
        trace.append("Key: ");
        if (plugin.getAPConfig().isPrivate()) {
            trace.append("private.").append(plugin.getAPConfig().getKeyHolder());
        } else if (plugin.getAPConfig().isDonor()) {
            trace.append("donor.").append(plugin.getAPConfig().getKeyHolder());
        } else {
            trace.append("none");
        }
        trace.append("\n");
        trace.append("Language: ").append(Language.getLocale()).append("\n");
        trace.append("DB version: ").append(plugin.getSqlManager().getVersion()).append("\n");
        trace.append("Original DB version: ").append(plugin.getSqlManager().getOriginalVersion()).append("\n");
        if (!stats) {
            trace.append("Server Version: ").append(plugin.getPlatformVersion()).append("\n");
            trace.append("Java: ").append(Runtime.version().toString()).append("\n");
        }
        trace.append("Queued: ").append(plugin.queueSize()).append("\n");
        if (!stats) {
            trace.append("Pool:\n");
            trace.append("  Size: ").append(plugin.getSqlManager().getConnectionPoolSize()).append("\n");
            trace.append("  Alive: ").append(ConnectionPool.getNumAlive()).append("\n");
            trace.append("  Born: ").append(ConnectionPool.getNumBorn()).append("\n");
        }
        long[] read = ConnectionPool.calculateReadTimes();
        if (read != null) {
            trace.append("Read:\n");
            trace.append("  Average Time: ").append(Math.round((double) read[1] / read[2] * 100.0) / 100.0).append("ms\n");
            trace.append("  Duty: ").append(Math.round((double) read[1] / read[0] * 10000.0) / 100.0).append("%\n");
            if (verbose) {
                trace.append("  Across: ").append(read[0]).append("ms\n");
                trace.append("  Count: ").append(read[2]).append("\n");
            }
        }
        long[] write = ConnectionPool.calculateWriteTimes();
        if (write != null) {
            trace.append("Write:\n");
            trace.append("  Average Time: ").append(Math.round((double) write[1] / write[2] * 100.0) / 100.0).append("ms\n");
            trace.append("  Duty: ").append(Math.round((double) write[1] / write[0] * 10000.0) / 100.0).append("%\n");
            if (verbose) {
                trace.append("  Across: ").append(write[0]).append("ms\n");
                trace.append("  Count: ").append(write[2]).append("\n");
                long writeCheckout = plugin.getSqlManager().getWriteCheckOutTime();
                StackTraceElement[] heldBy = plugin.getSqlManager().getWhoHasWriteConnection();
                if (writeCheckout > 0 && heldBy != null) {
                    trace.append("  Write Held: ").append(System.currentTimeMillis() - writeCheckout).append("ms\n");
                    trace.append("  Held by: ").append(StackUtil.format(heldBy, 0));
                } else {
                    trace.append("  Not Held");
                }
                trace.append("\n");
            }
        }
        if (!stats) {
            trace.append("Database type: ").append(plugin.getSqlManager().isMySQL() ? "mysql" : "sqlite").append("\n");
            if (!plugin.getSqlManager().isMySQL()) {
                boolean size = false;
                try {
                    File sqlitefile = new File(plugin.getDataFolder(), "database/auxprotect.db");
                    if (sqlitefile.exists()) {
                        trace.append("File size: ").append(Files.size(sqlitefile.toPath()) / 1024 / 1024).append("MB\n");
                        size = true;
                    }
                } catch (Exception ignored) {
                }
                if (!size) {
                    trace.append("*No file\n");
                }
            }
        }
        trace.append("Row counts: ").append(plugin.getSqlManager().getCount()).append(" total\n");
        if (verbose) {
            ArrayList<String[]> counts = new ArrayList<>();
            int widest = 0;
            for (Table table : Table.values()) {
                String[] arr;
                try {
                    arr = new String[]{table.toString(), String.valueOf(plugin.getSqlManager().count(table))};
                } catch (SQLException e) {
                    arr = new String[]{table.toString(), "ERROR"};
                }
                int width = arr[0].length() + arr[1].length();
                if (width > widest) {
                    widest = width;
                }
                counts.add(arr);
            }
            for (String[] arr : counts) {
                StringBuilder pad = new StringBuilder();
                int width = arr[0].length() + arr[1].length();
                pad.append(".".repeat(Math.max(0, widest - width + 3)));
                trace.append("  ").append(arr[0]).append(pad).append(arr[1]).append("\n");
            }
        }
        if ((chat && !verbose) || stats) {
            return trace.toString();
        }

        trace.append("\n");

        if (config) {
            try {
                trace.append("config.yml:");
                trace.append(dumpContents(plugin));
            } catch (Exception e) {
                trace.append("Error reading config.yml\n");
            }
        }

        trace.append("\n");

        trace.append("Error Log:\n").append(plugin.getStackLog()).append("\n\n");

        if (verbose) {
            trace.append("Thread Trace:\n");
            trace.append(StackUtil.dumpThreadStack());
            trace.append("\n\n");
        }

        if (!file) {
            try {
                return HasteBinAPI.post(trace.toString());
            } catch (Exception e) {
                plugin.warning("Failed to upload trace, writing to file...");
                plugin.print(e);
            }
        }
        File dumpdir = new File(plugin.getDataFolder(), "dump");
        File dump = new File(dumpdir, "dump-" + System.currentTimeMillis() + ".txt");
        dumpdir.mkdirs();
        BufferedWriter writer = new BufferedWriter(new FileWriter(dump));
        writer.write(trace.toString());
        writer.close();
        return dump.getAbsolutePath();
    }

    private static String dumpContents(IAuxProtect plugin) {
        return dumpContents(plugin, "", 0);
    }

    private static String dumpContents(IAuxProtect plugin, String parent, int depth) {
        StringBuilder build = new StringBuilder();
        if (parent.length() > 0) {
            build.append(" ".repeat(Math.max(0, depth)));
            build.append(parent.substring(parent.lastIndexOf(".") + 1)).append(": ");
        }
        if (parent.toLowerCase().contains("mysql") || parent.toLowerCase().contains("passw")) {
            build.append("REDACTED\n");
            return build.toString();
        }
        if (plugin.getAPConfig().getConfig().isSection(parent)) {
            build.append("\n");
            for (String key : plugin.getAPConfig().getConfig().getKeys(parent, false)) {
                if (key.length() == 0) {
                    continue;
                }
                build.append(dumpContents(plugin, parent + (parent.length() > 0 ? "." : "") + key, depth + 2));
            }
        } else {
            build.append(plugin.getAPConfig().getConfig().get(parent)).append("\n");
        }
        return build.toString();
    }

    @Override
    public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
        sender.sendMessageRaw("§aBuilding trace...");
        plugin.runAsync(() -> {
            boolean verbose = false;
            boolean chat = false;
            boolean file = false;
            boolean config = false;
            boolean stats = args[0].equalsIgnoreCase("stats");
            if (!stats) {
                for (int i = 1; i < args.length; i++) {
                    switch (args[i].toLowerCase()) {
                        case "chat" -> chat = true;
                        case "verbose" -> verbose = true;
                        case "file" -> file = true;
                        case "config" -> config = true;
                    }
                }
            }
            try {
                sender.sendMessageRaw("§a" + dump(plugin, verbose, chat, file, config, stats));
            } catch (Exception e) {
                plugin.print(e);
                sender.sendLang(Language.L.ERROR);
            }
            if (config) {
                sender.sendMessageRaw(
                        "§cWARNING! §eThis contains the contents of config.yml. Please ensure all §cMySQL passwords §ewere properly removed before sharing.");
            }
        });
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        List<String> out = new ArrayList<>();
        out.add("verbose");
        out.add("chat");
        out.add("file");
        out.add("config");
        return out;
    }
}
