import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Serves "/folders?q=..." - a small JSON list of folders (path + name)
 * under ROOT_DIR matching the query, for the "Move to..." picker. Always
 * includes "Home" (the root itself) so there's a way to move something all
 * the way back to the top.
 */
public class FoldersHandler implements HttpHandler {

    private static final int MAX_RESULTS = 40;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String q = QueryUtil.getParam(query, "q");
        final String needle = q == null ? "" : URLDecoder.decode(q, "UTF-8").trim().toLowerCase();

        List<String[]> results = new ArrayList<>(); // {relPath, displayLabel}

        if ("home".contains(needle) || needle.isEmpty()) {
            results.add(new String[]{"", "Home"});
        }

        try (Stream<Path> walk = Files.walk(Settings.rootDir().toPath(), 6)) {
            Path root = Settings.rootDir().toPath();
            walk.filter(Files::isDirectory)
                .filter(p -> !p.equals(root))
                .filter(p -> !HiddenFileUtil.isHiddenPath(root, p))
                .filter(p -> needle.isEmpty() || p.getFileName().toString().toLowerCase().contains(needle))
                .limit(MAX_RESULTS)
                .forEach(p -> {
                    String relPath = root.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                    results.add(new String[]{relPath, relPath});
                });
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            sb.append("{\"path\":\"").append(MiniJson.escape(results.get(i)[0])).append("\",")
              .append("\"label\":\"").append(MiniJson.escape(results.get(i)[1])).append("\"}");
            if (i < results.size() - 1) sb.append(",");
        }
        sb.append("]");

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
