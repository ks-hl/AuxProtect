package dev.heliosares.auxprotect.core;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ActivityTest {
    @Test
    public void checkNoDuplicateCharacters() {
        Set<Character> chars = new HashSet<>();
        for (Activity activity : Activity.values()) {
            if (!chars.add(activity.character)) {
                throw new IllegalArgumentException("Duplicate characters " + activity.character);
            }
        }
    }

    @Test
    public void testActivityParsing() {
        ActivityRecord record = new ActivityRecord(List.of(Activity.values()), 0.69);
        String data = record.toString();
        System.out.println(data);

        ActivityRecord parsed = ActivityRecord.parse(data);
        String dataParsed = parsed.toString();

        assertEquals(data, dataParsed);

        assertEquals(record.distanceMoved(), parsed.distanceMoved(), 0.1);

        assertEquals(record.activities(), parsed.activities());

        assertNotNull(ActivityRecord.parse(";"));
        assertNotNull(ActivityRecord.parse(";0"));
        assertNotNull(ActivityRecord.parse(";0.0"));
        assertNotNull(ActivityRecord.parse("/;0.0"));
        assertNotNull(ActivityRecord.parse("/;0"));
        assertNotNull(ActivityRecord.parse("/;"));
        assertNotNull(ActivityRecord.parse("///;"));
    }

    @Test
    public void testActivityScoring() {
        ActivityRecord record = new ActivityRecord(List.of(Activity.values()), 0);
        double totalScore = 0;
        for (Activity activity : Activity.values()) {
            totalScore += activity.score;
        }
        assertEquals(totalScore, record.countScore(), 1E-6);
    }
}
