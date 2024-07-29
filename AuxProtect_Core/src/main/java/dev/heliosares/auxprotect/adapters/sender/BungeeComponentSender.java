package dev.heliosares.auxprotect.adapters.sender;

import net.md_5.bungee.api.chat.BaseComponent;

public interface BungeeComponentSender {
    void sendMessage(BaseComponent... component);
}
