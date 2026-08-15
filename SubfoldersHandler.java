import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Serves "/subfolders?path=..." - a JSON array of the IMMEDIATE subfolder
 * names of the given folder (relative to Settings.rootDir()). Deliberately
 * non-recursive: a single File.listFiles() call is fast and responsive
 * regardless of how large the overall tree is, unlike a deep recursive
 * walk (which is what made "Move to..." effectively unusable once the root
 * covers a whole drive - Files.walk(root, 6) over C:\ could take a very
 * long time or return an overwhelming amount of data).
 *
 * Used by both the "Move to..." folder picker and the address bar's live
 * suggestions, so both browse hierarchically one level at a time instead of
 * searching the whole tree at once.
 */
public class SubfoldersHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String relPath = QueryUtil.getParam(query, "path");
        relPath = relPath == null ? "" : URLDecoder.decode(relPath, "UTF-8");

        File dir;
        try {
            dir = PathUtil.resolve(relPath);
        } catch (IOException e) {
            sendJson(exchange, 403, "[]");
            return;
        }

        if (!dir.isDirectory()) {
            sendJson(exchange, 200, "[]");
            return;
        }

        File[] entries = dir.listFiles((d, name) ->
            !HiddenFileUtil.isHiddenName(name) && new File(d, name).isDirectory());

        List<String> names = new ArrayList<>();
        if (entries != null) {
            for (File f : entries) names.add(f.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < names.size(); i++) {
            sb.append("{\"name\":\"").append(MiniJson.escape(names.get(i))).append("\"}");
            if (i < names.size() - 1) sb.append(",");
        }
        sb.append("]");

        sendJson(exchange, 200, sb.toString());
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
