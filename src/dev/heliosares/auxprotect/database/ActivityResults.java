package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.utils.ActivitySolver;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class ActivityResults extends Results {

    private final long rangeStart;
    private final long rangeEnd;

    public ActivityResults(IAuxProtect plugin, List<DbEntry> entries, SenderAdapter player, Parameters params) {
        super(plugin, entries, player, params);

        rangeEnd = entries.get(0).getTime();

        LocalDateTime startTime = Instant.ofEpochMilli(entries.get(entries.size() - 1).getTime())
                .atZone(ZoneId.systemDefault()).toLocalDateTime().withMinute(0).withSecond(0).withNano(0);
        rangeStart = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Override
    public void showPage(int page, int perPage_) {
        int lastpage = getNumPages(perPage_);
        if (page > lastpage || page < 1) {
            player.sendLang(Language.L.COMMAND__LOOKUP__NOPAGE);
            return;
        }
        setPerPage(perPage_);
        setCurrentPage(page);
        super.sendHeader();

        long millisperpage = getPerPage() * 3600000L;
        // long thisRangeStart = rangeStart + (page - 1) * millisperpage;
        long thisRangeEnd = rangeEnd - (getNumPages(perPage_) - page) * millisperpage;

        player.sendMessage(ActivitySolver.solveActivity(getEntries(),
                Math.max(thisRangeEnd - millisperpage, rangeStart), thisRangeEnd));

        super.sendArrowKeys(page);
    }

    @Override
    public int getNumPages(int perpage) {
        return (int) Math.ceil((double) (rangeEnd - rangeStart) / 3600000.0 / (double) perpage);
    }
}
