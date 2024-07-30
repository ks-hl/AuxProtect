package dev.heliosares.auxprotect.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.exceptions.BusyException;

import java.sql.SQLException;

public class APVListener {
    private final AuxProtectVelocity plugin;

    public APVListener(AuxProtectVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onChatEvent(PlayerChatEvent e) {
        plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getPlayer()), EntryAction.CHAT, false, e.getMessage().trim(), ""));
    }

    @Subscribe
    public void on(CommandExecuteEvent e) {
        plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getCommandSource()), EntryAction.COMMAND, false, e.getCommand().trim(), ""));
    }

    @Subscribe
    public void onServerKickEvent(KickedFromServerEvent e) {
        plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getPlayer()), EntryAction.KICK, false, e.getServer().getServerInfo().getName(), e.getServerKickReason().map(AuxProtectVelocity::toString).orElse("")));
        plugin.removeAPPlayer(e.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onLoginEvent(LoginEvent e) {
        String ip_ = e.getPlayer().getRemoteAddress().getAddress().toString();
        if (ip_.startsWith("/")) {
            ip_ = ip_.substring(1);
        }

        final String ip = ip_;

        plugin.runAsync(() -> {
            try {
                plugin.getSqlManager().getUserManager().updateUsernameAndIP(e.getPlayer().getUniqueId(), e.getPlayer().getUsername(), ip);
            } catch (BusyException ex) {
                plugin.warning("Database Busy: Unable to update username/ip for " + e.getPlayer().getUsername() + ", this may cause issues with lookups but will resolve when they relog and the database is not busy.");
            } catch (SQLException ex) {
                plugin.print(ex);
            }
        });
        String data = "";
        if (plugin.getAPConfig().isSessionLogIP()) data = "IP: " + ip;

        plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getPlayer().getUniqueId()), EntryAction.SESSION, true, e.getResult().isAllowed() ? "" : "CANCELLED", data));

        if (!e.getResult().isAllowed()) {
            plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getPlayer()), EntryAction.KICK, false, "", e.getResult().getReasonComponent().map(AuxProtectVelocity::toString).orElse("")));
            plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getPlayer()), EntryAction.SESSION, false, "", ""));
            plugin.removeAPPlayer(e.getPlayer().getUniqueId());
        }
    }

    @Subscribe(order = PostOrder.LATE)
    public void onServerConnectedEvent(ServerConnectedEvent e) {
        plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getPlayer()), EntryAction.CONNECT, false, e.getServer().getServerInfo().getName(), ""));
    }

    @Subscribe
    public void onPlayerDisconnectEvent(DisconnectEvent e) {
        plugin.add(new DbEntry(AuxProtectVelocity.getLabel(e.getPlayer()), EntryAction.SESSION, false, "", ""));
        plugin.removeAPPlayer(e.getPlayer().getUniqueId());
    }

}
