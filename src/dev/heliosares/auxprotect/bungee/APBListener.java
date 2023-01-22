package dev.heliosares.auxprotect.bungee;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.net.InetSocketAddress;
import java.sql.SQLException;

public class APBListener implements Listener {
    private final AuxProtectBungee plugin;

    public APBListener(AuxProtectBungee plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void chatEvent(ChatEvent e) {
        if (e.getSender() instanceof ProxiedPlayer player) {
            DbEntry entry = new DbEntry(AuxProtectBungee.getLabel(player), e.isCommand() ? EntryAction.COMMAND : EntryAction.CHAT, false, e.getMessage(), "");
            plugin.dbRunnable.add(entry);
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

        plugin.runAsync(() -> {
            try {
                plugin.getSqlManager().getUserManager().updateUsernameAndIP(e.getConnection().getUniqueId(), e.getConnection().getName(),
                        ip);
            } catch (SQLException ex) {
                plugin.print(ex);
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
