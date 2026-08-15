import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
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
 * Serves "/search?path=...&q=..." - recursively searches everything under
 * the given folder (case-insensitive filename match) and renders results as
 * a flat grid, each card labeled with which subfolder it came from.
 */
public class SearchHandler implements HttpHandler {

    private static final int MAX_RESULTS = 200;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String q = QueryUtil.getParam(query, "q");
        String relPath = QueryUtil.getParam(query, "path");
        q = q == null ? "" : URLDecoder.decode(q, "UTF-8").trim();
        relPath = relPath == null ? "" : URLDecoder.decode(relPath, "UTF-8");

        File startDir;
        try {
            startDir = PathUtil.resolve(relPath);
        } catch (IOException e) {
            sendText(exchange, 403, "Forbidden");
            return;
        }
        if (!startDir.isDirectory()) {
            sendText(exchange, 404, "Not found");
            return;
        }

        List<File> matches = new ArrayList<>();
        if (!q.isEmpty()) {
            String needle = q.toLowerCase();
            try (Stream<Path> walk = Files.walk(startDir.toPath())) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> !HiddenFileUtil.isHiddenPath(startDir.toPath(), p))
                    .filter(p -> p.getFileName().toString().toLowerCase().contains(needle))
                    .limit(MAX_RESULTS)
                    .forEach(p -> matches.add(p.toFile()));
            }
        }

        String html = buildResultsPage(matches, q, relPath);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildResultsPage(List<File> matches, String q, String relPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        String tabTitle = q.isEmpty() ? "Search" : "Search: " + q;
        sb.append("<title>").append(PathUtil.htmlEscape(tabTitle)).append("</title>");
        sb.append(Styles.CSS);
        sb.append("</head><body>");
        sb.append("<div class='page-content'>");

        sb.append("<div class='topbar'><a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.openTab){ parent.openTab('/dashboard','Dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append("<div class='breadcrumb'><a href='/browse?path=").append(PathUtil.urlEncode(relPath)).append("'>&larr; Back to folder</a></div>");
        sb.append("</div>");

        sb.append("<form class='upload-form' method='GET' action='/search'>")
          .append("<input type='hidden' name='path' value='").append(PathUtil.htmlEscape(relPath)).append("'>")
          .append("<input type='text' name='q' placeholder='Search filenames...' value='")
          .append(PathUtil.htmlEscape(q)).append("' style='flex:1;padding:8px;border:1px solid #c7cbd1;border-radius:6px;'>")
          .append("<button type='submit'>Search</button></form>");

        sb.append("<p style='padding:0 24px;color:#666;'>")
          .append(matches.size()).append(matches.size() == MAX_RESULTS ? "+ " : " ")
          .append("result").append(matches.size() == 1 ? "" : "s").append(" for \"")
          .append(PathUtil.htmlEscape(q)).append("\"</p>");

        sb.append("<div class='grid'>");
        for (File f : matches) {
            String childRel = PathUtil.relativeToRoot(f);
            String parentLabel = childRel.contains("/") ? childRel.substring(0, childRel.lastIndexOf('/')) : "/";
            sb.append(GridRenderer.fileCard(f, childRel, true, parentLabel));
        }
        if (matches.isEmpty() && !q.isEmpty()) {
            sb.append("<p class='empty'>No files matched.</p>");
        }
        sb.append("</div>");
        sb.append(PageScripts.MODAL_HTML);
        sb.append(PageScripts.SCRIPT);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private void sendText(HttpExchange exchange, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
