package dev.heliosares.auxprotect.utils;

public class TimeUtil {
	public static String millisToString(double millis) {
		millis /= 1000.0;
		if (millis < 60) {
			return padDouble(roundToPlaces(millis, 2)) + "/s";
		}
		millis /= 60.0;
		if (millis < 60) {
			return padDouble(roundToPlaces(millis, 2)) + "/m";
		}
		millis /= 60.0;
		if (millis < 24) {
			return padDouble(roundToPlaces(millis, 2)) + "/h";
		}
		millis /= 24.0;
		return padDouble(roundToPlaces(millis, 2)) + "/d";
	}

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
		return Math.round(value * Math.pow(10, places)) / Math.pow(10, places);
	}

	public static long convertTime(String timeStr) {
		String builder = "";
		long time = 0;
		if (timeStr.endsWith("e")) {
			try {
				return Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
			} catch (NumberFormatException e) {
			}
			return -1;
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
				break;
			default:
				return -1;
			}
			double timePart;
			try {
				timePart = Double.parseDouble(builder);
			} catch (NumberFormatException e) {
				return -1;
			}
			time += timePart * modifier;
			builder = "";
		}
		if (time == 0)
			return -1;
		return time;
	}

	public static long convertTimeOld(String timeStr) {
		char space = timeStr.charAt(timeStr.length() - 1);
		timeStr = timeStr.substring(0, timeStr.length() - 1);
		double time = -1;
		try {
			time = Double.parseDouble(timeStr);
		} catch (NumberFormatException e) {
			return -1;
		}
		switch (space) {
		case 'w':
			time *= 7;
		case 'd':
			time *= 24;
		case 'h':
			time *= 60;
		case 'm':
			time *= 60;
		case 's':
			time *= 1000;
		}
		return (long) time;
	}
}
