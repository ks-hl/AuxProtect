package dev.heliosares.auxprotect.utils;

public class TimeUtil {

    private static String padDouble(double d) {
        String[] split = (d + "").split("\\.");
        while (split[0].length() < 2) {
            split[0] = "0" + split[0];
        }
        if (split.length == 1) {
            return split[0] + ".00";
        }
        while (split[1].length() < 2) {
            split[1] += "0";
        }
        return split[0] + "." + split[1];
    }

    public static double roundToPlaces(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    public static String millisToString(double millis) {
        millis /= 1000.0;
        if (millis < 60) {
            return padDouble(roundToPlaces(millis, 2)) + "s";
        }
        millis /= 60.0;
        if (millis < 60) {
            return padDouble(roundToPlaces(millis, 2)) + "m";
        }
        millis /= 60.0;
        if (millis < 24) {
            return padDouble(roundToPlaces(millis, 2)) + "h";
        }
        millis /= 24.0;
        return padDouble(roundToPlaces(millis, 2)) + "d";
    }

    public static String millisToStringExtended(long time) {
        time /= 1000;
        int days = (int) (time / (60 * 60 * 24));
        int hours = (int) ((time / (60 * 60)) % 24);
        int minutes = (int) ((time / 60) % 60);
        int seconds = (int) (time % 60);
        String playtimeMsg = "";
        boolean started = false;
        if (days > 0) {
            playtimeMsg += days + "d";
            started = true;
        }
        if (hours > 0) {
            if (started) {
                playtimeMsg += " ";
            }
            playtimeMsg += " " + hours + "h";
            started = true;
        }
        if (minutes > 0) {
            if (started) {
                playtimeMsg += " ";
            }
            playtimeMsg += " " + minutes + "m";
            started = true;
        }
        if (seconds > 0) {
            if (started) {
                playtimeMsg += " ";
            }
            playtimeMsg += " " + seconds + "s";
            started = true;
        }
        return playtimeMsg;
    }

    public static long stringToMillis(String timeStr) throws NumberFormatException {
        String builder = "";
        long time = 0;
        if (timeStr.endsWith("e")) {
            return Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
        }
        for (char c : timeStr.toCharArray()) {
            if (c >= '0' && c <= '9' || c == '.') {
                builder += c;
                continue;
            }
            int modifier = 1;
            switch (c) {
                case 'w':
                    modifier *= 7;
                case 'd':
                    modifier *= 24;
                case 'h':
                    modifier *= 60;
                case 'm':
                    modifier *= 60;
                case 's':
                    modifier *= 1000;
                case 'f':
                    break;
                default:
                    throw new NumberFormatException("Invalid specifier: " + c);
            }
            time += Double.parseDouble(builder) * modifier;
            builder = "";
        }
        if (builder.length() > 0) {
            time += Double.parseDouble(builder);
        }
        return time;
    }
}
