package dev.heliosares.auxprotect.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import dev.heliosares.auxprotect.AuxProtect;

public class Telemetry {
	private final Metrics metrics;
	private static final Map<String, Integer> hooks = new HashMap<>();

	public static void reportHook(String name) {
		hooks.put(name, 1);
	}

	public Telemetry(AuxProtect plugin, int pluginId) {
		metrics = new Metrics(plugin, pluginId);

		metrics.addCustomChart(new Metrics.SingleLineChart("entries", new Callable<Integer>() {
			@Override
			public Integer call() throws Exception {
				int count = plugin.getSqlManager().getCount();
				plugin.debug("Reporting usage to bStats. " + count + " entries.");
				return count;
			}
		}));

		metrics.addCustomChart(new Metrics.SimplePie("private", new Callable<String>() {
			@Override
			public String call() throws Exception {
				return plugin.config.isPrivate() ? "Private" : "Public";
			}
		}));

		metrics.addCustomChart(new Metrics.SimplePie("mysql", new Callable<String>() {
			@Override
			public String call() throws Exception {
				return plugin.getSqlManager().isMySQL() ? "MySQL" : "SQLite";
			}
		}));

		metrics.addCustomChart(new Metrics.SimpleBarChart("hooks", new Callable<Map<String, Integer>>() {
			@Override
			public Map<String, Integer> call() throws Exception {
				return hooks;
			}
		}));
	}
}
