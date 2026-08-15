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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the Google OAuth2 client config and tokens for the optional Google
 * Drive integration, persisted to Config.DATA_DIR/gdrive.json (same pattern
 * as Settings.java's settings.json, kept as a separate file since it holds
 * credentials rather than app preferences).
 *
 * Supports any number of simultaneously-connected Google accounts (see
 * Account below) rather than a single global one - each is independent:
 * its own tokens, its own name/email/picture, connected and disconnected
 * separately from Settings' "Connected accounts" list. A Google Drive
 * session (see ShellScript.java) picks one by id when it's created and
 * keeps using that same one; GDriveClient.java's calls all take an
 * accountId parameter for exactly this reason - there's no single "the"
 * token anymore.
 *
 * One combined consent flow handles both identity and Drive access per
 * account - the requested scope includes openid/email/profile alongside
 * drive.readonly, so the single userinfo call completeAuth() makes after
 * the token exchange returns a name and profile picture too, not just an
 * email. (An earlier version of this split "sign in" and "connect Drive"
 * into two separate flows/buttons. That added a second consent screen and a
 * second OAuth client-type requirement - Google Identity Services' Sign In
 * button needs a "Web application" client with a JavaScript origin, which
 * unlike a "Desktop app" client requires a Client Secret - without actually
 * reducing the Google Cloud Console setup work, which is the same either
 * way. Merged back into one flow, one Desktop app client, no Secret needed.)
 *
 * This app has no server backend and no bundled client secret of its own -
 * connecting requires the person to create their own Google Cloud OAuth
 * client (Settings has the walkthrough + the exact redirect URI to
 * register) and paste its Client ID in, once, shared by every account
 * connected through it. The flow uses PKCE, which is what lets an
 * installed/desktop app the size of this one skip needing to treat the
 * paired Client Secret as truly confidential - Google still issues one for
 * "Desktop app" client types, and it's accepted here if provided, but it's
 * not required to keep the flow secure.
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

    // drive.readonly alone no longer covers everything this app does -
    // GDriveOnboardingHandler.java's "create these organizing folders for
    // me" flow needs to create new folders, which needs write access. The
    // narrower https://www.googleapis.com/auth/drive.file scope was
    // considered instead (write access, but only to files the app itself
    // created) - rejected because it would also restrict *reading* to only
    // app-created files, breaking the whole point of this app: browsing a
    // person's existing, already-there Drive tree. So this is the full
    // read/write "drive" scope instead - broader than strictly necessary
    // for onboarding alone, but the only option that also preserves
    // unrestricted read access. openid/email/profile are along for the
    // ride so the one userinfo call completeAuth() already makes also
    // returns a name and profile picture - zero extra round trips for
    // "Connected as Jane Doe (jane@gmail.com)" instead of just the bare
    // email.
    //
    // Accounts connected before this scope existed only hold a
    // drive.readonly-scoped refresh token - Google will reject any write
    // call (e.g. folder creation) made with it as an insufficient-scope
    // error, not silently degrade. GDriveOnboardingHandler.java surfaces
    // that specific case as "reconnect this account from Settings to grant
    // folder-creation access" rather than a generic failure, but there's no
    // way to detect it ahead of time short of actually trying the call -
    // OAuth doesn't expose "what scope does my existing token actually
    // have" cheaply.
    public static final String SCOPE = "openid email profile https://www.googleapis.com/auth/drive";

    private static volatile String clientId = null;
    private static volatile String clientSecret = null;

    /** One connected Google account's tokens + display info. */
    public static class Account {
        public final String id;
        public String email;
        public String name;
        public String picture;
        public volatile String accessToken;
        public volatile String refreshToken;
        public volatile long expiresAtMillis;
        Account(String id) { this.id = id; }
    }

    /** Read-only display info handed out to callers outside this class - never carries tokens. */
    public static class AccountInfo {
        public final String id, email, name, picture;
        public AccountInfo(String id, String email, String name, String picture) {
            this.id = id; this.email = email; this.name = name; this.picture = picture;
        }
        public String displayName() { return name != null ? name : (email != null ? email : "(unknown account)"); }
    }

    // Keyed by our own generated account id, not Google's - see connect()'s
    // dedupe-by-email logic for why a stable id independent of email is
    // useful (an email is how we recognize "you already connected this
    // one", but the id is what everything else - sessions, URLs - remembers
    // long-term).
    private static final Map<String, Account> accounts = new LinkedHashMap<>();

    // Short-lived, in-memory only: state -> PKCE code_verifier (+ which
    // flow kicked it off), for the handful of seconds between redirecting
    // to Google and it redirecting back to /gauth/callback. Never
    // persisted - there's no reason a token exchange should still be
    // pending after a server restart, and holding it in memory means a
    // stale entry just quietly stops mattering rather than needing
    // explicit cleanup.
    private static final Map<String, PendingAuth> pending = new ConcurrentHashMap<>();
    private static final long PENDING_TTL_MS = 10 * 60 * 1000;

    private static class PendingAuth {
        final String verifier;
        final long createdAt;
        PendingAuth(String verifier) { this.verifier = verifier; this.createdAt = System.currentTimeMillis(); }
    }

    public static class AuthResult {
        public final String accountId;
        public AuthResult(String accountId) { this.accountId = accountId; }
    }

    // Pulled straight out of the "state" query param on the callback -
    // works even in early-exit error paths (denied/cancelled) where
    // completeAuth() never runs, since it's plain string parsing rather
    // than a pending-map lookup. Falls back to "settings" for a
    // missing/malformed state, matching the default beginAuth() itself
    // uses for an unrecognized/absent context.
    public static String contextFromState(String state) {
        if (state == null) return "settings";
        int i = state.indexOf('~');
        return i == -1 ? "settings" : state.substring(0, i);
    }

    static {
        load();
    }

    public static synchronized boolean isConfigured() {
        return clientId != null && !clientId.trim().isEmpty();
    }

    public static synchronized boolean hasAnyAccount() {
        return !accounts.isEmpty();
    }

    // Sorted by display name/email so Settings and the account picker show
    // a stable, predictable order rather than insertion/connection order.
    public static synchronized List<AccountInfo> listAccounts() {
        List<AccountInfo> out = new ArrayList<>();
        for (Account a : accounts.values()) {
            out.add(new AccountInfo(a.id, a.email, a.name, a.picture));
        }
        out.sort(Comparator.comparing(a -> a.displayName().toLowerCase()));
        return out;
    }

    public static synchronized AccountInfo getAccountInfo(String accountId) {
        Account a = accounts.get(accountId);
        return a == null ? null : new AccountInfo(a.id, a.email, a.name, a.picture);
    }

    // Used by every Drive-facing handler to turn a possibly-missing/stale
    // "account" query param into an actual account to browse as: the
    // requested one if it's still connected, otherwise a reasonable
    // fallback (the first connected account, alphabetically) rather than
    // failing outright - covers old bookmarked/saved-session URLs from
    // before a session remembered which account it used, and the odd case
    // of an account being disconnected out from under a session that's
    // still pointed at it. Returns null only when no account is connected
    // at all.
    public static synchronized String resolveAccount(String requestedId) {
        if (requestedId != null && accounts.containsKey(requestedId)) return requestedId;
        List<AccountInfo> all = listAccounts();
        return all.isEmpty() ? null : all.get(0).id;
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

    public static synchronized void disconnect(String accountId) {
        accounts.remove(accountId);
        save();
        UserDataStore.clearAccount(accountId);
    }

    // ---------- OAuth round trip ----------

    // Starts the flow: mints a PKCE verifier/challenge pair and a random
    // state token, remembers the verifier under that state, and returns the
    // full Google consent-screen URL to redirect the browser to. Adding a
    // second (or third...) account works exactly the same way as the first
    // - "prompt=consent" always shows the account chooser + consent screen
    // rather than silently reusing whatever Google session cookie the
    // browser already has, so picking a different account to connect is
    // always possible, not just the first time.
    //
    // context is opaque to this class - it's folded into the state token
    // itself (rather than tracked here) purely so GoogleAuthHandler.java's
    // callback can tell which of its two return-trip behaviors to use
    // (redirect back to the Settings page vs. a self-closing popup page for
    // the account picker) without this class needing to expose anything
    // extra to look it up. See GoogleAuthHandler's class comment.
    public static String beginAuth(String context) throws IOException {
        if (!isConfigured()) throw new IOException("Google Drive isn't configured yet - add a Client ID in Settings first.");
        cleanupPending();
        String safeContext = (context == null || context.indexOf('~') != -1) ? "settings" : context;
        String state = safeContext + "~" + randomUrlSafe(24);
        String verifier = randomUrlSafe(64);
        String challenge = codeChallenge(verifier);
        pending.put(state, new PendingAuth(verifier));

        StringBuilder url = new StringBuilder("https://accounts.google.com/o/oauth2/v2/auth?");
        url.append("client_id=").append(urlEncode(clientId));
        url.append("&redirect_uri=").append(urlEncode(redirectUri()));
        url.append("&response_type=code");
        url.append("&scope=").append(urlEncode(SCOPE));
        url.append("&access_type=offline");
        url.append("&prompt=").append(urlEncode("consent select_account"));
        url.append("&state=").append(urlEncode(state));
        url.append("&code_challenge=").append(urlEncode(challenge));
        url.append("&code_challenge_method=S256");
        return url.toString();
    }

    // Finishes the flow: exchanges the authorization code for tokens (using
    // the verifier stashed under this state by beginAuth()), fetches the
    // connected account's name/email/picture, then upserts it into the
    // accounts map - keyed by matching email if this exact Google account
    // was already connected before (refreshing its tokens in place rather
    // than creating a confusing duplicate entry), or a freshly generated id
    // otherwise. Returns the id of whichever account this connected/updated.
    public static synchronized AuthResult completeAuth(String code, String state) throws IOException {
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
        String accessToken = str(resp.get("access_token"));
        if (accessToken == null) {
            Object err = resp.get("error_description");
            throw new IOException("Google didn't return an access token" + (err != null ? (": " + err) : "."));
        }
        String refreshToken = str(resp.get("refresh_token"));
        Object exp = resp.get("expires_in");
        long expiresIn = (exp instanceof Double) ? ((Double) exp).longValue() : 3600;
        long expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000;

        String email = null, name = null, picture = null;
        try {
            Map<String, Object> info = getJson("https://www.googleapis.com/oauth2/v2/userinfo", accessToken);
            email = str(info.get("email"));
            name = str(info.get("name"));
            picture = str(info.get("picture"));
        } catch (IOException e) {
            // Not fatal - the connection itself succeeded, we just won't
            // have a friendly name/picture to display for it.
        }

        Account existing = email == null ? null : findByEmail(email);
        Account account = existing != null ? existing : new Account(randomUrlSafe(12));
        account.email = email;
        account.name = name;
        account.picture = picture;
        account.accessToken = accessToken;
        // Google only returns a refresh_token on the very first consent for
        // a given client+account; reconnecting an already-connected account
        // (e.g. after a scope change) may not get a new one, so keep the
        // old one rather than clobbering it with null.
        if (refreshToken != null) account.refreshToken = refreshToken;
        account.expiresAtMillis = expiresAtMillis;
        accounts.put(account.id, account);
        save();
        return new AuthResult(account.id);
    }

    private static Account findByEmail(String email) {
        for (Account a : accounts.values()) {
            if (email.equalsIgnoreCase(a.email)) return a;
        }
        return null;
    }

    // Returns a currently-usable access token for this account,
    // transparently refreshing it first if it's missing or close to
    // expiry. Every Drive API call in GDriveClient.java goes through this
    // rather than reading an Account's accessToken directly.
    public static synchronized String getValidAccessToken(String accountId) throws IOException {
        Account a = accounts.get(accountId);
        if (a == null || a.refreshToken == null || a.refreshToken.isEmpty()) {
            throw new IOException("That Google account isn't connected - connect it from Settings first.");
        }
        if (a.accessToken == null || System.currentTimeMillis() > (a.expiresAtMillis - 60_000)) {
            refresh(a);
        }
        return a.accessToken;
    }

    private static void refresh(Account a) throws IOException {
        Map<String, String> form = new HashMap<>();
        form.put("client_id", clientId);
        if (clientSecret != null) form.put("client_secret", clientSecret);
        form.put("refresh_token", a.refreshToken);
        form.put("grant_type", "refresh_token");
        Map<String, Object> resp = postForm("https://oauth2.googleapis.com/token", form);
        Object at = resp.get("access_token");
        if (!(at instanceof String)) {
            Object err = resp.get("error_description");
            throw new IOException("Google didn't return an access token" + (err != null ? (": " + err) : "."));
        }
        a.accessToken = (String) at;
        Object rt = resp.get("refresh_token");
        if (rt instanceof String) a.refreshToken = (String) rt;
        Object exp = resp.get("expires_in");
        long expiresIn = (exp instanceof Double) ? ((Double) exp).longValue() : 3600;
        a.expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000;
        save();
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

    // POSTs a raw JSON body with bearer auth - used by GDriveClient.java's
    // createFolder() (Drive API's files.create). Distinct from postForm()
    // above, which is form-urlencoded and client-credential-authenticated
    // (the token endpoint) rather than bearer-token-authenticated (the
    // Drive API itself).
    static Map<String, Object> postJson(String urlStr, String bearerToken, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        return readJsonResponse(conn);
    }

    // Same as postJson but PATCH - Drive API's files.update (rename, trash,
    // and reparent-for-move all go through this one verb) doesn't support
    // POST, and HttpURLConnection itself has no real PATCH support (its
    // request-method validation only allows a fixed list, and the common
    // "reflect into the private method field" workaround doesn't survive
    // module encapsulation on modern JDKs - throws
    // InaccessibleObjectException without a --add-opens flag this app
    // doesn't set anywhere). Google's APIs document exactly this problem's
    // fix: send it as a POST with an X-HTTP-Method-Override: PATCH header,
    // which the server treats identically to a real PATCH.
    static Map<String, Object> patchJson(String urlStr, String bearerToken, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
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
            // Two different error shapes to account for here: the OAuth
            // token endpoint's flat {"error_description": "..."} (postForm's
            // callers), and the Drive API's own nested
            // {"error": {"message": "...", ...}} (getJson/postJson's Drive
            // v3 callers) - checked in that order so an OAuth error's flat
            // description is preferred when both happen to be present.
            Object err = map.get("error_description");
            if (err == null) {
                Object errObj = map.get("error");
                if (errObj instanceof Map) {
                    Object msg = ((Map<String, Object>) errObj).get("message");
                    err = msg != null ? msg : errObj;
                } else {
                    err = errObj;
                }
            }
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

    private static String str(Object o) {
        return o instanceof String ? (String) o : null;
    }

    // ---------- Persistence ----------
    // Same atomic-temp-file-then-move pattern as Settings.java. This file
    // holds real credentials (refresh tokens especially), so it's worth
    // calling out that - unlike settings.json - it's not something to
    // casually share or commit anywhere.

    private static synchronized void save() {
        try {
            if (!Config.DATA_DIR.exists()) Config.DATA_DIR.mkdirs();
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"clientId\": ").append(jsonStr(clientId)).append(",\n");
            sb.append("  \"clientSecret\": ").append(jsonStr(clientSecret)).append(",\n");
            sb.append("  \"accounts\": [\n");
            int i = 0, total = accounts.size();
            for (Account a : accounts.values()) {
                sb.append("    {\n");
                sb.append("      \"id\": ").append(jsonStr(a.id)).append(",\n");
                sb.append("      \"email\": ").append(jsonStr(a.email)).append(",\n");
                sb.append("      \"name\": ").append(jsonStr(a.name)).append(",\n");
                sb.append("      \"picture\": ").append(jsonStr(a.picture)).append(",\n");
                sb.append("      \"accessToken\": ").append(jsonStr(a.accessToken)).append(",\n");
                sb.append("      \"refreshToken\": ").append(jsonStr(a.refreshToken)).append(",\n");
                sb.append("      \"expiresAtMillis\": ").append(a.expiresAtMillis).append("\n");
                sb.append("    }").append(++i < total ? "," : "").append("\n");
            }
            sb.append("  ]\n");
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

    // Understands both the current multi-account format ("accounts": [...])
    // and the older single-account format this file used to be saved in
    // (bare accessToken/refreshToken/email/name/picture at the top level) -
    // any existing gdrive.json from before this app supported more than one
    // account still loads correctly, migrated in-memory into a one-entry
    // accounts list on first save after this update.
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

            Object accountsObj = root.get("accounts");
            if (accountsObj instanceof List) {
                for (Object o : (List<Object>) accountsObj) {
                    if (!(o instanceof Map)) continue;
                    Map<String, Object> m = (Map<String, Object>) o;
                    String id = str(m.get("id"));
                    if (id == null) continue;
                    Account a = new Account(id);
                    a.email = str(m.get("email"));
                    a.name = str(m.get("name"));
                    a.picture = str(m.get("picture"));
                    a.accessToken = str(m.get("accessToken"));
                    a.refreshToken = str(m.get("refreshToken"));
                    Object exp = m.get("expiresAtMillis");
                    a.expiresAtMillis = (exp instanceof Double) ? ((Double) exp).longValue() : 0;
                    accounts.put(a.id, a);
                }
            } else if (root.get("refreshToken") instanceof String) {
                // Legacy single-account file - migrate it into one entry.
                Account a = new Account(randomUrlSafe(12));
                a.email = str(root.get("email"));
                a.name = str(root.get("name"));
                a.picture = str(root.get("picture"));
                a.accessToken = str(root.get("accessToken"));
                a.refreshToken = str(root.get("refreshToken"));
                Object exp = root.get("expiresAtMillis");
                a.expiresAtMillis = (exp instanceof Double) ? ((Double) exp).longValue() : 0;
                accounts.put(a.id, a);
            }
        } catch (Exception e) {
            System.err.println("Warning: could not load Google Drive config: " + e.getMessage());
        }
    }
}
