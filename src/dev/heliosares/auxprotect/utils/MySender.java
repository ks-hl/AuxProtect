package dev.heliosares.auxprotect.utils;

import java.util.UUID;

import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class MySender {

	private final Object sender;
	private final boolean bungee;

	public MySender(org.bukkit.command.CommandSender sender) {
		this.sender = sender;
		this.bungee = false;
	}

	public MySender(net.md_5.bungee.api.CommandSender sender) {
		this.sender = sender;
		this.bungee = true;
	}

	public Object getSender() {
		return sender;
	}

	public boolean isBungee() {
		return bungee;
	}

	public void sendMessage(String message) {
		if (bungee) {
			((net.md_5.bungee.api.CommandSender) sender).sendMessage(TextComponent.fromLegacyText(message));
		} else {
			((org.bukkit.command.CommandSender) sender).sendMessage(message);
		}
	}

	public void sendMessage(BaseComponent... message) {
		if (bungee) {
			((net.md_5.bungee.api.CommandSender) sender).sendMessage(message);
		} else {
			((org.bukkit.command.CommandSender) sender).spigot().sendMessage(message);
		}
	}

	public boolean hasPermission(String node) {
		if (bungee) {
			return ((net.md_5.bungee.api.CommandSender) sender).hasPermission(node);
		} else {
			return ((org.bukkit.command.CommandSender) sender).hasPermission(node);
		}
	}

	public String getName() {
		if (bungee) {
			return ((net.md_5.bungee.api.CommandSender) sender).getName();
		} else {
			return ((org.bukkit.command.CommandSender) sender).getName();
		}
	}

	public UUID getUniqueId() {
		if (bungee) {
			if (sender instanceof ProxiedPlayer) {
				return ((ProxiedPlayer) sender).getUniqueId();
			}
		} else {
			if (sender instanceof Player) {
				return ((Player) sender).getUniqueId();
			}
		}
		return UUID.fromString("00000000-0000-0000-0000-000000000000");
	}
}
