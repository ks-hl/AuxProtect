package dev.heliosares.auxprotect.utils;

import org.bukkit.entity.Player;

public class Experience {
    /**
     * Calculate a player's total experience based on level and progress to next.
     *
     * @param player the Player
     * @return the amount of experience the Player has
     * @see <a
     * href=http://minecraft.gamepedia.com/Experience#Leveling_up>Experience#Leveling_up</a>
     */
    public static int getTotalExp(Player player) {
        return getExpFromLevel(player.getLevel()) + (int) player.getExp();
    }

    /**
     * Calculate total experience based on level.
     *
     * @param level the level
     * @return the total experience calculated
     * @see <a
     * href=http://minecraft.gamepedia.com/Experience#Leveling_up>Experience#Leveling_up</a>
     */
    public static int getExpFromLevel(int level) {
        if (level > 31) {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
        if (level > 16) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return level * level + 6 * level;
    }

    /**
     * Calculate level based on total experience.
     *
     * @param exp the total experience
     * @return the level calculated
     */
    public static double getLevelFromExp(long exp) {
        if (exp > 1507) {
            return (325D / 18D) + Math.sqrt((2 / 9D) * (exp - (54215D / 72D)));
        }
        if (exp > 352) {
            return 8.1 + Math.sqrt(0.4 * (exp - (7839 / 40D)));
        }
        if (exp > 0) {
            return Math.sqrt(exp + 9D) - 3;
        }
        return 0;
    }

    public static void setExp(Player player, int exp) {
        if (exp < 0) {
            exp = 0;
        }

        double levelAndExp = getLevelFromExp(exp);
        int level = (int) levelAndExp;
        player.setLevel(level);
        player.setExp((float) (levelAndExp - level));
    }
}
