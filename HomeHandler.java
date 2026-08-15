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
 * (frequently viewed files, recently downloaded files, frequently visited
 * folders), not a directory listing. It's loaded as a tab inside the app
 * shell ("/") rather than being the shell itself.
 *
 * All three sections are horizontally-scrollable rows rather than wrapping
 * grids, and capped at Settings.getDashboardMaxItems() (20 by default) -
 * keeps the page a fixed, predictable height no matter how much activity
 * has piled up.
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
        int max = Settings.getDashboardMaxItems();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Dashboard</title>");
        sb.append(Styles.CSS);
        sb.append("</head><body>");
        sb.append("<div class='page-content'>");

        sb.append("<div class='topbar'><h1>Dashboard</h1>");
        sb.append("<div class='breadcrumb'>Quick launch for your activity</div></div>");

        sb.append(fileSection("Frequently viewed", RecentActivity.getFrequentlyViewed(max),
            "Files you open often will show up here."));
        sb.append(fileSection("Recently downloaded", RecentActivity.getRecentDownloaded(),
            "Nothing yet - files you download will show up here."));
        sb.append(frequentFoldersSection(max));

        sb.append(PageScripts.MODAL_HTML);
        sb.append(PageScripts.SCRIPT);
        sb.append(dashboardRefreshScript());
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private String fileSection(String title, List<String> relPaths, String emptyMessage) {
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
            sb.append("<p class='empty'>").append(emptyMessage).append("</p>");
        } else {
            sb.append("<div class='dash-row'>").append(cards).append("</div>");
        }
        return sb.toString();
    }

    private String frequentFoldersSection(int max) {
        List<String> folders = RecentActivity.getFrequentFolders(max);
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
            sb.append("<div class='dash-row'>").append(cards).append("</div>");
        }
        return sb.toString();
    }

    // Polls /dashboard-events every few seconds for RecentActivity's version
    // counter and reloads the page when it changes. See DashboardEventsHandler
    // for why this is a poll rather than a long-held SSE connection - the
    // short version: SSE meant one more persistent connection competing with
    // every open Browse tab's own "/events" stream for the browser's 6-per-origin
    // connection cap, which is how "Recently downloaded" ended up silently
    // stuck. A poll opens and closes immediately each time, so it can't get
    // queued behind those longer-lived connections.
    private String dashboardRefreshScript() {
        return "<script>" +
               "(function(){" +
               "var lastVersion=null;" +
               "function poll(){" +
                 "fetch('/dashboard-events').then(function(r){ return r.json(); }).then(function(data){" +
                   "if(data.version===-1) return;" + // live refresh turned off in Settings
                   "if(lastVersion===null){ lastVersion=data.version; return; }" +
                   "if(data.version!==lastVersion){ location.reload(); }" +
                 "}).catch(function(){ /* offline or server restarting - try again next tick */ });" +
               "}" +
               "poll();" +
               "setInterval(poll, 3000);" +
               "})();" +
               "</script>";
    }

    private String parentLabel(String rel) {
        return rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) : "/";
    }
}
