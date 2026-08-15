import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Runtime-configurable settings, persisted to Config.DATA_DIR/settings.json.
 * Config.java's constants remain the compile-time defaults; anything set
 * here overrides them for the current and future runs, without needing to
 * edit source and recompile - specifically so "which folder does Home open"
 * can be changed from the Settings page instead of Config.java.
 */
public class Settings {

    private static final File SETTINGS_FILE = new File(Config.DATA_DIR, "settings.json");

    private static volatile String rootDirOverride = null;
    private static volatile int dashboardMaxItems = 20;
    private static volatile boolean liveRefreshEnabled = true;

    static {
        load();
    }

    // The folder "Home" actually opens - the override if one is set and
    // still valid, otherwise Config.java's default.
    public static File rootDir() {
        if (rootDirOverride != null && !rootDirOverride.isEmpty()) {
            File f = new File(rootDirOverride);
            if (f.isDirectory()) return f;
        }
        return Config.ROOT_DIR;
    }

    public static String getRootDirOverride() {
        return rootDirOverride;
    }

    // Returns null on success, or a human-readable error message.
    public static String setRootDirOverride(String path) {
        if (path == null) path = "";
        path = path.trim();
        if (path.isEmpty()) {
            rootDirOverride = null;
            save();
            return null;
        }
        File f = new File(path);
        if (!f.isDirectory()) {
            return "That folder doesn't exist: " + path;
        }
        rootDirOverride = f.getAbsolutePath();
        save();
        return null;
    }

    public static int getDashboardMaxItems() {
        return dashboardMaxItems;
    }

    public static void setDashboardMaxItems(int n) {
        dashboardMaxItems = Math.max(1, Math.min(100, n));
        save();
    }

    public static boolean isLiveRefreshEnabled() {
        return liveRefreshEnabled;
    }

    public static void setLiveRefreshEnabled(boolean enabled) {
        liveRefreshEnabled = enabled;
        save();
    }

    // ---------- Persistence ----------

    private static synchronized void save() {
        try {
            if (!Config.DATA_DIR.exists()) Config.DATA_DIR.mkdirs();
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"rootDirOverride\": ")
              .append(rootDirOverride == null ? "null" : "\"" + MiniJson.escape(rootDirOverride) + "\"")
              .append(",\n");
            sb.append("  \"dashboardMaxItems\": ").append(dashboardMaxItems).append(",\n");
            sb.append("  \"liveRefreshEnabled\": ").append(liveRefreshEnabled).append("\n");
            sb.append("}\n");

            Path tempFile = Files.createTempFile(Config.DATA_DIR.toPath(), "settings", ".tmp");
            Files.write(tempFile, sb.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tempFile, SETTINGS_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Warning: could not save settings: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        if (!SETTINGS_FILE.exists()) return;
        try {
            String content = new String(Files.readAllBytes(SETTINGS_FILE.toPath()), StandardCharsets.UTF_8);
            Object parsed = MiniJson.parse(content);
            if (!(parsed instanceof Map)) return;
            Map<String, Object> root = (Map<String, Object>) parsed;

            Object rd = root.get("rootDirOverride");
            rootDirOverride = (rd instanceof String) ? (String) rd : null;

            Object dm = root.get("dashboardMaxItems");
            if (dm instanceof Double) dashboardMaxItems = ((Double) dm).intValue();

            Object lr = root.get("liveRefreshEnabled");
            if (lr instanceof Boolean) liveRefreshEnabled = (Boolean) lr;
        } catch (Exception e) {
            System.err.println("Warning: could not load settings (using defaults): " + e.getMessage());
        }
    }
}
