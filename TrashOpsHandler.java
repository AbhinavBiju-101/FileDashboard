import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Handles "POST /trashops" with action=restore|permanent-delete|empty and
 * (for restore/permanent-delete) an id identifying the trash entry.
 */
public class TrashOpsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String body = readAll(exchange.getRequestBody());
        String action = formParam(body, "action");
        String id = formParam(body, "id");

        try {
            switch (action == null ? "" : action) {
                case "restore": {
                    String restoredPath = TrashManager.restore(id);
                    respondJson(exchange, true, "Restored to " + (restoredPath.isEmpty() ? "Home" : restoredPath));
                    return;
                }
                case "permanent-delete":
                    TrashManager.permanentlyDelete(id);
                    respondJson(exchange, true, "Deleted permanently");
                    return;
                case "empty":
                    TrashManager.emptyTrash();
                    respondJson(exchange, true, "Recycle bin emptied");
                    return;
                default:
                    respondJson(exchange, false, "Unknown action.");
            }
        } catch (IOException e) {
            respondJson(exchange, false, e.getMessage());
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
