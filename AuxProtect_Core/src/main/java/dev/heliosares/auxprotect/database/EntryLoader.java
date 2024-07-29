package dev.heliosares.auxprotect.database;

import javax.annotation.Nullable;

@FunctionalInterface
public interface EntryLoader {
    @Nullable
    DbEntry load(EntryData entryData);
}
