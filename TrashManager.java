import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Recycle bin: "deleting" a file/folder actually moves it into
 * Config.TRASH_DIR (a hidden folder inside ROOT_DIR) and records where it
 * came from, so it can be restored later. Nothing is permanently removed
 * until the person explicitly empties the trash or deletes a single item
 * from it. Manifest is persisted as JSON so trash survives a restart, same
 * approach as RecentActivity.
 */
public class TrashManager {

    private static final File MANIFEST_FILE = new File(Config.DATA_DIR, "trash.json");
    private static final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public static class Entry {
        public String id;
        public String trashedName;   // filename actually on disk inside TRASH_DIR
        public String originalRelPath; // where it was, relative to ROOT_DIR
        public String originalName;
        public boolean isDirectory;
        public long deletedTime;
        public long size; // bytes, for files; 0 for folders (not worth summing recursively here)
    }

    static {
        load();
        startAutoPurgeScheduler();
    }

    // Checks for and permanently removes anything past Config.TRASH_
    // RETENTION_DAYS on an hourly timer - hourly granularity is plenty
    // since the badge itself only shows whole days remaining. Runs once
    // immediately at startup too, in case the server was off long enough
    // for something to have already expired. No-ops entirely when
    // TRASH_RETENTION_DAYS is 0 or less.
    private static void startAutoPurgeScheduler() {
        if (Config.TRASH_RETENTION_DAYS <= 0) return;
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trash-auto-purge");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(TrashManager::purgeExpired, 0, 1, TimeUnit.HOURS);
    }

    /** Permanently deletes every trash entry past the retention window. Safe to call anytime. */
    public static synchronized void purgeExpired() {
        if (Config.TRASH_RETENTION_DAYS <= 0) return;
        long cutoff = System.currentTimeMillis() - Config.TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000;
        for (Entry e : new ArrayList<>(entries.values())) {
            if (e.deletedTime <= cutoff) {
                try {
                    permanentlyDelete(e.id);
                } catch (IOException ex) {
                    System.err.println("Warning: auto-purge failed for \"" + e.originalName + "\": " + ex.getMessage());
                }
            }
        }
    }

    // Milliseconds until this entry is auto-purged (negative once it's
    // overdue but hasn't been swept yet); -1 if auto-purge is disabled.
    // Used to render the countdown badge on trash cards.
    public static long millisUntilPurge(Entry e) {
        if (Config.TRASH_RETENTION_DAYS <= 0) return -1;
        long deadline = e.deletedTime + Config.TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000;
        return deadline - System.currentTimeMillis();
    }

    /** Moves a file/folder into the trash. relPath is relative to ROOT_DIR. */
    public static synchronized Entry moveToTrash(File target, String relPath) throws IOException {
        if (!Config.TRASH_DIR.exists()) Config.TRASH_DIR.mkdirs();

        boolean wasDirectory = target.isDirectory();
        long originalSize = wasDirectory ? 0 : target.length();

        String id = UUID.randomUUID().toString();
        String trashedName = id + "-" + target.getName();
        File dest = new File(Config.TRASH_DIR, trashedName);

        Files.move(target.toPath(), dest.toPath());

        Entry e = new Entry();
        e.id = id;
        e.trashedName = trashedName;
        e.originalRelPath = relPath;
        e.originalName = target.getName();
        e.isDirectory = wasDirectory;
        e.deletedTime = System.currentTimeMillis();
        e.size = originalSize;
        entries.put(id, e);
        save();
        return e;
    }

    /** Moves an item back to where it came from. Fails if something already occupies that spot. */
    public static synchronized String restore(String id) throws IOException {
        return restore(id, null);
    }

    // sub identifies one file/folder *inside* a trashed folder (relative,
    // forward-slashed - same convention TrashBrowseHandler/TrashFileHandler
    // use). When empty/null, restores the whole trash entry (original
    // behavior). When set, restores just that nested item to its
    // corresponding spot under the entry's original location, leaving the
    // rest of the trashed folder (and its manifest entry) in place - lets
    // someone pull one file back out of a trashed folder without having to
    // restore the entire folder.
    public static synchronized String restore(String id, String sub) throws IOException {
        Entry e = entries.get(id);
        if (e == null) throw new IOException("That item is no longer in the trash.");

        File trashedBase = new File(Config.TRASH_DIR, e.trashedName);
        if (!trashedBase.exists()) {
            entries.remove(id);
            save();
            throw new IOException("That item's data is missing from the trash - removing the stale entry.");
        }

        boolean nested = sub != null && !sub.isEmpty();
        File source = nested ? PathUtil.resolveWithinBase(trashedBase, sub) : trashedBase;
        if (!source.exists()) {
            throw new IOException("That item is no longer in the trash.");
        }

        String destRelPath = nested
            ? (e.originalRelPath.isEmpty() ? sub : e.originalRelPath + "/" + sub)
            : e.originalRelPath;
        File destination = new File(Settings.rootDir(), destRelPath);
        if (destination.exists()) {
            throw new IOException("Can't restore - \"" + source.getName() + "\" already exists at its original location.");
        }

        File parent = destination.getParentFile();
        if (!parent.exists()) parent.mkdirs();

        Files.move(source.toPath(), destination.toPath());
        if (!nested) {
            entries.remove(id);
            save();
        }
        return destRelPath;
    }

