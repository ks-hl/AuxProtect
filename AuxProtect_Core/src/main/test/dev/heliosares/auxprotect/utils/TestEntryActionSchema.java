package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.database.EntryAction;
import org.junit.Test;

public class TestEntryActionSchema {
    @Test
    public void testEntryActionSchema() {
        EntryAction.values(); // just need to access the class because it's self-validating upon static initialization
    }
}
