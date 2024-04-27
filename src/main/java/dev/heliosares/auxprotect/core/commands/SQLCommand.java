package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.database.ResultMap;
import dev.heliosares.auxprotect.exceptions.BusyException;

import java.util.List;

public class SQLCommand <S, P extends IAuxProtect, SA extends SenderAdapter<S, P>> extends Command<S,P,SA>  {

    public SQLCommand(P plugin) {
        super(plugin, "sql", APPermission.ADMIN, true, "sqli", "sqlu");
    }

    @Override
    public boolean hasPermission(SA sender) {
        return super.hasPermission(sender) && sender.isConsole();
    }

    @Override
    public void onCommand(SA sender, String commandLabel, String[] args) {
        if (!plugin.getAPConfig().isConsoleSQL()) {
            sender.sendMessageRaw("&cThis command must be enabled in the config by setting 'ConsoleSQLCommands: true'");
            return;
        }
        StringBuilder msg = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            msg.append(args[i]).append(" ");
        }
        final String stmt = msg.toString().trim();
        sender.sendMessageRaw("&aRunning...");
        try {
            if (args[0].equalsIgnoreCase("sql")) {
                plugin.getSqlManager().execute(stmt, 3000L);
            } else if (args[0].equalsIgnoreCase("sqli")) {
                plugin.getSqlManager().execute(stmt, 30000L);
            } else {
                ResultMap results = plugin.getSqlManager().executeGetMap(stmt);
                StringBuilder line = new StringBuilder();
                for (String label : results.getLabels()) {
                    if (!line.isEmpty()) {
                        line.append(" | ");
                    }
                    line.append(label);
                }
                sender.sendMessageRaw(line.toString());
                for (ResultMap.Result result : results.getResults()) {
                    line = new StringBuilder();
                    for (Object part : result.getValues()) {
                        if (!line.isEmpty()) {
                            line.append(", ");
                        }
                        line.append(part);
                    }
                    sender.sendMessageRaw(line.toString());
                }
            }
        } catch (BusyException e) {
            sender.sendLang(Language.L.DATABASE_BUSY);
            return;
        } catch (Exception e) {
            sender.sendLang(Language.L.ERROR);
            plugin.warning("Error while executing '" + stmt + "'");
            plugin.print(e);
            return;
        }
        sender.sendMessageRaw("&aSQL statement executed successfully.");
    }

    @Override
    public List<String> onTabComplete(SA sender, String label, String[] args) {
        return null;
    }

    @Override
    public boolean exists() {
        return true;
    }

}