    /** Permanently deletes one item from the trash. */
    public static synchronized void permanentlyDelete(String id) throws IOException {
        permanentlyDelete(id, null);
    }

    // Same sub convention as restore() above: empty/null deletes the whole
    // trash entry (and its manifest record); set, deletes just that nested
    // file/folder from inside the trashed folder, leaving the rest of it
    // (and the manifest entry) alone.
    public static synchronized void permanentlyDelete(String id, String sub) throws IOException {
        Entry e = entries.get(id);
        if (e == null) throw new IOException("That item is no longer in the trash.");

        File trashedBase = new File(Config.TRASH_DIR, e.trashedName);
        boolean nested = sub != null && !sub.isEmpty();
        File target = nested ? PathUtil.resolveWithinBase(trashedBase, sub) : trashedBase;

        if (target.exists()) {
            if (target.isDirectory()) {
                deleteRecursively(target.toPath());
            } else {
                Files.delete(target.toPath());
            }
        }
        if (!nested) {
            entries.remove(id);
            save();
        }
    }

    /** Empties the entire trash. */
    public static synchronized void emptyTrash() throws IOException {
        for (String id : new ArrayList<>(entries.keySet())) {
            permanentlyDelete(id);
        }
    }

    public static synchronized Entry get(String id) {
        return entries.get(id);
    }

    public static List<Entry> list() {
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort((a, b) -> Long.compare(b.deletedTime, a.deletedTime)); // newest first
        return list;
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) { /* best-effort */ }
            });
        }
    }

    // ---------- Persistence (same pattern as RecentActivity) ----------

    private static synchronized void save() {
        try {
            if (!Config.DATA_DIR.exists()) Config.DATA_DIR.mkdirs();

            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"entries\": [\n");
            List<Entry> all = new ArrayList<>(entries.values());
            for (int i = 0; i < all.size(); i++) {
                Entry e = all.get(i);
                sb.append("    {")
                  .append("\"id\": \"").append(MiniJson.escape(e.id)).append("\", ")
                  .append("\"trashedName\": \"").append(MiniJson.escape(e.trashedName)).append("\", ")
                  .append("\"originalRelPath\": \"").append(MiniJson.escape(e.originalRelPath)).append("\", ")
                  .append("\"originalName\": \"").append(MiniJson.escape(e.originalName)).append("\", ")
                  .append("\"isDirectory\": ").append(e.isDirectory).append(", ")
                  .append("\"deletedTime\": ").append(e.deletedTime).append(", ")
                  .append("\"size\": ").append(e.size)
                  .append("}");
                sb.append(i < all.size() - 1 ? ",\n" : "\n");
            }
            sb.append("  ]\n}\n");

            Path tempFile = Files.createTempFile(Config.DATA_DIR.toPath(), "trash", ".tmp");
            Files.write(tempFile, sb.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tempFile, MANIFEST_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Warning: could not save trash manifest: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        if (!MANIFEST_FILE.exists()) return;
        try {
            String content = new String(Files.readAllBytes(MANIFEST_FILE.toPath()), StandardCharsets.UTF_8);
            Object parsed = MiniJson.parse(content);
            if (!(parsed instanceof Map)) return;
            Object arr = ((Map<String, Object>) parsed).get("entries");
            if (!(arr instanceof List)) return;

            for (Object item : (List<Object>) arr) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> obj = (Map<String, Object>) item;
                Entry e = new Entry();
                e.id = (String) obj.get("id");
                e.trashedName = (String) obj.get("trashedName");
                e.originalRelPath = (String) obj.get("originalRelPath");
                e.originalName = (String) obj.get("originalName");
                Object isDir = obj.get("isDirectory");
                e.isDirectory = Boolean.TRUE.equals(isDir);
                Object time = obj.get("deletedTime");
                e.deletedTime = time instanceof Double ? ((Double) time).longValue() : 0L;
                Object sizeObj = obj.get("size");
                e.size = sizeObj instanceof Double ? ((Double) sizeObj).longValue() : 0L;
                if (e.id != null) entries.put(e.id, e);
            }
        } catch (Exception e) {
            System.err.println("Warning: could not load trash manifest (starting fresh): " + e.getMessage());
        }
    }
}
