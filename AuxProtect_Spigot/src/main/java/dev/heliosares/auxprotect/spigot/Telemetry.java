package dev.heliosares.auxprotect.spigot;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.spigot.Metrics;

import java.util.HashMap;
import java.util.Map.Entry;

public class Telemetry {
    private static final HashMap<String, Boolean> hooks = new HashMap<>();

    public static void init(AuxProtectSpigot plugin, int pluginId) {
        Metrics metrics = new Metrics(plugin, pluginId);

        metrics.addCustomChart(new Metrics.SingleLineChart("entries", () -> plugin.getSqlManager().getCount()));

        metrics.addCustomChart(new Metrics.SimplePie("private", () -> {
            if (plugin.isPrivate()) {
                return "Private";
            }
            if (plugin.getAPConfig().isDonor()) {
                return "Donor";
            }
            return "Public";
        }));

        metrics.addCustomChart(new Metrics.SimplePie("mysql", () -> plugin.getSqlManager().isMySQL() ? "MySQL" : "SQLite"));

        metrics.addCustomChart(new Metrics.SimplePie("db-version", () -> plugin.getSqlManager().getVersion() + ""));

        metrics.addCustomChart(new Metrics.SimplePie("db-original-version", () -> plugin.getSqlManager().getOriginalVersion() + ""));

        metrics.addCustomChart(new Metrics.SimplePie("updatechecker", () -> plugin.getAPConfig().shouldCheckForUpdates() ? "Enabled" : "Disabled"));

        for (Entry<String, Boolean> entry : hooks.entrySet()) {
            metrics.addCustomChart(new Metrics.SimplePie("hook-" + entry.getKey(), () -> entry.getValue() ? "Enabled" : "Disabled"));
        }

    }

    public static void reportHook(IAuxProtect plugin, String name, boolean state) {
        if (state) {
            plugin.info(name + " hooked");
        } else {
            plugin.debug(name + " not hooked");
        }
        hooks.put(name.toLowerCase(), state);
    }
}
