import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serves "/suggest?q=...&path=..." - returns a small JSON array of
 * {name, path, type} suggestions for the search box's live dropdown,
 * ranked by SearchSuggester (activity first, then a shallow filename walk).
 */
public class SuggestHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String q = QueryUtil.getParam(query, "q");
        String relPath = QueryUtil.getParam(query, "path");
        q = q == null ? "" : URLDecoder.decode(q, "UTF-8");
        relPath = relPath == null ? "" : URLDecoder.decode(relPath, "UTF-8");

        File searchRoot;
        try {
            searchRoot = PathUtil.resolve(relPath);
        } catch (IOException e) {
            searchRoot = Settings.rootDir();
        }

        List<SearchSuggester.Suggestion> suggestions = SearchSuggester.suggest(q, searchRoot);

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < suggestions.size(); i++) {
            SearchSuggester.Suggestion s = suggestions.get(i);
            sb.append("{\"name\":\"").append(MiniJson.escape(s.name)).append("\",")
              .append("\"path\":\"").append(MiniJson.escape(s.path)).append("\",")
              .append("\"type\":\"").append(s.isFolder ? "folder" : "file").append("\"}");
            if (i < suggestions.size() - 1) sb.append(",");
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
