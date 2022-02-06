package dev.heliosares.auxprotect.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;

public class ChartRenderer extends MapRenderer {

	private byte[][] map = new byte[128][128];
	private final double values[];
	public double xScale = 1;
	public double yScale = 1;
	private final byte bgColor;

	public static final int xShift = 27;
	public static final int yShift = 12;
	public static final int xSize = 100;
	public static final int ySize = 100;

	public static final byte BLUE = 0x30;
	public static final byte BROWN = 0x28;
	public static final byte DARK_BROWN = 0x34;
	public static final byte DARK_GRAY = 0x2c;
	public static final byte DARK_GREEN = 0x1c;
	public static final byte GRAY_1 = 0xc;
	public static final byte GRAY_2 = 0x18;
	public static final byte LIGHT_BROWN = 0x8;
	public static final byte LIGHT_GRAY = 0x24;
	public static final byte LIGHT_GREEN = 0x4;
	public static final byte PALE_BLUE = 0x14;
	public static final byte RED = 0x10;
	public static final byte TRANSPARENT = 0x0;
	public static final byte WHITE = 0x20;
	public static final byte BLACK = 0x76;

	private final boolean pallet;
	private final String title;
	private final int xDivs;
	@SuppressWarnings("unused")
	private final String[] xLabels;

	public ChartRenderer(String title, byte bgColor, int values) {
		this.title = title;
		this.pallet = false;
		this.bgColor = bgColor;
		this.values = new double[values];
		this.xDivs = 11;
		this.xLabels = new String[] { "1", "2", "3", "4", "5", "6" };
		if (pallet) {
			for (int x = 0; x < map.length; x++) {
				for (int y = 0; y < map[x].length; y++) {
					map[y][x] = (byte) (((x / 8) + (y / 8) * 16) - 128);
				}
			}
			return;
		}
	}

	public void update() {
		for (int x = 0; x < map.length; x++) {
			for (int y = 0; y < map[x].length; y++) {
				map[x][y] = bgColor;
			}
		}
		for (int i = 0; i < values.length; i++) {
			values[i] = getValue(i);
		}
		// DRAW BORDER
		// HORIZONTALS
		for (int y = 0; y < 2; y++) {
			for (int x = 0; x < xSize + 2; x++) {
				map[x + xShift - 1][y * (ySize + 1) + yShift] = BLACK;
			}
		}
		// VERTICALS
		for (int x = 0; x < 2; x++) {
			for (int y = 0; y < ySize + 2; y++) {
				map[x * (xSize + 1) + xShift - 1][y + yShift] = BLACK;

			}
		}
		// DRAW DATA
		for (int i = 0; i < values.length; i++) {
			int[] coords = getCoordsForData(i, values[i]);
			map[coords[0]][coords[1]] = RED;
		}
	}

	public double getValue(int x) {
		return 90 - 3 * Math.pow((x - 50) / 10D, 2);
	}

	@Override
	public void render(MapView view, MapCanvas canvas, Player player) {
		if (pallet) {
			for (int x = 0; x < map.length; x++) {
				byte[] xRow = map[x];
				for (int y = 0; y < xRow.length; y++) {
					byte id = (byte) (((x / 8) + (y / 8) * 16) - 128);
					canvas.setPixel(x, y, id);
				}
			}
			return;
		}
		for (int x = 0; x < map.length; x++) {
			for (int y = 0; y < map[x].length; y++) {
				canvas.setPixel(x, y, map[x][y]);
			}
		}
		canvas.drawText(2, 2, MinecraftFont.Font, title);
		// DRAW DIVs (Done every tick because it requires drawText)
		// Y Divs
		double max = 100 / yScale;
		for (int y = 0; y < 10; y++) {
			int yPos = (10 - y) * 10 + yShift + 1;
			double number = (int) (y * max / 10D);
			int powers = 0;
			while (number >= 1000) {
				number /= 1000;
				powers++;
			}
			char suffix = ' ';
			switch (powers) {
			case 1:
				suffix = 'k';
				break;
			case 2:
				suffix = 'M';
				break;
			}
			canvas.drawText(1, yPos - 4, MinecraftFont.Font, doubleToString(number) + "" + suffix);
			canvas.setPixel(xShift - 2, yPos, BLACK);
		}
		drawXDivs(view, canvas, player);
	}

	public void drawXDivs(MapView view, MapCanvas canvas, Player player) {
		double pixelsPerDiv = xSize / (double) (xDivs - 1);
		for (int x = 0; x < xDivs; x++) {
			map[(int) Math.round(x * pixelsPerDiv + xShift) - 1][ySize + yShift + 2] = BLACK;
		}
	}

	private static String doubleToString(double d) {
		String output = d + "";
		if (output.endsWith(".0")) {
			return output.split("\\.")[0];
		}
		return output;
	}

	private int[] getCoordsForData(double x, double y) {
		x *= xScale;
		y *= yScale;

		if (x > xSize + 1) {
			x = xSize + 1;
		}
		if (x < 0) {
			x = 0;
		}

		if (y > ySize + 1) {
			y = ySize + 1;
		}
		if (y < 0) {
			y = 0;
		}

		return new int[] { xShift + (int) Math.round(x), yShift + 100 - (int) Math.round(y) };
	}

	public ItemStack asItem(Player player) {
		MapView view = Bukkit.createMap(player.getWorld());
		for (MapRenderer renderer : view.getRenderers())
			view.removeRenderer(renderer);
		view.addRenderer(this);
		ItemStack i = new ItemStack(Material.FILLED_MAP, 1);
		if (i.getItemMeta() instanceof MapMeta) {
			MapMeta meta = ((MapMeta) i.getItemMeta());
			meta.setMapView(view);
			i.setItemMeta(meta);
		} else {
			return null;
		}
		return i;
	}
}