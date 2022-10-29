package dev.heliosares.auxprotect.database;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.utils.ActivitySolver;

import dev.heliosares.auxprotect.core.Language;
public class ActivityResults extends Results {

	private final long rangeStart;
	private final long rangeEnd;

	public ActivityResults(IAuxProtect plugin, ArrayList<DbEntry> entries, SenderAdapter player, Parameters params) {
		super(plugin, entries, player, params);

		rangeEnd = entries.get(0).getTime();

		LocalDateTime startTime = Instant.ofEpochMilli(entries.get(entries.size() - 1).getTime())
				.atZone(ZoneId.systemDefault()).toLocalDateTime().withMinute(0).withSecond(0).withNano(0);
		rangeStart = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	@Override
	public void showPage(int page, int perpage_) {
		int lastpage = getNumPages(perpage_);
		if (page > lastpage || page < 1) {
			player.sendLang(Language.L.COMMAND__LOOKUP__NOPAGE);
			return;
		}
		perpage = perpage_;
		prevpage = page;
		super.sendHeader();

		long millisperpage = perpage * 3600000L;
		// long thisRangeStart = rangeStart + (page - 1) * millisperpage;
		long thisRangeEnd = rangeEnd - (getNumPages(perpage_) - page) * millisperpage;

		player.sendMessage(ActivitySolver.solveActivity(getEntries(),
				Math.max(thisRangeEnd - millisperpage, rangeStart), thisRangeEnd));

		super.sendArrowKeys(page);
	}

	@Override
	public int getNumPages(int perpage) {
		return (int) Math.ceil((double) (rangeEnd - rangeStart) / 3600000.0 / (double) perpage);
	}
}
