package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.exceptions.AlreadyExistsException;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.exceptions.ParseException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.assertThrows;

public class TestAPI {
    @Test
    public void testAPI() throws SQLException, BusyException, IOException, ClassNotFoundException, AlreadyExistsException, InterruptedException, LookupException, ParseException {
        File sqliteFile = new File("test_run", "database.db");
        if (sqliteFile.exists()) //noinspection ResultOfMethodCallIgnored
            sqliteFile.delete();
        TestPlugin testPlugin = new TestPlugin("jdbc:sqlite:" + sqliteFile.getAbsolutePath(), null, sqliteFile, false, null, null);

        EntryAction testAction = AuxProtectAPI.createAction("test", "test_action", "test_action", null);
        assert testAction != null;
        assertThrows(AlreadyExistsException.class, () -> AuxProtectAPI.createAction("test", "test_action", "test_action", null));

        DbEntry entry = new DbEntry("user", testAction, false, "target", "data");
        testPlugin.add(entry);
        Thread.sleep(100);
        var lookup = testPlugin.getSqlManager().getLookupManager().lookup(new Parameters(Table.AUXPROTECT_API).addAction(null, testAction, 0));
        assert !lookup.isEmpty();
    }
}
