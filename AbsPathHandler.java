import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Handles "GET /abspath" - resolves a "path" (or trashId+sub) to its real,
 * absolute filesystem path and returns it as JSON. Read-only counterpart
 * to RevealHandler; used by the "Copy path" context-menu action so what
 * lands on the clipboard is something paste-able into a terminal or
 * another app, not just the app's own ROOT_DIR-relative path.
 */
public class AbsPathHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String relPath = QueryUtil.getParam(query, "path");
        String trashId = QueryUtil.getParam(query, "trashId");
        String sub = QueryUtil.getParam(query, "sub");
        relPath = relPath == null ? null : URLDecoder.decode(relPath, "UTF-8");
        trashId = trashId == null ? null : URLDecoder.decode(trashId, "UTF-8");
        sub = sub == null ? null : URLDecoder.decode(sub, "UTF-8");

        File target;
        try {
            target = PathUtil.resolvePathOrTrash(relPath, trashId, sub);
        } catch (IOException e) {
            respondJson(exchange, false, e.getMessage(), null);
            return;
        }

        respondJson(exchange, true, null, target.getAbsolutePath());
    }

    private void respondJson(HttpExchange exchange, boolean success, String message, String path) throws IOException {
        StringBuilder json = new StringBuilder("{\"success\": ").append(success);
        if (message != null) json.append(", \"message\": \"").append(MiniJson.escape(message)).append("\"");
        if (path != null) json.append(", \"path\": \"").append(MiniJson.escape(path)).append("\"");
        json.append("}");
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
