package dev.heliosares.auxprotect.utils;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import dev.heliosares.auxprotect.AuxProtect;

public class Telemetry {
	private final Metrics metrics;
	private static final HashMap<String, Boolean> hooks = new HashMap<>();

	public static void reportHook(String name, boolean state) {
		hooks.put(name.toLowerCase(), state);
	}

	public Telemetry(AuxProtect plugin, int pluginId) {
		metrics = new Metrics(plugin, pluginId);

		metrics.addCustomChart(new Metrics.SingleLineChart("entries", new Callable<Integer>() {
			@Override
			public Integer call() throws Exception {
				int count = plugin.getSqlManager().getCount();
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

		metrics.addCustomChart(new Metrics.SimplePie("db-version", new Callable<String>() {
			@Override
			public String call() throws Exception {
				return plugin.getSqlManager().getVersion() + "";
			}
		}));

		metrics.addCustomChart(new Metrics.SimplePie("db-original-version", new Callable<String>() {
			@Override
			public String call() throws Exception {
				return plugin.getSqlManager().getOriginalVersion() + "";
			}
		}));

		metrics.addCustomChart(new Metrics.SimplePie("updatechecker", new Callable<String>() {
			@Override
			public String call() throws Exception {
				return plugin.getAPConfig().checkforupdates ? "Enabled" : "Disabled";
			}
		}));

		for (Entry<String, Boolean> entry : hooks.entrySet()) {
			metrics.addCustomChart(new Metrics.SimplePie("hook-" + entry.getKey(), new Callable<String>() {
				@Override
				public String call() throws Exception {
					return entry.getValue() ? "Enabled" : "Disabled";
				}
			}));
		}
	}
}
