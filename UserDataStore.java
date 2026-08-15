import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The app's user-data store, persisted to Config.DATA_DIR/userdata.json.
 * Kept deliberately separate from GDriveAuth.java's gdrive.json (that one
 * holds nothing but OAuth credentials) and Settings.java's settings.json
 * (app-wide preferences, not tied to any one identity) - this one is where
 * per-account, per-person data belongs, e.g. GDriveOnboardingHandler.java's
 * "has this Google account been through Drive folder onboarding yet"
 * record below.
 *
 * IMPORTANT - what this actually is right now: one atomically-written JSON
 * file, read into memory at startup and rewritten on every change, using
 * the exact same pattern Settings.java and GDriveAuth.java already use.
 * Genuinely calling this "a database" is a bit generous for what's really
 * a flat file - but it's built with that migration in mind: every record is
 * already addressed by a stable key (an account id) the same way a real
 * table's primary key would be, and every accessor here (get/put/query)
 * already goes through this one class rather than being read/written
 * ad hoc elsewhere, so swapping the storage engine out later - a real
 * embedded SQL database (SQLite is the obvious choice) once this project
 * has a build tool that can actually pull in a JDBC driver - only ever
 * touches this file, not every call site. There's no dependency manager or
 * network access to a Maven repository in the environment this was written
 * in, so a real SQLite/JDBC dependency genuinely isn't fetchable here; this
 * is the honest version of "a database" that's actually buildable today
 * without lying about what's backing it. See TODO.md.
 *
 * Record shape, one per Google account id (see GDriveAuth.java):
 *   {
 *     "driveOnboarding": {
 *       "status": "done" | "skipped",
 *       "folders": { "Pictures": "<driveFolderId>", "Documents": "...", ... },
 *       "updatedAt": <epoch millis>
 *     }
 *   }
 * Nothing else is stored per-account yet - the shape is a Map<String,Object>
 * per account rather than a fixed class specifically so a second feature
 * needing its own per-account record (a future upgrade, per the person who
 * asked for this) can add its own top-level key without a schema migration.
 */
public class UserDataStore {

    private static final java.io.File DATA_FILE = new java.io.File(Config.DATA_DIR, "userdata.json");

    // accountId -> record. ConcurrentHashMap since Drive requests (and
    // therefore onboarding checks) can come from multiple browser tabs'
    // worth of concurrent HTTP requests.
    private static final Map<String, Map<String, Object>> accounts = new ConcurrentHashMap<>();

    static {
        load();
    }

    /** The onboarding record for one account, or null if it's never been recorded. */
    public static class DriveOnboarding {
        public final String status; // "done" | "skipped"
        public final Map<String, String> folders; // folder name -> Drive folder id, e.g. {"Pictures": "1abc..."}
        public final long updatedAt;
        DriveOnboarding(String status, Map<String, String> folders, long updatedAt) {
            this.status = status;
            this.folders = folders;
            this.updatedAt = updatedAt;
        }
        public boolean isDone() { return "done".equals(status); }
        public boolean isSkipped() { return "skipped".equals(status); }
    }

    @SuppressWarnings("unchecked")
    public static DriveOnboarding getDriveOnboarding(String accountId) {
        if (accountId == null) return null;
        Map<String, Object> account = accounts.get(accountId);
        if (account == null) return null;
        Object raw = account.get("driveOnboarding");
        if (!(raw instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) raw;
        String status = str(m.get("status"));
        if (status == null) return null;
        Map<String, String> folders = new LinkedHashMap<>();
        Object foldersObj = m.get("folders");
        if (foldersObj instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) foldersObj).entrySet()) {
                if (e.getValue() instanceof String) folders.put(e.getKey(), (String) e.getValue());
            }
        }
        Object updated = m.get("updatedAt");
        long updatedAt = (updated instanceof Double) ? ((Double) updated).longValue() : 0;
        return new DriveOnboarding(status, folders, updatedAt);
    }

    public static synchronized void setDriveOnboarding(String accountId, String status, Map<String, String> folders) {
        if (accountId == null) return;
        Map<String, Object> account = accounts.computeIfAbsent(accountId, k -> new LinkedHashMap<>());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("status", status);
        record.put("folders", folders == null ? new LinkedHashMap<>() : folders);
        record.put("updatedAt", System.currentTimeMillis());
        account.put("driveOnboarding", record);
        save();
    }

    public static synchronized void clearAccount(String accountId) {
        if (accountId == null) return;
        if (accounts.remove(accountId) != null) save();
    }

    private static String str(Object o) {
        return o instanceof String ? (String) o : null;
    }

    // ---------- Persistence (same atomic-temp-file-then-move pattern as Settings.java/GDriveAuth.java) ----------

    private static synchronized void save() {
        try {
            if (!Config.DATA_DIR.exists()) Config.DATA_DIR.mkdirs();
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"accounts\": {\n");
            List<String> ids = new ArrayList<>(accounts.keySet());
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                sb.append("    \"").append(MiniJson.escape(id)).append("\": ");
                sb.append(toJson(accounts.get(id), 4));
                sb.append(i < ids.size() - 1 ? ",\n" : "\n");
            }
            sb.append("  }\n}\n");

            Path tempFile = Files.createTempFile(Config.DATA_DIR.toPath(), "userdata", ".tmp");
            Files.write(tempFile, sb.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tempFile, DATA_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Warning: could not save user data: " + e.getMessage());
        }
    }

    // Minimal recursive JSON writer for the Map<String,Object>/Map<String,String>
    // shapes this file actually stores - not a general-purpose serializer,
    // just enough for records built out of Map/String/Number.
    @SuppressWarnings("unchecked")
    private static String toJson(Object value, int indent) {
        String pad = " ".repeat(indent), padIn = " ".repeat(indent + 2);
        if (value instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) value;
            if (m.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder("{\n");
            int i = 0, n = m.size();
            for (Map.Entry<String, Object> e : m.entrySet()) {
                sb.append(padIn).append("\"").append(MiniJson.escape(e.getKey())).append("\": ").append(toJson(e.getValue(), indent + 2));
                sb.append(++i < n ? ",\n" : "\n");
            }
            sb.append(pad).append("}");
            return sb.toString();
        } else if (value instanceof String) {
            return "\"" + MiniJson.escape((String) value) + "\"";
        } else if (value instanceof Number) {
            return String.valueOf(((Number) value).longValue());
        } else {
            return "null";
        }
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        if (!DATA_FILE.exists()) return;
        try {
            String content = new String(Files.readAllBytes(DATA_FILE.toPath()), StandardCharsets.UTF_8);
            Object parsed = MiniJson.parse(content);
            if (!(parsed instanceof Map)) return;
            Object accountsObj = ((Map<String, Object>) parsed).get("accounts");
            if (!(accountsObj instanceof Map)) return;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) accountsObj).entrySet()) {
                if (e.getValue() instanceof Map) {
                    accounts.put(e.getKey(), (Map<String, Object>) e.getValue());
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: could not load user data: " + e.getMessage());
        }
    }
}
