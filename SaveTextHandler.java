import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Handles "POST /save-text" - overwrites a text/markdown/code file with
 * new content from the Viewer's edit mode. Deliberately ROOT_DIR-only
 * (via PathUtil.resolve, not resolvePathOrTrash): trashed items stay
 * read-only, same as everywhere else in the app, so nobody can edit
 * something on its way to being restored/deleted out from under it.
 *
 * Refuses to touch anything that doesn't already look text-like
 * (ViewabilityUtil.isTextLike) - editing here is meant for notes/config/
 * code files, not a general-purpose binary patcher.
 */
public class SaveTextHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String body = readAll(exchange.getRequestBody());
        String relPath = formParam(body, "path");
        String content = formParam(body, "content");

        File file;
        try {
            file = PathUtil.resolve(relPath);
        } catch (IOException e) {
            respondJson(exchange, false, "Forbidden");
            return;
        }

        if (!file.exists() || file.isDirectory()) {
            respondJson(exchange, false, "That file no longer exists.");
            return;
        }

        String ext = GridRenderer.getExtension(file.getName()).toLowerCase();
        if (!ViewabilityUtil.isTextLike(file, ext)) {
            respondJson(exchange, false, "This file type can't be edited here.");
            return;
        }

        try {
            Files.write(file.toPath(), (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            respondJson(exchange, true, "Saved");
        } catch (IOException e) {
            respondJson(exchange, false, "Couldn't save: " + e.getMessage());
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
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toString("UTF-8");
    }

    private void respondJson(HttpExchange exchange, boolean success, String message) throws IOException {
        String json = "{\"success\": " + success + ", \"message\": \"" + MiniJson.escape(message == null ? "" : message) + "\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
