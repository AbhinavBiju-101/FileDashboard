import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles "/gdrive-onboarding" - the "let's organize this Drive" flow
 * offered the first time a Drive session is opened for a given Google
 * account (see ShellScript.java's shellApplyDriveSidebar(), which is what
 * actually calls this and reacts to the result).
 *
 * DEFAULT_FOLDERS is the fixed set of top-level organizing folders this
 * offers to create at Drive's root: Pictures, Videos, Uploads, Documents,
 * Music - picked to mirror the classic OS folders SidebarRenderer.java
 * already shows for local browsing, so "Home folders" feels familiar in
 * either mode. Nothing stops someone from declining every one of them (see
 * "skipped" below) if their Drive is already organized the way they like.
 *
 * GET  ?account=X            - status check: {"status": "done"|"skipped"|"needs-prompt", "folders": {...}}
 * POST action=create&account=X&folders=Pictures,Documents,...   - creates whichever of those don't already exist, records "done"
 * POST action=skip&account=X                                     - records "skipped", no folders created or shown
 *
 * Detection (the GET path, when there's no record yet in
 * UserDataStore.java) works by actually checking Drive's root for folders
 * already named after any of these defaults, rather than only trusting
 * whatever this app itself remembers - covers both "this Drive was already
 * organized this way before ever connecting to this app" and "the
 * app's own data file got cleared/reset but the Drive itself didn't
 * change". Once a status is determined one way or another it's cached in
 * UserDataStore so this doesn't re-probe Drive's root on every single
 * request - the tradeoff is that manually deleting all the default folders
 * later won't bring the prompt back on its own; a fresh probe only happens
 * again if that account's onboarding record is ever cleared (e.g.
 * disconnecting and reconnecting the account - see GDriveAuth.disconnect()).
 */
public class GDriveOnboardingHandler implements HttpHandler {

    // name -> emoji icon, in the order offered/created. LinkedHashMap so
    // both orderings (the offer list and the eventual sidebar shortcuts)
    // stay predictable rather than however a plain HashMap happens to
    // iterate.
    public static final Map<String, String> DEFAULT_FOLDERS = new LinkedHashMap<>();
    static {
        DEFAULT_FOLDERS.put("Pictures", "&#128247;");
        DEFAULT_FOLDERS.put("Videos", "&#127909;");
        DEFAULT_FOLDERS.put("Documents", "&#128196;");
        DEFAULT_FOLDERS.put("Music", "&#127925;");
        DEFAULT_FOLDERS.put("Uploads", "&#11014;&#65039;");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            handleStatus(exchange);
        } else if ("POST".equalsIgnoreCase(method)) {
            handleAction(exchange);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String account = QueryUtil.getParam(query, "account");
        account = account == null ? null : URLDecoder.decode(account, "UTF-8");
        String accountId = GDriveAuth.resolveAccount(account);
        if (accountId == null) {
            respondJson(exchange, "needs-prompt", null, "No Google account is connected.");
            return;
        }

        UserDataStore.DriveOnboarding existing = UserDataStore.getDriveOnboarding(accountId);
        if (existing != null) {
            respondJson(exchange, existing.status, existing.folders, null);
            return;
        }

        // No record yet - probe Drive's own root before assuming this
        // account has never seen onboarding (see class comment).
        try {
            List<GDriveClient.DriveItem> rootItems = GDriveClient.listChildren(accountId, "root");
            Map<String, String> found = new LinkedHashMap<>();
            for (GDriveClient.DriveItem item : rootItems) {
                if (!GDriveClient.isFolder(item.mimeType)) continue;
                if (DEFAULT_FOLDERS.containsKey(item.name) && !found.containsKey(item.name)) {
                    found.put(item.name, item.id);
                }
            }
            if (!found.isEmpty()) {
                UserDataStore.setDriveOnboarding(accountId, "done", found);
                respondJson(exchange, "done", found, null);
            } else {
                respondJson(exchange, "needs-prompt", null, null);
            }
        } catch (IOException e) {
            // Couldn't check - don't nag with a prompt over a transient
            // network hiccup, but don't claim "done" either; the client
            // just tries again next time a Drive session opens.
            respondJson(exchange, "unknown", null, e.getMessage());
        }
    }

    private void handleAction(HttpExchange exchange) throws IOException {
        String body = readAll(exchange.getRequestBody());
        String action = formParam(body, "action");
        String account = formParam(body, "account");
        String foldersParam = formParam(body, "folders");
        String accountId = GDriveAuth.resolveAccount(account);
        if (accountId == null) {
            respondError(exchange, "No Google account is connected.");
            return;
        }

        if ("skip".equals(action)) {
            UserDataStore.setDriveOnboarding(accountId, "skipped", null);
            respondJson(exchange, "skipped", null, null);
            return;
        }
        if (!"create".equals(action)) {
            respondError(exchange, "Unknown action.");
            return;
        }

        String[] requested = (foldersParam == null || foldersParam.isEmpty()) ? new String[0] : foldersParam.split(",");
        try {
            List<GDriveClient.DriveItem> rootItems = GDriveClient.listChildren(accountId, "root");
            Map<String, String> existingByName = new LinkedHashMap<>();
            for (GDriveClient.DriveItem item : rootItems) {
                if (GDriveClient.isFolder(item.mimeType)) existingByName.put(item.name, item.id);
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (String name : requested) {
                if (!DEFAULT_FOLDERS.containsKey(name)) continue; // ignore anything not in our known set
                if (existingByName.containsKey(name)) {
                    result.put(name, existingByName.get(name)); // already there - reuse it, don't make a duplicate
                } else {
                    result.put(name, GDriveClient.createFolder(accountId, "root", name));
                }
            }
            UserDataStore.setDriveOnboarding(accountId, "done", result);
            respondJson(exchange, "done", result, null);
        } catch (IOException e) {
            respondError(exchange, friendlyError(e));
        }
    }

    private String friendlyError(IOException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("HTTP 403") && msg.toLowerCase().contains("insufficient")) {
            return "This Google account needs to be reconnected from Settings to allow creating folders in Drive.";
        }
        return msg.isEmpty() ? "Something went wrong." : msg;
    }

    private void respondJson(HttpExchange exchange, String status, Map<String, String> folders, String error) throws IOException {
        StringBuilder json = new StringBuilder("{\"status\":\"").append(MiniJson.escape(status)).append("\"");
        json.append(",\"folders\":{");
        if (folders != null) {
            int i = 0, n = folders.size();
            for (Map.Entry<String, String> e : folders.entrySet()) {
                json.append("\"").append(MiniJson.escape(e.getKey())).append("\":\"").append(MiniJson.escape(e.getValue())).append("\"");
                if (++i < n) json.append(",");
            }
        }
        json.append("}");
        if (error != null) {
            json.append(",\"error\":\"").append(MiniJson.escape(error)).append("\"");
        }
        json.append("}");
        writeJson(exchange, json.toString());
    }

    private void respondError(HttpExchange exchange, String message) throws IOException {
        writeJson(exchange, "{\"status\":\"error\",\"folders\":{},\"error\":\"" + MiniJson.escape(message) + "\"}");
    }

    private void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String formParam(String body, String key) {
        if (body == null) return null;
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx == -1) continue;
            if (pair.substring(0, idx).equals(key)) {
                try {
                    return URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                } catch (Exception e) {
                    return pair.substring(idx + 1);
                }
            }
        }
        return null;
    }

    private String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toString("UTF-8");
    }
}
