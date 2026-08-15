import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the Google OAuth2 client config and tokens for the optional Google
 * Drive integration, persisted to Config.DATA_DIR/gdrive.json (same pattern
 * as Settings.java's settings.json, kept as a separate file since it holds
 * credentials rather than app preferences).
 *
 * One combined consent flow handles both identity and Drive access - the
 * requested scope includes openid/email/profile alongside drive.readonly,
 * so the single userinfo call completeAuth() makes after the token exchange
 * returns a name and profile picture too, not just an email. (An earlier
 * version of this split "sign in" and "connect Drive" into two separate
 * flows/buttons. That added a second consent screen and a second OAuth
 * client-type requirement - Google Identity Services' Sign In button needs
 * a "Web application" client with a JavaScript origin, which unlike a
 * "Desktop app" client requires a Client Secret - without actually reducing
 * the Google Cloud Console setup work, which is the same either way. Merged
 * back into one flow, one Desktop app client, no Secret needed.)
 *
 * This app has no server backend and no bundled client secret of its own -
 * connecting requires the person to create their own Google Cloud OAuth
 * client (Settings has the walkthrough + the exact redirect URI to
 * register) and paste its Client ID in. The flow uses PKCE, which is what
 * lets an installed/desktop app the size of this one skip needing to treat
 * the paired Client Secret as truly confidential - Google still issues one
 * for "Desktop app" client types, and it's accepted here if provided, but
 * it's not required to keep the flow secure.
 *
 * IMPORTANT: none of this has been exercised against real Google endpoints
 * - there's no network access to google.com in the sandbox this was
 * written in. The HTTP calls, JSON field names, and error handling are
 * written to match Google's documented OAuth2 + Drive v3 APIs, but treat
 * this as unverified until someone with an actual Google Cloud project
 * clicks through it for real. See TODO.md.
 */
public class GDriveAuth {

    private static final java.io.File CONFIG_FILE = new java.io.File(Config.DATA_DIR, "gdrive.json");

    // drive.readonly is the only scope this integration actually needs -
    // browsing/downloading, no writes back to Drive (see
    // GDriveBrowseHandler.java's class comment for why that's deliberate
    // for this first pass). openid/email/profile are along for the ride so
    // the one userinfo call completeAuth() already makes also returns a
    // name and profile picture - zero extra round trips for "Connected as
    // Jane Doe (jane@gmail.com)" instead of just the bare email.
    public static final String SCOPE = "openid email profile https://www.googleapis.com/auth/drive.readonly";

    private static volatile String clientId = null;
    private static volatile String clientSecret = null;
    private static volatile String accessToken = null;
    private static volatile String refreshToken = null;
    private static volatile long expiresAtMillis = 0;
    private static volatile String email = null;
    private static volatile String name = null;
    private static volatile String picture = null;

    // Short-lived, in-memory only: state -> PKCE code_verifier, for the
    // handful of seconds between redirecting to Google and it redirecting
    // back to /gauth/callback. Never persisted - there's no reason a token
    // exchange should still be pending after a server restart, and holding
    // it in memory means a stale entry just quietly stops mattering rather
    // than needing explicit cleanup.
    private static final Map<String, PendingAuth> pending = new ConcurrentHashMap<>();
    private static final long PENDING_TTL_MS = 10 * 60 * 1000;

    private static class PendingAuth {
        final String verifier;
        final long createdAt;
        PendingAuth(String verifier) { this.verifier = verifier; this.createdAt = System.currentTimeMillis(); }
    }

    static {
        load();
    }

    public static synchronized boolean isConfigured() {
        return clientId != null && !clientId.trim().isEmpty();
    }

    public static synchronized boolean isConnected() {
        return refreshToken != null && !refreshToken.isEmpty();
    }

    public static synchronized String getEmail() {
        return email;
    }

    public static synchronized String getName() {
        return name;
    }

    public static synchronized String getPicture() {
        return picture;
    }

    public static synchronized String getClientId() {
        return clientId;
    }

    public static synchronized String getClientSecret() {
        return clientSecret;
    }

    public static String redirectUri() {
        return "http://localhost:" + Config.PORT + "/gauth/callback";
    }

    public static synchronized void setClientCredentials(String id, String secret) {
        clientId = id == null ? null : id.trim();
        clientSecret = (secret == null || secret.trim().isEmpty()) ? null : secret.trim();
        save();
    }

