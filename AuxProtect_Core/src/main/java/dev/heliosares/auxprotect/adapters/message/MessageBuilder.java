package dev.heliosares.auxprotect.adapters.message;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.exceptions.NotPlayerException;

import java.util.List;

public abstract class MessageBuilder {
    protected abstract void send(SenderAdapter<?, ?> to, List<GenericComponent> line) throws NotPlayerException;
}
