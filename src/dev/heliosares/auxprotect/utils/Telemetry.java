package dev.heliosares.auxprotect.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;

import dev.heliosares.auxprotect.AuxProtect;

public class Telemetry {
	private final Metrics metrics;

	public Telemetry(AuxProtect plugin, int pluginId) {
		metrics = new Metrics(plugin, pluginId);

		metrics.addCustomChart(new Metrics.MultiLineChart("players_and_servers", new Callable<Map<String, Integer>>() {
			@Override
			public Map<String, Integer> call() throws Exception {
				Map<String, Integer> valueMap = new HashMap<>();
				valueMap.put("servers", 1);
				valueMap.put("players", Bukkit.getOnlinePlayers().size());
				return valueMap;
			}
		}));

		metrics.addCustomChart(new Metrics.SingleLineChart("entries", new Callable<Integer>() {
			@Override
			public Integer call() throws Exception {
				return plugin.getSqlManager().count();
			}
		}));
	}
}
