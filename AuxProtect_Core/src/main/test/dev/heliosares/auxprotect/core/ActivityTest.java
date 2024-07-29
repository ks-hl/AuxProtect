package dev.heliosares.auxprotect.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class ActivityTest {
    @Test
    public void checkNoDuplicateCharacters() {
        Set<Character> chars = new HashSet<>();
        for (Activity activity : Activity.values()) {
            if (Set.of(';', '+').contains(activity.character)) {
                throw new IllegalArgumentException("Reserved character '" + activity.character + "' used by " + activity);
            }
            if (!chars.add(activity.character)) {
                throw new IllegalArgumentException("Duplicate characters " + activity.character);
            }
        }
    }

    @Test
    public void testActivityParsing() {
        ActivityRecord record = new ActivityRecord(List.of(Activity.values()), 0, 0.69);
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
        assertNotNull(ActivityRecord.parse("///+2;"));
        assertNotNull(ActivityRecord.parse("///+2.2;"));

        assertThrows(IllegalArgumentException.class, () -> ActivityRecord.parse(""));
        assertThrows(IllegalArgumentException.class, () -> ActivityRecord.parse("+;"));
        assertThrows(IllegalArgumentException.class, () -> ActivityRecord.parse(";;"));
        assertThrows(IllegalArgumentException.class, () -> ActivityRecord.parse("+"));
        assertThrows(IllegalArgumentException.class, () -> ActivityRecord.parse("21"));
        assertThrows(IllegalArgumentException.class, () -> ActivityRecord.parse("21.0"));
    }

    @Test
    public void testActivityScoring() {
        ActivityRecord record = new ActivityRecord(List.of(Activity.values()), 0, 0);
        double totalScore = 0;
        for (Activity activity : Activity.values()) {
            totalScore += activity.score;
        }
        assertEquals(totalScore, record.countScore(), 1E-6);
    }

    @Test
    public void testTruncation() {
        ArrayList<Activity> actions = new ArrayList<>();
        double score = 0;
        for (int i = 0; i < 100; i++) {
            Activity activity = Activity.values()[i % Activity.values().length];
            score += activity.score;
            actions.add(activity);
        }
        ActivityRecord record = new ActivityRecord(actions, 0, 0);

        String data = record.toString();
        System.out.println(data);

        assertEquals(score, record.countScore(), 1E-6);
        assertEquals(score, ActivityRecord.parse(data).countScore(), 1);
    }
}
