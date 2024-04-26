package dev.heliosares.auxprotect.velocity;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.exceptions.BusyException;

import java.net.InetSocketAddress;
import java.sql.SQLException;

public class APVListener implements Listener {
    private final AuxProtectVelocity plugin;

    public APVListener(AuxProtectVelocity plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChatEvent(ChatEvent e) {
        if (e.getSender() instanceof ProxiedPlayer player) {
            DbEntry entry = new DbEntry(AuxProtectVelocity.getLabel(player), e.isCommand() ? EntryAction.COMMAND : EntryAction.CHAT, false, e.getMessage().trim(), "");
            plugin.add(entry);
        }
    }

    @EventHandler
    public void onServerKickEvent(ServerKickEvent e) {
        plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getPlayer()), EntryAction.KICK, false, e.getKickedFrom().getName(), BaseComponent.toLegacyText(e.getKickReasonComponent())));
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
        plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getConnection().getUniqueId()), EntryAction.SESSION, true, e.isCancelled() ? "CANCELLED" : "", data));

        if (e.isCancelled()) {
            plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getConnection()), EntryAction.KICK, false, "", BaseComponent.toLegacyText(e.getCancelReasonComponents())));
            plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getConnection()), EntryAction.SESSION, false, "", ""));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerConnectedEvent(ServerConnectedEvent e) {
        plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getPlayer()), EntryAction.CONNECT, false, e.getServer().getInfo().getName(), ""));
    }

    @EventHandler
    public void onPlayerDisconnectEvent(PlayerDisconnectEvent e) {
        plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getPlayer()), EntryAction.SESSION, false, "", ""));
    }

}
