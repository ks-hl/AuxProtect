package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.database.ConnectionPool;
import dev.heliosares.auxprotect.database.ResultMap;

import java.util.List;

public class SQLCommand extends Command {

    public SQLCommand(IAuxProtect plugin) {
        super(plugin, "sql", APPermission.ADMIN, true, "sqli", "sqlu");
    }

    @Override
    public boolean hasPermission(SenderAdapter sender) {
        return super.hasPermission(sender) && sender.isConsole();
    }

    @Override
    public void onCommand(SenderAdapter sender, String commandLabel, String[] args) {
        StringBuilder msg = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            msg.append(args[i]).append(" ");
        }
        final String stmt = msg.toString().trim();
        sender.sendMessageRaw("§aRunning...");
        try {
            if (args[0].equalsIgnoreCase("sql")) {
                plugin.getSqlManager().execute(stmt, 3000L);
            } else if (args[0].equalsIgnoreCase("sqli")) {
                plugin.getSqlManager().executeWrite(stmt);
            } else {
                ResultMap results = plugin.getSqlManager().executeGetMap(stmt);
                StringBuilder line = new StringBuilder();
                for (String label : results.getLabels()) {
                    if (line.length() > 0) {
                        line.append(" | ");
                    }
                    line.append(label);
                }
                sender.sendMessageRaw(line.toString());
                for (ResultMap.Result result : results.getResults()) {
                    line = new StringBuilder();
                    for (Object part : result.getValues()) {
                        if (line.length() > 0) {
                            line.append(", ");
                        }
                        line.append(part);
                    }
                    sender.sendMessageRaw(line.toString());
                }
            }
        } catch (ConnectionPool.BusyException e) {
            sender.sendLang(Language.L.DATABASE_BUSY);
            return;
        } catch (Exception e) {
            sender.sendLang(Language.L.ERROR);
            plugin.warning("Error while executing '" + stmt + "'");
            plugin.print(e);
            return;
        }
        sender.sendMessageRaw("§aSQL statement executed successfully.");
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        return null;
    }

    @Override
    public boolean exists() {
        return true;
    }

}
