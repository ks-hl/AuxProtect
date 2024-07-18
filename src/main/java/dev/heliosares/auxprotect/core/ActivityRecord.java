package dev.heliosares.auxprotect.core;

import net.md_5.bungee.api.ChatColor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public record ActivityRecord(@Nonnull List<Activity> activities, double distanceMoved) {
    @Override
    public String toString() {
        StringBuilder activityString = new StringBuilder(getActivityString());

        activityString.append(";");
        final double moved = distanceMoved();
        if (moved > 1E-6) {
            if (moved >= 10) {
                activityString.append((int) Math.round(moved));
            } else {
                activityString.append(Math.round(moved * 10) / 10D);
            }
        }
        return activityString.toString();
    }

    public String getActivityString() {
        StringBuilder activityString = new StringBuilder();

        for (Activity a : activities()) {
            activityString.append(a.character);
        }

        return activityString.toString();
    }

    public double countScore() {
        double score = Math.floor((distanceMoved()) / 10);

        if (distanceMoved() > 1E-6) score++;

        for (Activity activity : activities()) {
            score += activity.score;
        }

        return score;
    }

    public static ActivityRecord parse(String data) throws IllegalArgumentException {
        if (data == null) return null;

        if (data.matches("\\d+")) {
            throw new IllegalArgumentException("Legacy activity");
        }
        if (!data.matches("[^;]*;(\\d+(\\.\\d)?)?")) {
            throw new IllegalArgumentException("Invalid activity string");
        }
        List<Activity> activities = new ArrayList<>();
        if (data.startsWith(";")) data = " " + data;
        if (data.endsWith(";")) data += " ";
        String[] parts = data.split(";");

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid activity string format");
        }

        for (char c : parts[0].trim().toCharArray()) {
            activities.add(Activity.getByChar(c));
        }

        double distance = parts[1].isBlank() ? 0 : Double.parseDouble(parts[1].trim());

        return new ActivityRecord(activities, distance);
    }

    public String getHoverText() {
        StringBuilder hoverText = new StringBuilder("\n\n");
        hoverText.append(ChatColor.COLOR_CHAR + "7Activity: " + ChatColor.COLOR_CHAR + "9").append(getActivityString()).append("\n");
        for (Activity activity : new HashSet<>(activities())) {
            hoverText.append(ChatColor.COLOR_CHAR + "7  ").append(activity.character).append(" = ").append(activity.toString().toLowerCase()).append(" (").append(activity.score).append(")\n");
        }
        hoverText.append(ChatColor.COLOR_CHAR + "7Moved " + ChatColor.COLOR_CHAR + "9").append(distanceMoved()).append(ChatColor.COLOR_CHAR).append("7 Blocks");
        return hoverText.toString();
    }
}
