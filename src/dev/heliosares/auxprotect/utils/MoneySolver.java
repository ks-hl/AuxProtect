package dev.heliosares.auxprotect.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import dev.heliosares.auxprotect.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;

public class MoneySolver extends ChartRenderer {
	private double[] values = new double[100];
	private List<LocalDate> xDivs;

	private MoneySolver(Player player, ArrayList<DbEntry> results, int time, String user) {
		super(user + "'" + (user.toLowerCase().endsWith("s") ? "" : "s") + " Money",
				(byte) (ChartRenderer.LIGHT_GRAY + 2), 100);
		long start = results.get(results.size() - 1).getTime();
		long end = results.get(0).getTime();
		long duration = end - start;
		long inc = duration / 99;
		double currentMoney = 0;
		int resultsIndex = 0;
		LocalDate startDate = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate();
		LocalDate endDate = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate();
		this.xDivs = new ArrayList<>();
		this.xDivs.addAll(getDatesBetween(startDate, endDate));
		this.xDivs.add(endDate);
		double maxMoney = 0;
		for (int i = 0; i < 100; i++) {
			while (true) {
				DbEntry result = results.get(results.size() - 1 - resultsIndex);
				if (result.getTime() > start + inc * i) {
					break;
				}
				try {
					currentMoney = Double.parseDouble(result.getData().substring(1).replaceAll(",", ""));
				} catch (Exception e) {
					try {
						currentMoney = Double.parseDouble(result.getTargetUUID().substring(1).replaceAll(",", ""));
					} catch (Exception e1) {
						e1.printStackTrace();
						return;
					}
				}
				resultsIndex++;
			}
			values[i] = currentMoney;
			if (currentMoney > maxMoney) {
				maxMoney = currentMoney;
			}
		}
		while (maxMoney * this.yScale > 100) {
			this.yScale /= 10.0;
		}
		maxMoney = sigDigRounder(maxMoney, 1, 1);
		this.yScale = 100 / maxMoney;
		update();
	}

	public static List<LocalDate> getDatesBetween(LocalDate startDate, LocalDate endDate) {
		long numOfDaysBetween = ChronoUnit.DAYS.between(startDate, endDate);
		return IntStream.iterate(0, i -> i + 1).limit(numOfDaysBetween).mapToObj(i -> startDate.plusDays(i))
				.collect(Collectors.toList());
	}

	private static double sigDigRounder(double value, int nSigDig, int dir) {
		double intermediate = value / Math.pow(10, Math.floor(Math.log10(Math.abs(value))) - (nSigDig - 1));

		if (dir > 0)
			intermediate = Math.ceil(intermediate);
		else if (dir < 0)
			intermediate = Math.floor(intermediate);
		else
			intermediate = Math.round(intermediate);

		double result = intermediate * Math.pow(10, Math.floor(Math.log10(Math.abs(value))) - (nSigDig - 1));

		return (result);

	}

	@Override
	public double getValue(int x) {
		return values[x];
	}

	@Override
	public void drawXDivs(MapView view, MapCanvas canvas, Player player) {
		double pixelsPerDiv = xSize / (double) xDivs.size();
		int numberOfLabels = 3;
		for (int x = 0; x < xDivs.size() + 1; x++) {
			canvas.setPixel((int) Math.round(x * pixelsPerDiv + xShift) - 1, ySize + yShift + 2, BLACK);
		}
		double inc = xDivs.size() / (double) numberOfLabels;
		double incP1 = (xDivs.size() + 1) / (double) numberOfLabels;
		for (int i = 0; i < numberOfLabels; i++) {
			String str = xDivs.get((int) Math.min(Math.ceil(inc * i), xDivs.size() - 1)).getDayOfMonth() + "";
			int x = (int) Math.round(i * incP1 * pixelsPerDiv + xShift) - 6;
			int xWidth = str.length() * 6;
			if (x + xWidth >= 128) {
				x = 127 - xWidth;
			}
			canvas.drawText(x, ySize + yShift + 3, MinecraftFont.Font, str);
		}
	}

	public static void showMoney(IAuxProtect plugin, Player player, ArrayList<DbEntry> results, int time, String users) {
		plugin.runSync(new Runnable() {
			@Override
			public void run() {
				MoneySolver solver = new MoneySolver(player, results, time, users);
				player.getInventory().addItem(solver.asItem(player));
			}
		});
	}
}
