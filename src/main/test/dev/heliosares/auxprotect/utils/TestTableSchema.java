package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.database.Table;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestTableSchema {
    @Test
    public void testTables() {
        for (PlatformType platformType : PlatformType.values()) {
            if (platformType == PlatformType.NONE) continue;
            System.out.println("Checking " + platformType);
            for (Table table : Table.values()) {
                System.out.println("    " + table);
                if (!table.hasAPEntries()) continue;
                String header = table.getValuesHeader(platformType);
                assert header != null;
                assertEquals(table.getNumColumns(platformType), header.replaceAll("[^,]+", "").length() + 1);
            }
        }
    }
}
