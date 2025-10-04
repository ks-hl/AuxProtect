package dev.heliosares.auxprotect.database;

public class Snowflake {
    // If you're still using this on October 7, 4892, you'll have some problems
    public static final int COUNTER_FACTOR = 100_000;
    private static long lastTime;
    private static int counter;

    public static synchronized long getNextSnowflake() {
        long now = System.currentTimeMillis();
        if (now > lastTime) {
            lastTime = now;
            counter = 0;
        } else if (counter + 1 < COUNTER_FACTOR) {
            counter++;
        } else { // Resort to old method of skipping to the next millisecond. It's not pretty, but this should be an extreme edge case
            lastTime++;
            counter = 0;
        }
        return lastTime * COUNTER_FACTOR + counter;
    }
}
