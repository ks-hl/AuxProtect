package dev.heliosares.auxprotect.bungee;

import java.net.InetSocketAddress;
import java.util.UUID;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import xyz.olivermartin.multichat.bungee.Events;
import xyz.olivermartin.multichat.bungee.MultiChat;
import xyz.olivermartin.multichat.bungee.MultiChatUtil;

public class APBListener implements Listener {
	private final AuxProtectBungee plugin;

	public APBListener(AuxProtectBungee plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void chatEvent(ChatEvent e) {
		if (e.getSender() instanceof ProxiedPlayer) {
			ProxiedPlayer player = (ProxiedPlayer) e.getSender();
			if (e.isCommand()) {
				DbEntry entry = new DbEntry(AuxProtectBungee.getLabel(player), EntryAction.COMMAND, false,
						e.getMessage(), "");
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
						DbEntry entry = new DbEntry(AuxProtectBungee.getLabel(player), EntryAction.MSG, false,
								AuxProtectBungee.getLabel(target), message);
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

				DbEntry entry = new DbEntry(AuxProtectBungee.getLabel(player), EntryAction.MSG, false,
						AuxProtectBungee.getLabel(target), message);
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

					DbEntry entry = new DbEntry(AuxProtectBungee.getLabel(player), EntryAction.MSG, false,
							AuxProtectBungee.getLabel(target), message);
					plugin.dbRunnable.add(entry);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void serverConnectEvent(LoginEvent e) {
		if (e.isCancelled()) {
			return;
		}
		String ip_ = ((InetSocketAddress) e.getConnection().getSocketAddress()).getAddress().toString();
		if (ip_.startsWith("/")) {
			ip_ = ip_.substring(1);
		}

		final String ip = ip_;

		plugin.runAsync(new Runnable() {

			@Override
			public void run() {
				plugin.getSqlManager().updateUsernameAndIP(e.getConnection().getUniqueId().toString(),
						e.getConnection().getName(), ip);
			}
		});
		plugin.dbRunnable.add(new DbEntry("$" + e.getConnection().getUniqueId().toString(), EntryAction.SESSION, true,
				"", "IP: " + ip));
	}

	@EventHandler
	public void onDisconnect(PlayerDisconnectEvent e) {
		plugin.dbRunnable
				.add(new DbEntry(AuxProtectBungee.getLabel(e.getPlayer()), EntryAction.SESSION, false, "", ""));
	}

}
