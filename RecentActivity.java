import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks activity for the home dashboard: recently viewed files, recently
 * downloaded files, and how often each folder gets browsed - persisted to
 * a small JSON file (Config.DATA_DIR/activity.json) so it survives restarts.
 *
 * recentViewed/recentDownloaded use access-ordered LinkedHashMaps as a cheap
 * MRU (most-recently-used) cache: re-opening a file just moves it back to
 * the front instead of creating a duplicate entry. Every mutation triggers
 * a write-through save; for a personal local tool the request volume is low
 * enough that this is simpler and safer than batching writes.
 */
public class RecentActivity {

    private static final int MAX_RECENT = 20;
    private static final File STORE_FILE = new File(Config.DATA_DIR, "activity.json");

    private static final Map<String, Long> recentViewed =
        Collections.synchronizedMap(new LinkedHashMap<String, Long>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_RECENT;
            }
        });

    private static final Map<String, Long> recentDownloaded =
        Collections.synchronizedMap(new LinkedHashMap<String, Long>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_RECENT;
            }
        });

    private static final Map<String, Integer> folderVisitCounts = new ConcurrentHashMap<>();

    static {
        load();
    }

    public static void recordView(String relPath) {
        recentViewed.put(relPath, System.currentTimeMillis());
        save();
    }

    public static void recordDownload(String relPath) {
        recentDownloaded.put(relPath, System.currentTimeMillis());
        save();
    }

    public static void recordFolderVisit(String relPath) {
        folderVisitCounts.merge(relPath, 1, Integer::sum);
        save();
    }

    public static void forgetPath(String relPath) {
        // Called when a file/folder is renamed, moved, or deleted so stale
        // entries don't linger in "recent" lists pointing at nothing.
        recentViewed.remove(relPath);
        recentDownloaded.remove(relPath);
        folderVisitCounts.remove(relPath);
        save();
    }

    // Most recent first.
    public static List<String> getRecentViewed() {
        return reversedSnapshot(recentViewed);
    }

    // Most recent first.
    public static List<String> getRecentDownloaded() {
        return reversedSnapshot(recentDownloaded);
    }

    public static List<String> getFrequentFolders(int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(folderVisitCounts.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            result.add(entries.get(i).getKey());
        }
        return result;
    }

    // Exposes raw activity for the search-suggestion ranker (recency +
    // frequency signal), most-recent-first, capped to avoid scanning everything.
    public static List<String> getAllKnownPaths() {
        List<String> combined = new ArrayList<>();
        combined.addAll(getRecentViewed());
        combined.addAll(getRecentDownloaded());
        combined.addAll(folderVisitCounts.keySet());
        return combined;
    }

    private static List<String> reversedSnapshot(Map<String, Long> map) {
        List<String> list;
        synchronized (map) {
            list = new ArrayList<>(map.keySet());
        }
        Collections.reverse(list); // LinkedHashMap access-order puts most-recent last
        return list;
    }

    // ---------- Persistence ----------

    private static synchronized void save() {
        try {
            if (!Config.DATA_DIR.exists()) Config.DATA_DIR.mkdirs();

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");

            sb.append("  \"recentViewed\": [\n");
            appendEntries(sb, recentViewed);
            sb.append("  ],\n");

            sb.append("  \"recentDownloaded\": [\n");
            appendEntries(sb, recentDownloaded);
            sb.append("  ],\n");

            sb.append("  \"folderVisits\": {\n");
            List<String> keys = new ArrayList<>(folderVisitCounts.keySet());
            for (int i = 0; i < keys.size(); i++) {
                String k = keys.get(i);
                sb.append("    \"").append(MiniJson.escape(k)).append("\": ").append(folderVisitCounts.get(k));
                sb.append(i < keys.size() - 1 ? ",\n" : "\n");
            }
            sb.append("  }\n");

            sb.append("}\n");

            // Write to a temp file then atomically rename, so a crash mid-write
            // never leaves activity.json truncated/corrupted.
            Path tempFile = Files.createTempFile(Config.DATA_DIR.toPath(), "activity", ".tmp");
            Files.write(tempFile, sb.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tempFile, STORE_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Warning: could not save activity data: " + e.getMessage());
        }
    }

    private static void appendEntries(StringBuilder sb, Map<String, Long> map) {
        List<String> keys;
        synchronized (map) {
            keys = new ArrayList<>(map.keySet());
        }
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            Long time = map.get(k);
            if (time == null) continue;
            sb.append("    {\"path\": \"").append(MiniJson.escape(k)).append("\", \"time\": ").append(time).append("}");
            sb.append(i < keys.size() - 1 ? ",\n" : "\n");
        }
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        if (!STORE_FILE.exists()) return;
        try {
            String content = new String(Files.readAllBytes(STORE_FILE.toPath()), StandardCharsets.UTF_8);
            Object parsed = MiniJson.parse(content);
            if (!(parsed instanceof Map)) return;
            Map<String, Object> root = (Map<String, Object>) parsed;

            loadEntries(root.get("recentViewed"), recentViewed);
            loadEntries(root.get("recentDownloaded"), recentDownloaded);

            Object folderVisitsObj = root.get("folderVisits");
            if (folderVisitsObj instanceof Map) {
                Map<String, Object> fv = (Map<String, Object>) folderVisitsObj;
                for (Map.Entry<String, Object> e : fv.entrySet()) {
                    if (e.getValue() instanceof Double) {
                        folderVisitCounts.put(e.getKey(), ((Double) e.getValue()).intValue());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: could not load activity data (starting fresh): " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadEntries(Object arrObj, Map<String, Long> target) {
        if (!(arrObj instanceof List)) return;
        List<Object> arr = (List<Object>) arrObj;
        // Stored oldest-first, so re-inserting in this order rebuilds the
        // same recency order in the access-ordered LinkedHashMap.
        for (Object item : arr) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> obj = (Map<String, Object>) item;
            Object path = obj.get("path");
            Object time = obj.get("time");
            if (path instanceof String && time instanceof Double) {
                target.put((String) path, ((Double) time).longValue());
            }
        }
    }
}
