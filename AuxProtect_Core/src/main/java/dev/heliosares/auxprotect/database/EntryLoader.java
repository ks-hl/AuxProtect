package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.exceptions.BusyException;

import java.sql.SQLException;
import java.util.function.Predicate;

public record EntryLoader(Predicate<EntryData> applies, Loader loader) {

    @FunctionalInterface
    public interface Loader {
        DbEntry load(EntryData data) throws SQLException;
    }
}
