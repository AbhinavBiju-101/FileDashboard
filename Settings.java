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
    // Off by default: File Dashboard's actual point is browsing your own
    // local files (zero-upload, nothing leaves the machine - see the
    // project write-up). Drive integration only ever shows up in the UI,
    // and its routes only ever respond, once someone deliberately opts in
    // here - see GDriveGateFilter, which every /gdrive* and /gauth/* route
    // is wrapped in.
    private static volatile boolean gdriveExperimentalEnabled = false;
    // Off by default - see FileServer.java's bind address, which is
    // loopback-only (127.0.0.1) whenever this is false. That's the actual
    // fix for "anyone on the same network can reach this": if the socket
    // never listens on anything but the loopback interface, there is no
    // network path to it from another device, full stop - not a password
    // in front of an open door, no door at all. Turning this on is an
    // explicit, informed choice (see SettingsHandler.java's Network Access
    // section) for people who deliberately want to reach their own
    // dashboard from their own phone/tablet on their own home network -
    // which is exactly the case where accessToken below still matters.
    private static volatile boolean networkAccessEnabled = false;
    // Generated on first use (see getOrCreateAccessToken()), not before -
    // no point burning entropy/disk writes on a token that stays loopback-
    // only and irrelevant forever. Required (not optional) the moment
    // networkAccessEnabled is true - see AuthFilter.java.
    private static volatile String accessToken = null;

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

    public static boolean isGdriveExperimentalEnabled() {
        return gdriveExperimentalEnabled;
    }

    public static void setGdriveExperimentalEnabled(boolean enabled) {
        gdriveExperimentalEnabled = enabled;
        save();
    }

    public static boolean isNetworkAccessEnabled() {
        return networkAccessEnabled;
    }

    // Turning this on always makes sure a token exists first (see
    // getOrCreateAccessToken()) - there's never a moment where the setting
    // flips to "reachable from the network" without a token already in
    // place to require.
    public static void setNetworkAccessEnabled(boolean enabled) {
        if (enabled) getOrCreateAccessToken();
        networkAccessEnabled = enabled;
        save();
    }

    public static String getAccessToken() {
        return accessToken;
    }

    // Lazily generates one the first time it's actually needed (see
    // setNetworkAccessEnabled(true) above), rather than at first server
    // startup regardless of whether network access will ever be used.
    // 24 random bytes, base64url-encoded with padding stripped - ~32
    // characters, URL-safe as-is so it drops straight into a query param
    // or the Set-Cookie header AuthFilter.java sends after the first check.
    public static synchronized String getOrCreateAccessToken() {
        if (accessToken == null || accessToken.isEmpty()) {
            byte[] raw = new byte[24];
            new java.security.SecureRandom().nextBytes(raw);
            accessToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            save();
        }
        return accessToken;
    }

    public static synchronized String regenerateAccessToken() {
        byte[] raw = new byte[24];
        new java.security.SecureRandom().nextBytes(raw);
        accessToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        save();
        return accessToken;
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
            sb.append("  \"liveRefreshEnabled\": ").append(liveRefreshEnabled).append(",\n");
            sb.append("  \"gdriveExperimentalEnabled\": ").append(gdriveExperimentalEnabled).append(",\n");
            sb.append("  \"networkAccessEnabled\": ").append(networkAccessEnabled).append(",\n");
            sb.append("  \"accessToken\": ")
              .append(accessToken == null ? "null" : "\"" + MiniJson.escape(accessToken) + "\"").append("\n");
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

            Object gd = root.get("gdriveExperimentalEnabled");
            if (gd instanceof Boolean) gdriveExperimentalEnabled = (Boolean) gd;

            Object na = root.get("networkAccessEnabled");
            if (na instanceof Boolean) networkAccessEnabled = (Boolean) na;

            Object at = root.get("accessToken");
            accessToken = (at instanceof String) ? (String) at : null;
        } catch (Exception e) {
            System.err.println("Warning: could not load settings (using defaults): " + e.getMessage());
        }
    }
}
