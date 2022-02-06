package dev.heliosares.auxprotect.utils;

import org.bukkit.entity.Player;

public class Experience {
	/**
	 * Calculate a player's total experience based on level and progress to next.
	 *
	 * @param player the Player
	 * @return the amount of experience the Player has
	 *
	 * @see <a
	 *      href=http://minecraft.gamepedia.com/Experience#Leveling_up>Experience#Leveling_up</a>
	 */
	public static int getTotalExp(Player player) {
		return getExpFromLevel(player.getLevel()) + player.getExpToLevel();
	}

	/**
	 * Calculate total experience based on level.
	 *
	 * @param level the level
	 * @return the total experience calculated
	 *
	 * @see <a
	 *      href=http://minecraft.gamepedia.com/Experience#Leveling_up>Experience#Leveling_up</a>
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
	 * Get the total amount of experience required to progress to the next level.
	 *
	 * @param level the current level
	 *
	 * @see <a
	 *      href=http://minecraft.gamepedia.com/Experience#Leveling_up>Experience#Leveling_up</a>
	 */
	public static int getExpToNextLevel(int level) {
		if (level > 30) {
			// Simplified formula. Internal: 112 + (level - 30) * 9
			return level * 9 - 158;
		}
		if (level > 15) {
			// Simplified formula. Internal: 37 + (level - 15) * 5
			return level * 5 - 38;
		}
		// Internal: 7 + level * 2
		return level * 2 + 7;
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

	/**
	 * Change a Player's experience.
	 *
	 * <p>
	 * This method is preferred over {@link Player#giveExp(int)}. <br>
	 * In older versions the method does not take differences in exp per level into
	 * account. This leads to overlevelling when granting players large amounts of
	 * experience. <br>
	 * In modern versions, while differing amounts of experience per level are
	 * accounted for, the approach used is loop-heavy and requires an excessive
	 * number of calculations, which makes it quite slow.
	 *
	 * @param player the Player affected
	 * @param exp    the amount of experience to add or remove
	 */
	public static void giveExp(Player player, int exp) {
		exp += getTotalExp(player);
		setExp(player, exp);
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
