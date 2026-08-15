import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serves "/dashboard" - the landing dashboard. This is deliberately separate
 * from the folder browser at "/browse": it's a quick-launch surface
 * (recently opened files, recently downloaded files, frequently visited
 * folders), not a directory listing. It's loaded as a tab inside the app
 * shell ("/") rather than being the shell itself.
 */
public class HomeHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String html = buildPage();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildPage() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Dashboard</title>");
        sb.append(Styles.CSS);
        sb.append("</head><body>");
        sb.append("<div class='page-content'>");

        sb.append("<div class='topbar'><h1>Dashboard</h1>");
        sb.append("<div class='breadcrumb'>Quick launch for your recent activity</div></div>");

        sb.append(fileSection("Recently viewed", RecentActivity.getRecentViewed()));
        sb.append(fileSection("Recently downloaded", RecentActivity.getRecentDownloaded()));
        sb.append(frequentFoldersSection());

        sb.append(PageScripts.MODAL_HTML);
        sb.append(PageScripts.SCRIPT);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private String fileSection(String title, List<String> relPaths) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class='section-title'>").append(title).append("</h2>");

        StringBuilder cards = new StringBuilder();
        int shown = 0;
        for (String rel : relPaths) {
            try {
                File f = PathUtil.resolve(rel);
                if (f.isFile()) {
                    cards.append(GridRenderer.fileCard(f, rel, true, parentLabel(rel)));
                    shown++;
                }
            } catch (IOException ignored) {
                // file may have moved/been deleted since it was recorded - skip it
            }
        }

        if (shown == 0) {
            sb.append("<p class='empty'>Nothing yet - files you open or download will show up here.</p>");
        } else {
            sb.append("<div class='grid'>").append(cards).append("</div>");
        }
        return sb.toString();
    }

    private String frequentFoldersSection() {
        List<String> folders = RecentActivity.getFrequentFolders(8);
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class='section-title'>Frequent folders</h2>");

        StringBuilder cards = new StringBuilder();
        int shown = 0;
        for (String rel : folders) {
            try {
                File f = PathUtil.resolve(rel);
                if (f.isDirectory()) {
                    String label = rel.isEmpty() ? "Home" : f.getName();
                    cards.append(GridRenderer.folderCard(rel, label));
                    shown++;
                }
            } catch (IOException ignored) {
                // folder may have moved/been deleted since it was recorded - skip it
            }
        }

        if (shown == 0) {
            sb.append("<p class='empty'>Folders you browse often will show up here.</p>");
        } else {
            sb.append("<div class='grid'>").append(cards).append("</div>");
        }
        return sb.toString();
    }

    private String parentLabel(String rel) {
        return rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) : "/";
    }
}
