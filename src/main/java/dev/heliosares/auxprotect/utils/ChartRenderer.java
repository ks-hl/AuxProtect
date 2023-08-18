package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.Arrays;

public class ChartRenderer extends MapRenderer {

    public static final int xShift = 27;
    public static final int yShift = 12;
    public static final int xSize = 100;
    public static final int ySize = 100;
    public final double xScale = 1;
    private final double[] values;
    private final Color bgColor;
    private final String title;
    private final int xDivs;
    private final AuxProtectSpigot plugin;
    private final Color[][] map = new Color[128][128];
    public double yScale = 1;

    public ChartRenderer(AuxProtectSpigot plugin, String title, Color bgColor, int values) {
        this.plugin = plugin;
        this.title = title;
        this.bgColor = bgColor;
        this.values = new double[values];
        this.xDivs = 11;
    }

    private static String doubleToString(double d) {
        String output = d + "";
        if (output.endsWith(".0")) {
            return output.split("\\.")[0];
        }
        return output;
    }

    public void update() {
        for (Color[] colors : map) {
            Arrays.fill(colors, bgColor);
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = getValue(i);
        }
        // DRAW BORDER
        // HORIZONTALS
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < xSize + 2; x++) {
                map[x + xShift - 1][y * (ySize + 1) + yShift] = Color.BLACK;
            }
        }
        // VERTICALS
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < ySize + 2; y++) {
                map[x * (xSize + 1) + xShift - 1][y + yShift] = Color.BLACK;

            }
        }
        // DRAW DATA
        for (int i = 0; i < values.length; i++) {
            int[] coords = getCoordsForData(i, values[i]);
            map[coords[0]][coords[1]] = Color.RED;
        }
    }

    public double getValue(int x) {
        return 90 - 3 * Math.pow((x - 50) / 10D, 2);
    }

    @Override
    public void render(@Nonnull MapView view, @Nonnull MapCanvas canvas, @Nonnull Player player) {
        for (int x = 0; x < map.length; x++) {
            for (int y = 0; y < map[x].length; y++) {
                setPixelColor(canvas, x, y, map[x][y]);
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
            char suffix = switch (powers) {
                case 1 -> 'k';
                case 2 -> 'M';
                default -> ' ';
            };
            canvas.drawText(1, yPos - 4, MinecraftFont.Font, doubleToString(number) + suffix);
            setPixelColor(canvas, xShift - 2, yPos, Color.BLACK);
        }
        drawXDivs(view, canvas, player);
    }

    public void drawXDivs(MapView view, MapCanvas canvas, Player player) {
        double pixelsPerDiv = xSize / (double) (xDivs - 1);
        for (int x = 0; x < xDivs; x++) {
            map[(int) Math.round(x * pixelsPerDiv + xShift) - 1][ySize + yShift + 2] = Color.BLACK;
        }
    }

    public ItemStack asItem(Player player) {
        MapView view = Bukkit.createMap(player.getWorld());
        for (MapRenderer renderer : view.getRenderers())
            view.removeRenderer(renderer);
        view.addRenderer(this);
        ItemStack i = new ItemStack(Material.FILLED_MAP, 1);
        if (i.getItemMeta() instanceof MapMeta meta) {
            meta.setMapView(view);
            i.setItemMeta(meta);
        } else {
            return null;
        }
        return i;
    }

    @SuppressWarnings("deprecation")
    void setPixelColor(MapCanvas canvas, int x, int y, Color color) {
        if (color == null) {
            color = bgColor;
        }
        if (plugin.getCompatabilityVersion() >= 19) {
            canvas.setPixelColor(x, y, color);
        } else {
            canvas.setPixel(x, y, MapPalette.matchColor(color));
        }
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

        return new int[]{xShift + (int) Math.round(x), yShift + 100 - (int) Math.round(y)};
    }
}