    public static synchronized void disconnect() {
        accessToken = null;
        refreshToken = null;
        expiresAtMillis = 0;
        email = null;
        name = null;
        picture = null;
        save();
    }

    // ---------- OAuth round trip ----------

    // Starts the flow: mints a PKCE verifier/challenge pair and a random
    // state token, remembers the verifier under that state, and returns the
    // full Google consent-screen URL to redirect the browser to.
    public static String beginAuth() throws IOException {
        if (!isConfigured()) throw new IOException("Google Drive isn't configured yet - add a Client ID in Settings first.");
        cleanupPending();
        String state = randomUrlSafe(24);
        String verifier = randomUrlSafe(64);
        String challenge = codeChallenge(verifier);
        pending.put(state, new PendingAuth(verifier));

        StringBuilder url = new StringBuilder("https://accounts.google.com/o/oauth2/v2/auth?");
        url.append("client_id=").append(urlEncode(clientId));
        url.append("&redirect_uri=").append(urlEncode(redirectUri()));
        url.append("&response_type=code");
        url.append("&scope=").append(urlEncode(SCOPE));
        url.append("&access_type=offline");
        url.append("&prompt=consent");
        url.append("&state=").append(urlEncode(state));
        url.append("&code_challenge=").append(urlEncode(challenge));
        url.append("&code_challenge_method=S256");
        return url.toString();
    }

    // Finishes the flow: exchanges the authorization code for tokens (using
    // the verifier stashed under this state by beginAuth()), then fetches
    // the connected account's name/email/picture so Settings has something
    // human to show instead of just "Connected".
    public static synchronized void completeAuth(String code, String state) throws IOException {
        PendingAuth p = pending.remove(state);
        if (p == null) {
            throw new IOException("That connection link expired or was already used - try connecting again.");
        }
        Map<String, String> form = new HashMap<>();
        form.put("client_id", clientId);
        if (clientSecret != null) form.put("client_secret", clientSecret);
        form.put("code", code);
        form.put("code_verifier", p.verifier);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", redirectUri());

        Map<String, Object> resp = postForm("https://oauth2.googleapis.com/token", form);
        applyTokenResponse(resp);

        try {
            Map<String, Object> info = getJson("https://www.googleapis.com/oauth2/v2/userinfo",
                accessToken);
            Object em = info.get("email");
            if (em instanceof String) email = (String) em;
            Object nm = info.get("name");
            if (nm instanceof String) name = (String) nm;
            Object pic = info.get("picture");
            if (pic instanceof String) picture = (String) pic;
        } catch (IOException e) {
            // Not fatal - the connection itself succeeded, we just won't
            // have a friendly name/email/picture to display. Settings
            // falls back to plain "Connected" in that case.
            email = null;
            name = null;
            picture = null;
        }
        save();
    }

    // Returns a currently-usable access token, transparently refreshing it
    // first if it's missing or close to expiry. Every Drive API call in
    // GDriveClient.java goes through this rather than reading accessToken
    // directly.
    public static synchronized String getValidAccessToken() throws IOException {
        if (!isConnected()) throw new IOException("Google Drive isn't connected - connect it from Settings first.");
        if (accessToken == null || System.currentTimeMillis() > (expiresAtMillis - 60_000)) {
            refresh();
        }
        return accessToken;
    }

    private static void refresh() throws IOException {
        Map<String, String> form = new HashMap<>();
        form.put("client_id", clientId);
        if (clientSecret != null) form.put("client_secret", clientSecret);
        form.put("refresh_token", refreshToken);
        form.put("grant_type", "refresh_token");
        Map<String, Object> resp = postForm("https://oauth2.googleapis.com/token", form);
        applyTokenResponse(resp);
        save();
    }

    @SuppressWarnings("unchecked")
    private static void applyTokenResponse(Map<String, Object> resp) throws IOException {
        Object at = resp.get("access_token");
        if (!(at instanceof String)) {
            Object err = resp.get("error_description");
            throw new IOException("Google didn't return an access token" + (err != null ? (": " + err) : "."));
        }
        accessToken = (String) at;
        Object rt = resp.get("refresh_token");
        if (rt instanceof String) refreshToken = (String) rt; // only present on the very first exchange, usually
        Object exp = resp.get("expires_in");
        long expiresIn = (exp instanceof Double) ? ((Double) exp).longValue() : 3600;
        expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000;
    }

