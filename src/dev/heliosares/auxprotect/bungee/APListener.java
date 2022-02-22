package dev.heliosares.auxprotect.bungee;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLManager.LookupException;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import xyz.olivermartin.multichat.bungee.Events;
import xyz.olivermartin.multichat.bungee.MultiChat;
import xyz.olivermartin.multichat.bungee.MultiChatUtil;

public class APListener implements Listener {
	private final AuxProtectBungee plugin;

	public APListener(AuxProtectBungee plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void chatEvent(ChatEvent e) {
		if (e.getSender() instanceof ProxiedPlayer) {
			ProxiedPlayer player = (ProxiedPlayer) e.getSender();
			if (e.isCommand()) {
				DbEntry entry = new DbEntry(player.getName(), EntryAction.COMMAND, false, "$null", 0, 0, 0,
						e.getMessage(), player.getUniqueId().toString());
				plugin.dbRunnable.add(entry);
				if (e.getMessage().toLowerCase().startsWith("/msg ")
						|| e.getMessage().toLowerCase().startsWith("/message ")
						|| e.getMessage().toLowerCase().startsWith("/tell ")
						|| e.getMessage().toLowerCase().startsWith("/whisper ")
						|| e.getMessage().toLowerCase().startsWith("/chat ")) {
					msg(player, e.getMessage().substring(5).split(" "));
				} else if (e.getMessage().toLowerCase().startsWith("/r ")
						|| e.getMessage().toLowerCase().startsWith("/reply ")
						|| e.getMessage().toLowerCase().startsWith("/respond ")) {
					r(player, e.getMessage().substring(3).split(" "));
				}
			}
			if (Events.PMToggle.containsKey(player.getUniqueId())) {

				String message = e.getMessage();

				if (!e.isCommand()) {

					if (ProxyServer.getInstance().getPlayer((UUID) Events.PMToggle.get(player.getUniqueId())) != null) {

						ProxiedPlayer target = ProxyServer.getInstance()
								.getPlayer((UUID) Events.PMToggle.get(player.getUniqueId()));
						DbEntry entry = new DbEntry((player).getName(), EntryAction.MSG, false, "$null", 0, 0, 0,
								target.getName(), message);
						plugin.dbRunnable.add(entry);
					}

				}
			}
		}
	}

	public void msg(ProxiedPlayer player, String[] args) {
		if (args.length > 1) {
			String message = MultiChatUtil.getMessageFromArgs(args, 1);
			if (ProxyServer.getInstance().getPlayer(args[0]) != null) {
				ProxiedPlayer target = ProxyServer.getInstance().getPlayer(args[0]);

				DbEntry entry = new DbEntry((player).getName(), EntryAction.MSG, false, "$null", 0, 0, 0,
						target.getName(), message);
				plugin.dbRunnable.add(entry);
			}
		}
	}

	public void r(ProxiedPlayer player, String[] args) {
		if (args.length >= 1) {
			String message = MultiChatUtil.getMessageFromArgs(args);
			if (MultiChat.lastmsg.containsKey(player.getUniqueId())) {
				if (ProxyServer.getInstance().getPlayer((UUID) MultiChat.lastmsg.get(player.getUniqueId())) != null) {
					ProxiedPlayer target = ProxyServer.getInstance()
							.getPlayer((UUID) MultiChat.lastmsg.get(player.getUniqueId()));

					DbEntry entry = new DbEntry(player.getName(), EntryAction.MSG, false, "$null", 0, 0, 0,
							target.getName(), message);
					plugin.dbRunnable.add(entry);
				}
			}
		}
	}

	@EventHandler
	public void serverConnectEvent(ServerConnectEvent e) {
		plugin.getSqlManager().updateUsername(e.getPlayer().getUniqueId().toString(), e.getPlayer().getName());
		Runnable run = new Runnable() {

			@Override
			public void run() {
				HashMap<String, String> params = new HashMap<>();
				params.put("user", "$" + e.getPlayer().getUniqueId());
				params.put("action", "username");

				ArrayList<DbEntry> results = null;
				try {
					results = plugin.getSqlManager().lookup(params, null, false);
				} catch (LookupException e1) {
					plugin.warning(e1.toString());
					return;
				}
				if (results == null)
					return;
				String newestusername = "";
				long highestusername = 0;
				for (DbEntry entry : results) {
					if (entry.getAction() == EntryAction.USERNAME) {
						if (entry.getTime() > highestusername) {
							highestusername = entry.getTime();
							newestusername = entry.getTarget();
						}
					}
				}
				if (!e.getPlayer().getName().equals(newestusername)) {
					plugin.dbRunnable.add(new DbEntry("$"+e.getPlayer().getUniqueId().toString(), EntryAction.USERNAME, false,
							"", 0, 0, 0, e.getPlayer().getName(), ""));
				}
			}
		};
		plugin.getProxy().getScheduler().runAsync(plugin, run);
	}
}
