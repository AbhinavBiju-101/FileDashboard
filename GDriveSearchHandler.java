import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serves "/gdrive-search?q=..." - a name search across the whole connected
 * Drive (see GDriveClient.search()'s comment on why this isn't scoped to a
 * starting folder the way local /search is). Reuses GDriveBrowseHandler's
 * card renderers so a hit looks exactly like it would sitting in a normal
 * folder listing; clicking into a folder hit treats it as a fresh
 * top-level breadcrumb of its own, same simplification GDriveSuggestHandler
 * makes for the address bar.
 */
public class GDriveSearchHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String q = QueryUtil.getParam(query, "q");
        q = q == null ? "" : URLDecoder.decode(q, "UTF-8");

        String html = buildPage(q);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildPage(String q) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Search Google Drive</title>");
        sb.append(Styles.CSS);
        sb.append("</head><body><div class='page-content'>");

        sb.append("<div class='topbar'>");
        sb.append("<a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append("<div class='breadcrumb'><a href='/gdrive?path='>My Drive</a> / Search results for \u201c")
          .append(PathUtil.htmlEscape(q)).append("\u201d</div>");
        sb.append("<div class='search-suggest-wrap'>");
        sb.append("<form class='search-inline' method='GET' action='/gdrive-search'>")
          .append("<input type='text' name='q' class='js-gdrive-search-input' placeholder='Search Google Drive...' autocomplete='off'>")
          .append("<button type='submit'>Search</button></form>");
        sb.append("<div class='search-suggestions' id='gdriveSearchSuggestions'></div>");
        sb.append("</div>");
        sb.append("</div>");

        if (!GDriveAuth.isConnected()) {
            sb.append("<p class='empty'>Google Drive isn't connected. <a href='/settings' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/settings'); return false; }\">Connect it in Settings</a>.</p>");
        } else if (q.trim().isEmpty()) {
            sb.append("<p class='empty'>Type something to search for in Google Drive.</p>");
        } else {
            try {
                List<GDriveClient.DriveItem> items = GDriveClient.search(q);
                if (items.isEmpty()) {
                    sb.append("<p class='empty'>No matches for \u201c").append(PathUtil.htmlEscape(q)).append("\u201d in Drive.</p>");
                } else {
                    sb.append("<div class='grid'>");
                    for (GDriveClient.DriveItem item : items) {
                        if (GDriveClient.isFolder(item.mimeType)) {
                            sb.append(GDriveBrowseHandler.folderCardForPath(
                                PathUtil.urlEncode(item.id) + "|" + PathUtil.urlEncode(item.name),
                                item.name, item.id, item.webViewLink));
                        } else {
                            sb.append(GDriveBrowseHandler.fileCard(item));
                        }
                    }
                    sb.append("</div>");
                }
            } catch (IOException e) {
                sb.append("<p class='empty'>Couldn't search Google Drive: ").append(PathUtil.htmlEscape(e.getMessage())).append("</p>");
            }
        }

        sb.append(GDriveBrowseHandler.SEARCH_SUGGEST_SCRIPT);
        sb.append(GDriveBrowseHandler.CONTEXT_MENU_SCRIPT);
        sb.append("</div></body></html>");
        return sb.toString();
    }
}
