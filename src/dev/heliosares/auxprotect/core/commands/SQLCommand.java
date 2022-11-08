package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.database.ConnectionPool;
import dev.heliosares.auxprotect.exceptions.CommandException;

import java.util.List;

public class SQLCommand extends Command {

    public SQLCommand(IAuxProtect plugin) {
        super(plugin, "sql", APPermission.ADMIN, "sqli", "sqlu");
    }

    @Override
    public boolean hasPermission(SenderAdapter sender) {
        return super.hasPermission(sender) && sender.isConsole();
    }

    @Override
    public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
        String msg = "";
        for (int i = 1; i < args.length; i++) {
            msg += args[i] + " ";
        }
        final String stmt = msg.trim();
        plugin.runAsync(() -> {
            sender.sendMessageRaw("§aRunning...");
            try {
                if (args[0].equalsIgnoreCase("sql")) {
                    plugin.getSqlManager().execute(stmt, false);
                } else if (args[0].equalsIgnoreCase("sqli")) {
                    plugin.getSqlManager().executeWrite(stmt);
                } else {
                    List<List<String>> results = plugin.getSqlManager().executeGet(stmt);
                    if (results != null) {
                        for (List<String> result : results) {
                            String line = "";
                            for (String part : result) {
                                if (line.length() > 0) {
                                    line += ", ";
                                }
                                line += part;
                            }
                            sender.sendMessageRaw(line);
                        }
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

        });
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
