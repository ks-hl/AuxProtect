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
import net.md_5.bungee.api.event.ServerConnectedEvent;
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

	private void handlePM(ProxiedPlayer from, ProxiedPlayer to, String msg) {
		DbEntry entry = new DbEntry(AuxProtectBungee.getLabel(from), EntryAction.MSG, false,
				AuxProtectBungee.getLabel(to), msg);
		plugin.dbRunnable.add(entry);
	}

	@EventHandler
	public void chatEvent(ChatEvent e) {
		try {
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
						msg(player, e.getMessage().substring(e.getMessage().indexOf(" ") + 1).split(" "));
					} else if (e.getMessage().toLowerCase().startsWith("/r ")
							|| e.getMessage().toLowerCase().startsWith("/reply ")
							|| e.getMessage().toLowerCase().startsWith("/respond ")) {
						r(player, e.getMessage().substring(e.getMessage().indexOf(" ") + 1).split(" "));
					}
				} else if (Events.PMToggle.containsKey(player.getUniqueId())) {
					String message = e.getMessage();
					if (ProxyServer.getInstance().getPlayer((UUID) Events.PMToggle.get(player.getUniqueId())) != null) {
						ProxiedPlayer target = ProxyServer.getInstance()
								.getPlayer((UUID) Events.PMToggle.get(player.getUniqueId()));
						handlePM(player, target, message);
					}

				}
			}
		} catch (NoClassDefFoundError ignored) {
		}
	}

	public void msg(ProxiedPlayer player, String[] args) {
		if (args.length > 1) {
			String message = MultiChatUtil.getMessageFromArgs(args, 1);
			ProxiedPlayer target = ProxyServer.getInstance().getPlayer(args[0]);
			if (target != null) {

				handlePM(player, target, message);
			}
		}
	}

	public void r(ProxiedPlayer player, String[] args) {
		if (args.length >= 1) {
			String message = MultiChatUtil.getMessageFromArgs(args);
			if (MultiChat.lastmsg.containsKey(player.getUniqueId())) {
				ProxiedPlayer target = ProxyServer.getInstance()
						.getPlayer((UUID) MultiChat.lastmsg.get(player.getUniqueId()));
				if (target != null) {

					handlePM(player, target, message);
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
				plugin.getSqlManager().updateUsernameAndIP(e.getConnection().getUniqueId(), e.getConnection().getName(),
						ip);
			}
		});
		plugin.dbRunnable.add(new DbEntry(AuxProtectBungee.getLabel(e.getConnection().getUniqueId()),
				EntryAction.SESSION, true, "", "IP: " + ip));
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void serverConnectEvent(ServerConnectedEvent e) {
		plugin.dbRunnable.add(new DbEntry(AuxProtectBungee.getLabel(e.getPlayer()), EntryAction.CONNECT, false,
				e.getServer().getInfo().getName(), ""));
	}

	@EventHandler
	public void onDisconnect(PlayerDisconnectEvent e) {
		plugin.dbRunnable
				.add(new DbEntry(AuxProtectBungee.getLabel(e.getPlayer()), EntryAction.SESSION, false, "", ""));
	}

}
