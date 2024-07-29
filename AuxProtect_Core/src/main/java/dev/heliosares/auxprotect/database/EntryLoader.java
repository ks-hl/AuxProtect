package dev.heliosares.auxprotect.database;

import java.util.function.Function;
import java.util.function.Predicate;

public record EntryLoader(Predicate<EntryData> applies, Function<EntryData, DbEntry> loader) {
}
