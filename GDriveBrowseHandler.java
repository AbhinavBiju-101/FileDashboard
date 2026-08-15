import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Serves "/gdrive?path=..." - browsing Google Drive, reusing the exact same
 * .grid/.card/.icon/.name/.meta CSS classes as local Browse/Dashboard/Trash
 * so it looks like part of the same app rather than a bolted-on extra page.
 *
 * Deliberately read-only for this first pass (no upload/rename/move/delete
 * against Drive) and deliberately does NOT load PageScripts.java: that
 * script's selection/context-menu/rename/move/delete logic all POST to
 * /fileops against local filesystem paths, and reusing it here would either
 * silently do nothing or - worse - do something to a local path that
 * happens to collide with a Drive-shaped one. Cards here are plain links
 * instead: click a folder to navigate, click "Open"/"Download" on a file.
 *
 * Drive has no real paths - files just have parent folder ids - so the
 * "path" query param is a synthetic breadcrumb trail this handler invented:
 * "id|urlencodedName" segments joined by "/", e.g.
 * "1AbcId|Projects/1XyzId|Reports". Carrying the name alongside each id
 * means rendering the breadcrumb never needs extra API calls just to know
 * what to label each level.
 *
 * Unverified against real Google endpoints - see GDriveAuth.java's class
 * comment.
 */
public class GDriveBrowseHandler implements HttpHandler {

    private static class Crumb {
        final String id;
        final String name;
        Crumb(String id, String name) { this.id = id; this.name = name; }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String rawPath = QueryUtil.getParam(query, "path");
        List<Crumb> crumbs = parsePath(rawPath);
        String currentFolderId = crumbs.isEmpty() ? "root" : crumbs.get(crumbs.size() - 1).id;

        String html = buildPage(crumbs, currentFolderId);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private List<Crumb> parsePath(String rawPath) {
        List<Crumb> crumbs = new ArrayList<>();
        if (rawPath == null || rawPath.isEmpty()) return crumbs;
        try {
            rawPath = URLDecoder.decode(rawPath, "UTF-8");
        } catch (Exception ignored) {}
        for (String segment : rawPath.split("/")) {
            if (segment.isEmpty()) continue;
            int bar = segment.indexOf('|');
            if (bar == -1) continue;
            crumbs.add(new Crumb(segment.substring(0, bar), segment.substring(bar + 1)));
        }
        return crumbs;
    }

    private String pathFor(List<Crumb> crumbs, int uptoInclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= uptoInclusive; i++) {
            if (i > 0) sb.append('/');
            Crumb c = crumbs.get(i);
            sb.append(PathUtil.urlEncode(c.id)).append('|').append(PathUtil.urlEncode(c.name));
        }
        return sb.toString();
    }

