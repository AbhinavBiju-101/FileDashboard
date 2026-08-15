import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A separate, chronological companion to RecentActivity: where RecentActivity
 * keeps small MRU caches (capped at 20) tuned for "what's relevant right
 * now", this keeps a longer append-only timeline of the same kind of events
 * (view/download/upload), so the Dashboard can show "past week" and
 * "on this day" sections without those getting evicted by a busy afternoon
 * of file-browsing. Persisted to Config.DATA_DIR/timeline.json, same
 * temp-file-then-atomic-move pattern as RecentActivity/TrashManager.
 *
 * Capped at MAX_EVENTS rather than kept forever - for a self-hosted, single-
 * person tool that's a reasonable trade-off between "on this day" actually
 * having something to show a year from now and not growing an unbounded log
 * file. If activity is heavy enough to roll a year-old entry out of a 3000-
 * event cap, "on this day" simply won't have anything for that particular
 * day - it degrades gracefully rather than erroring.
 */
public class ActivityLog {

    public static class Event {
        public String path;
        public String type; // "viewed" | "downloaded" | "uploaded"
        public long time;
    }

    private static final int MAX_EVENTS = 3000;
    private static final File STORE_FILE = new File(Config.DATA_DIR, "timeline.json");
    private static final List<Event> events = Collections.synchronizedList(new ArrayList<>());

    // Bumped on every recorded event, same purpose as RecentActivity.version -
    // DashboardEventsHandler combines both so the Dashboard's live-refresh
    // poll picks up timeline changes too.
    private static volatile long version = 0;

    public static long getVersion() {
        return version;
    }

    static {
        load();
    }

    public static void record(String relPath, String type) {
        Event e = new Event();
        e.path = relPath;
        e.type = type;
        e.time = System.currentTimeMillis();
        events.add(e);
        synchronized (events) {
            while (events.size() > MAX_EVENTS) events.remove(0);
        }
        version++;
        save();
    }

    // Most-recent-first, everything within the last 7 days.
    public static List<Event> getPastWeek() {
        long cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        List<Event> result = new ArrayList<>();
        synchronized (events) {
            for (int i = events.size() - 1; i >= 0; i--) {
                Event e = events.get(i);
                if (e.time >= cutoff) result.add(e);
            }
        }
        return result;
    }

    // Anything that happened on today's calendar month+day in a previous
    // year - classic "memories" behavior. Most recent matching year first.
    public static List<Event> getOnThisDay() {
        Calendar today = Calendar.getInstance();
        int todayMonth = today.get(Calendar.MONTH);
        int todayDay = today.get(Calendar.DAY_OF_MONTH);
        int thisYear = today.get(Calendar.YEAR);

        List<Event> result = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        synchronized (events) {
            for (Event e : events) {
                cal.setTimeInMillis(e.time);
                if (cal.get(Calendar.MONTH) == todayMonth
                    && cal.get(Calendar.DAY_OF_MONTH) == todayDay
                    && cal.get(Calendar.YEAR) != thisYear) {
                    result.add(e);
                }
            }
        }
        result.sort((a, b) -> Long.compare(b.time, a.time));
        return result;
    }

    // ---------- Persistence (same pattern as RecentActivity/TrashManager) ----------

    private static synchronized void save() {
        try {
            if (!Config.DATA_DIR.exists()) Config.DATA_DIR.mkdirs();

            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"events\": [\n");
            List<Event> snapshot;
            synchronized (events) {
                snapshot = new ArrayList<>(events);
            }
            for (int i = 0; i < snapshot.size(); i++) {
                Event e = snapshot.get(i);
                sb.append("    {")
                  .append("\"path\": \"").append(MiniJson.escape(e.path)).append("\", ")
                  .append("\"type\": \"").append(MiniJson.escape(e.type)).append("\", ")
                  .append("\"time\": ").append(e.time)
                  .append("}");
                sb.append(i < snapshot.size() - 1 ? ",\n" : "\n");
            }
            sb.append("  ]\n}\n");

            Path tempFile = Files.createTempFile(Config.DATA_DIR.toPath(), "timeline", ".tmp");
            Files.write(tempFile, sb.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tempFile, STORE_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Warning: could not save timeline data: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        if (!STORE_FILE.exists()) return;
        try {
            String content = new String(Files.readAllBytes(STORE_FILE.toPath()), StandardCharsets.UTF_8);
            Object parsed = MiniJson.parse(content);
            if (!(parsed instanceof Map)) return;
            Object arr = ((Map<String, Object>) parsed).get("events");
            if (!(arr instanceof List)) return;

            for (Object item : (List<Object>) arr) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> obj = (Map<String, Object>) item;
                Object path = obj.get("path");
                Object type = obj.get("type");
                Object time = obj.get("time");
                if (path instanceof String && type instanceof String && time instanceof Double) {
                    Event e = new Event();
                    e.path = (String) path;
                    e.type = (String) type;
                    e.time = ((Double) time).longValue();
                    events.add(e);
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: could not load timeline data (starting fresh): " + e.getMessage());
        }
    }
}
