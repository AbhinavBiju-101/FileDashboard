import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Handles "POST /gdrive-ops" - the Drive equivalent of FileOpsHandler.java,
 * same form-encoded-body/JSON-response shape, but working with Drive item
 * ids instead of local filesystem paths:
 *   - create-folder: account, parentId, newName
 *   - rename:        account, id, newName
 *   - delete:         account, id                  (moves to Drive's own trash)
 *   - duplicate:      account, id
 *   - move:           account, id, oldParentId, destId
 * Responds with {"success": true/false, "message": "...", "newId": "..."}
 * ("newId" only for create-folder/duplicate, where the client needs to know
 * what Drive assigned the new item so its context menu grid can be
 * refreshed/highlighted without a full reload).
 *
 * Every write here needs the broader read/write Drive scope (see
 * GDriveAuth.SCOPE) - an account connected before that scope existed will
 * get an insufficient-scope error back from Google, surfaced here as a
 * specific "reconnect this account from Settings" message rather than a
 * generic failure, since there's no way to tell ahead of time without
 * actually trying the call.
 */
public class GDriveOpsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String body = readAll(exchange.getRequestBody());
        String action = formParam(body, "action");
        String account = formParam(body, "account");
        String id = formParam(body, "id");
        String newName = formParam(body, "newName");
        String parentId = formParam(body, "parentId");
        String oldParentId = formParam(body, "oldParentId");
        String destId = formParam(body, "destId");

        String accountId = GDriveAuth.resolveAccount(account);
        if (accountId == null) {
            respondJson(exchange, false, "No Google account is connected.", null);
            return;
        }

        try {
            switch (action == null ? "" : action) {
                case "create-folder": {
                    String sanitized = sanitizeName(newName, "Folder");
                    String parent = (parentId == null || parentId.isEmpty()) ? "root" : parentId;
                    String newId = GDriveClient.createFolder(accountId, parent, sanitized);
                    respondJson(exchange, true, "Created " + sanitized, newId);
                    return;
                }
                case "rename": {
                    requireId(id);
                    String sanitized = sanitizeName(newName, null);
                    GDriveClient.renameItem(accountId, id, sanitized);
                    respondJson(exchange, true, "Renamed to " + sanitized, null);
                    return;
                }
                case "delete": {
                    requireId(id);
                    GDriveClient.trashItem(accountId, id);
                    respondJson(exchange, true, "Moved to Google Drive's trash", null);
                    return;
                }
                case "duplicate": {
                    requireId(id);
                    GDriveClient.DriveItem meta = GDriveClient.getMetadata(accountId, id);
                    String copyName = generateCopyName(meta.name);
                    String newId = GDriveClient.copyItem(accountId, id, copyName);
                    respondJson(exchange, true, "Created " + copyName, newId);
                    return;
                }
                case "move": {
                    requireId(id);
                    if (oldParentId == null || oldParentId.isEmpty()) throw new IOException("Missing source folder.");
                    if (destId == null || destId.isEmpty()) throw new IOException("Choose a destination folder.");
                    if (destId.equals(oldParentId)) throw new IOException("That's already where it is.");
                    if (destId.equals(id)) throw new IOException("Can't move a folder into itself.");
                    GDriveClient.moveItem(accountId, id, oldParentId, destId);
                    respondJson(exchange, true, "Moved", null);
                    return;
                }
                default:
                    respondJson(exchange, false, "Unknown action.", null);
            }
        } catch (IOException e) {
            respondJson(exchange, false, friendlyError(e), null);
        }
    }

    private void requireId(String id) throws IOException {
        if (id == null || id.isEmpty()) throw new IOException("Missing item id.");
    }

    private String sanitizeName(String name, String fallback) throws IOException {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            if (fallback != null) return fallback;
            throw new IOException("Name can't be empty.");
        }
        return trimmed;
    }

    private String generateCopyName(String originalName) {
        if (originalName == null) originalName = "Untitled";
        String base = originalName, ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) {
            base = originalName.substring(0, dot);
            ext = originalName.substring(dot);
        }
        return base + " (copy)" + ext;
    }

    // Google's insufficient-scope error surfaces as an HTTP 403 whose
    // message mentions "insufficient" (Google's exact wording has varied
    // historically, so this matches loosely rather than a fixed string).
    private String friendlyError(IOException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("HTTP 403") && msg.toLowerCase().contains("insufficient")) {
            return "This Google account needs to be reconnected from Settings to allow making changes to Drive.";
        }
        return msg.isEmpty() ? "Something went wrong." : msg;
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

    private void respondJson(HttpExchange exchange, boolean success, String message, String newId) throws IOException {
        StringBuilder json = new StringBuilder("{\"success\": ").append(success)
            .append(", \"message\": \"").append(MiniJson.escape(message == null ? "" : message)).append("\"");
        if (newId != null) {
            json.append(", \"newId\": \"").append(MiniJson.escape(newId)).append("\"");
        }
        json.append("}");
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