    private String buildPage(List<Crumb> crumbs, String currentFolderId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Google Drive</title>");
        sb.append(Styles.CSS);
        sb.append(gdriveStyles());
        sb.append("</head><body><div class='page-content'>");

        sb.append("<div class='topbar'>");
        sb.append("<a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append("<div class='breadcrumb'>");
        sb.append("<a href='/gdrive?path='>My Drive</a>");
        for (int i = 0; i < crumbs.size(); i++) {
            sb.append(" / ");
            if (i == crumbs.size() - 1) {
                sb.append(PathUtil.htmlEscape(crumbs.get(i).name));
            } else {
                sb.append("<a href='/gdrive?path=").append(pathFor(crumbs, i)).append("'>")
                  .append(PathUtil.htmlEscape(crumbs.get(i).name)).append("</a>");
            }
        }
        sb.append("</div></div>");

        if (!GDriveAuth.isConnected()) {
            sb.append("<div class='gdrive-empty-state'>");
            sb.append("<p>Google Drive isn't connected yet.</p>");
            sb.append("<p><a href='/settings' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/settings'); return false; }\">Connect it in Settings</a> to browse your Drive files here.</p>");
            sb.append("</div>");
        } else {
            try {
                List<GDriveClient.DriveItem> items = GDriveClient.listChildren(currentFolderId);
                sb.append("<div class='grid'>");
                for (GDriveClient.DriveItem item : items) {
                    if (GDriveClient.isFolder(item.mimeType)) {
                        sb.append(folderCard(crumbs, item));
                    } else {
                        sb.append(fileCard(item));
                    }
                }
                if (items.isEmpty()) {
                    sb.append("</div><p class='empty'>This folder is empty.</p>");
                } else {
                    sb.append("</div>");
                    if (items.size() >= 200) {
                        sb.append("<p class='empty gdrive-more-note'>Showing the first 200 items - larger folders aren't paginated yet.</p>");
                    }
                }
            } catch (IOException e) {
                sb.append("<div class='gdrive-empty-state gdrive-error'>");
                sb.append("<p>Couldn't load this folder from Google Drive.</p>");
                sb.append("<p class='gdrive-error-detail'>").append(PathUtil.htmlEscape(e.getMessage())).append("</p>");
                sb.append("</div>");
            }
        }

        sb.append("</div></body></html>");
        return sb.toString();
    }

    private String folderCard(List<Crumb> crumbs, GDriveClient.DriveItem item) {
        List<Crumb> withThis = new ArrayList<>(crumbs);
        withThis.add(new Crumb(item.id, item.name));
        String name = PathUtil.htmlEscape(item.name);
        return "<a class=\"card folder\" href=\"/gdrive?path=" + pathFor(withThis, withThis.size() - 1) + "\">" +
               "<div class=\"icon\">&#128193;</div>" +
               "<div class=\"name\" title=\"" + name + "\">" + name + "</div>" +
               "</a>";
    }

    private String fileCard(GDriveClient.DriveItem item) {
        String name = PathUtil.htmlEscape(item.name);
        boolean nativeDoc = GDriveClient.isNativeGoogleDoc(item.mimeType);
        String icon = iconForMime(item.mimeType, item.name);
        String sizeLabel = nativeDoc ? nativeDocLabel(item.mimeType) : GridRenderer.humanSize(item.size);

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card file\">");
        sb.append("<div class=\"icon\">").append(icon).append("</div>");
        sb.append("<div class=\"name\" title=\"").append(name).append("\">").append(name).append("</div>");
        sb.append("<div class=\"meta\">").append(sizeLabel).append("</div>");
        sb.append("<div class=\"meta gdrive-actions\">");
        if (item.webViewLink != null) {
            sb.append("<a href=\"").append(PathUtil.htmlEscape(item.webViewLink)).append("\" target=\"_blank\" rel=\"noopener\">Open</a>");
        }
        if (!nativeDoc) {
            if (item.webViewLink != null) sb.append(" &middot; ");
            sb.append("<a href=\"/gdrive-file?id=").append(PathUtil.urlEncode(item.id))
              .append("&name=").append(PathUtil.urlEncode(item.name))
              .append("&mime=").append(PathUtil.urlEncode(item.mimeType == null ? "" : item.mimeType))
              .append("\">Download</a>");
        }
        sb.append("</div>");
        sb.append("</div>");
        return sb.toString();
    }

    private String nativeDocLabel(String mimeType) {
        if (mimeType == null) return "Google file";
        if (mimeType.endsWith(".document")) return "Google Doc";
        if (mimeType.endsWith(".spreadsheet")) return "Google Sheet";
        if (mimeType.endsWith(".presentation")) return "Google Slides";
        if (mimeType.endsWith(".form")) return "Google Form";
        if (mimeType.endsWith(".drawing")) return "Google Drawing";
        return "Google file";
    }

    private String iconForMime(String mimeType, String name) {
        if (mimeType != null) {
            if (mimeType.endsWith(".document")) return GridRenderer.iconFor("doc");
            if (mimeType.endsWith(".spreadsheet")) return GridRenderer.iconFor("xlsx");
            if (mimeType.endsWith(".presentation")) return GridRenderer.iconFor("pptx");
        }
        String ext = GridRenderer.getExtension(name).toLowerCase();
        return GridRenderer.iconFor(ext);
    }

    private String gdriveStyles() {
        return "<style>" +
            ".gdrive-empty-state{padding:48px 24px;text-align:center;color:#666;font-size:14px;line-height:1.7;}" +
            ".gdrive-empty-state a{color:#2563eb;text-decoration:none;}" +
            ".gdrive-empty-state a:hover{text-decoration:underline;}" +
            ".gdrive-error{color:#9c1f1f;}" +
            ".gdrive-error-detail{font-size:12px;color:#888;}" +
            ".gdrive-actions{margin-top:2px;}" +
            ".gdrive-actions a{color:#2563eb;text-decoration:none;}" +
            ".gdrive-actions a:hover{text-decoration:underline;}" +
            ".gdrive-more-note{padding:0 24px 24px;}" +
            "</style>";
    }
}