    private static void cleanupPending() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> now - e.getValue().createdAt > PENDING_TTL_MS);
    }

    // ---------- Small HTTP + PKCE helpers (no external dependencies) ----------

    private static Map<String, Object> postForm(String urlStr, Map<String, String> form) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readJsonResponse(conn);
    }

    static Map<String, Object> getJson(String urlStr, String bearerToken) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        if (bearerToken != null) conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        return readJsonResponse(conn);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJsonResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = is == null ? "" : readAll(is);
        Object parsed;
        try {
            parsed = MiniJson.parse(body);
        } catch (Exception e) {
            throw new IOException("Google returned a response that couldn't be parsed (HTTP " + status + "): " + body);
        }
        if (!(parsed instanceof Map)) {
            throw new IOException("Unexpected response from Google (HTTP " + status + ").");
        }
        Map<String, Object> map = (Map<String, Object>) parsed;
        if (status < 200 || status >= 300) {
            Object err = map.get("error_description");
            if (err == null) err = map.get("error");
            throw new IOException("Google API error (HTTP " + status + "): " + (err != null ? err : body));
        }
        return map;
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toString("UTF-8");
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String randomUrlSafe(int numBytes) {
        byte[] b = new byte[numBytes];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String codeChallenge(String verifier) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IOException("Could not compute PKCE challenge: " + e.getMessage());
        }
    }

    // ---------- Persistence ----------
    // Same atomic-temp-file-then-move pattern as Settings.java. This file
    // holds real credentials (refresh token especially), so it's worth
    // calling out that - unlike settings.json - it's not something to
    // casually share or commit anywhere.

    private static synchronized void save() {
        try {
            if (!Config.DATA_DIR.exists()) Config.DATA_DIR.mkdirs();
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"clientId\": ").append(jsonStr(clientId)).append(",\n");
            sb.append("  \"clientSecret\": ").append(jsonStr(clientSecret)).append(",\n");
            sb.append("  \"accessToken\": ").append(jsonStr(accessToken)).append(",\n");
            sb.append("  \"refreshToken\": ").append(jsonStr(refreshToken)).append(",\n");
            sb.append("  \"expiresAtMillis\": ").append(expiresAtMillis).append(",\n");
            sb.append("  \"email\": ").append(jsonStr(email)).append(",\n");
            sb.append("  \"name\": ").append(jsonStr(name)).append(",\n");
            sb.append("  \"picture\": ").append(jsonStr(picture)).append("\n");
            sb.append("}\n");

            Path tempFile = Files.createTempFile(Config.DATA_DIR.toPath(), "gdrive", ".tmp");
            Files.write(tempFile, sb.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tempFile, CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            try { CONFIG_FILE.setReadable(false, false); CONFIG_FILE.setReadable(true, true); } catch (Exception ignored) {}
            try { CONFIG_FILE.setWritable(false, false); CONFIG_FILE.setWritable(true, true); } catch (Exception ignored) {}
        } catch (IOException e) {
            System.err.println("Warning: could not save Google Drive config: " + e.getMessage());
        }
    }

    private static String jsonStr(String s) {
        return s == null ? "null" : "\"" + MiniJson.escape(s) + "\"";
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        if (!CONFIG_FILE.exists()) return;
        try {
            String content = new String(Files.readAllBytes(CONFIG_FILE.toPath()), StandardCharsets.UTF_8);
            Object parsed = MiniJson.parse(content);
            if (!(parsed instanceof Map)) return;
            Map<String, Object> root = (Map<String, Object>) parsed;
            Object v;
            v = root.get("clientId"); clientId = (v instanceof String) ? (String) v : null;
            v = root.get("clientSecret"); clientSecret = (v instanceof String) ? (String) v : null;
            v = root.get("accessToken"); accessToken = (v instanceof String) ? (String) v : null;
            v = root.get("refreshToken"); refreshToken = (v instanceof String) ? (String) v : null;
            v = root.get("expiresAtMillis"); expiresAtMillis = (v instanceof Double) ? ((Double) v).longValue() : 0;
            v = root.get("email"); email = (v instanceof String) ? (String) v : null;
            v = root.get("name"); name = (v instanceof String) ? (String) v : null;
            v = root.get("picture"); picture = (v instanceof String) ? (String) v : null;
        } catch (Exception e) {
            System.err.println("Warning: could not load Google Drive config: " + e.getMessage());
        }
    }
}
