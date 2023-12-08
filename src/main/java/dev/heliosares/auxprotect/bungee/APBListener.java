package dev.heliosares.auxprotect.bungee;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.exceptions.BusyException;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
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
    public void onChatEvent(ChatEvent e) {
        if (e.getSender() instanceof ProxiedPlayer player) {
            DbEntry entry = new DbEntry(AuxProtectBungee.getLabel(player), e.isCommand() ? EntryAction.COMMAND : EntryAction.CHAT, false, e.getMessage().trim(), "");
            plugin.add(entry);
        }
    }

    @EventHandler
    public void onServerKickEvent(ServerKickEvent e) {
        plugin.add(new DbEntry(AuxProtectBungee.getLabel(e.getPlayer()), EntryAction.KICK, false, e.getKickedFrom().getName(), BaseComponent.toLegacyText(e.getKickReasonComponent())));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLoginEvent(LoginEvent e) {
        String ip_ = ((InetSocketAddress) e.getConnection().getSocketAddress()).getAddress().toString();
        if (ip_.startsWith("/")) {
            ip_ = ip_.substring(1);
        }

        final String ip = ip_;

        plugin.runAsync(() -> {
            try {
                plugin.getSqlManager().getUserManager().updateUsernameAndIP(e.getConnection().getUniqueId(), e.getConnection().getName(), ip);
            } catch (BusyException ex) {
                plugin.warning("Database Busy: Unable to update username/ip for " + e.getConnection().getName() + ", this may cause issues with lookups but will resolve when they relog and the database is not busy.");
            } catch (SQLException ex) {
                plugin.print(ex);
            }
        });
        String data = "";
        if (plugin.getAPConfig().isSessionLogIP()) data = "IP: " + ip;
        plugin.add(new DbEntry(AuxProtectBungee.getLabel(e.getConnection().getUniqueId()), EntryAction.SESSION, true, e.isCancelled() ? "CANCELLED" : "", data));

        if (e.isCancelled()) {
            plugin.add(new DbEntry(AuxProtectBungee.getLabel(e.getConnection()), EntryAction.KICK, false, "", BaseComponent.toLegacyText(e.getCancelReasonComponents())));
            plugin.add(new DbEntry(AuxProtectBungee.getLabel(e.getConnection()), EntryAction.SESSION, false, "", ""));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerConnectedEvent(ServerConnectedEvent e) {
        plugin.add(new DbEntry(AuxProtectBungee.getLabel(e.getPlayer()), EntryAction.CONNECT, false, e.getServer().getInfo().getName(), ""));
    }

    @EventHandler
    public void onPlayerDisconnectEvent(PlayerDisconnectEvent e) {
        plugin.add(new DbEntry(AuxProtectBungee.getLabel(e.getPlayer()), EntryAction.SESSION, false, "", ""));
    }

}
