import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Serves "/browse?path=...": a grid view of whatever folder is requested,
 * relative to Settings.rootDir() (your home directory by default, or
 * search, whole-folder zip download, and a live auto-refresh connection.
 */
public class BrowseHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String relPath = QueryUtil.getParam(query, "path");
        String sort = QueryUtil.getParam(query, "sort");
        String order = QueryUtil.getParam(query, "order");
        relPath = relPath == null ? "" : URLDecoder.decode(relPath, "UTF-8");
        sort = sort == null ? "name" : sort;
        order = order == null ? "asc" : order;

        File dir;
        try {
            dir = PathUtil.resolve(relPath);
        } catch (IOException e) {
            sendText(exchange, 403, "Forbidden: " + e.getMessage());
            return;
        }

        if (!dir.exists() || !dir.isDirectory()) {
            sendText(exchange, 404, "Not found or not a directory: " + relPath);
            return;
        }

        RecentActivity.recordFolderVisit(relPath);

        String html;
        try {
            html = buildPage(dir, relPath, sort, order);
        } catch (Exception e) {
            sendText(exchange, 500, "Something went wrong listing this folder: " + e.getMessage() +
                "\n\nThis can happen with unusual filesystem entries (broken symlinks, junction points, " +
                "permission-restricted items). Try a specific subfolder instead.");
            return;
        }
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildPage(File dir, String relPath, String sort, String order) {
        File[] entries = dir.listFiles((d, name) -> !HiddenFileUtil.isHiddenName(name));
        if (entries == null) entries = new File[0];
        Arrays.sort(entries, buildComparator(sort, order));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        String tabTitle = relPath.isEmpty() ? "Home" : dir.getName();
        sb.append("<title>").append(PathUtil.htmlEscape(tabTitle)).append("</title>");
        sb.append(Styles.CSS);
        sb.append("</head><body>");
        sb.append("<div class='page-content'>");

        sb.append("<div class='topbar'>");
        sb.append("<a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append(buildBreadcrumb(relPath));
        sb.append(buildToolbar(relPath, sort, order));
        sb.append("</div>");

        sb.append("<form class='upload-form' method='POST' action='/upload?path=")
          .append(PathUtil.urlEncode(relPath)).append("' enctype='multipart/form-data'>")
          .append("<input type='file' name='file' required>")
          .append("<button type='submit'>Upload here</button>")
          .append("</form>");

        sb.append("<div class='grid' data-current-path=\"").append(PathUtil.htmlEscape(relPath)).append("\">");

        int skipped = 0;
        for (File f : entries) {
            try {
                String childRel = relPath.isEmpty() ? f.getName() : relPath + "/" + f.getName();
                if (f.isDirectory()) {
                    sb.append(GridRenderer.folderCard(childRel, f.getName()));
                } else {
                    sb.append(GridRenderer.fileCard(f, childRel, false, null));
                }
            } catch (Exception e) {
                // A single unreadable file, broken symlink, or Windows junction
                // point shouldn't be able to blank the whole page - skip it and
                // keep rendering the rest of the folder.
                skipped++;
            }
        }
        if (skipped > 0) {
            sb.append("<p class='empty'>(").append(skipped).append(" item").append(skipped == 1 ? "" : "s")
              .append(" couldn't be displayed and ").append(skipped == 1 ? "was" : "were").append(" skipped.)</p>");
        }

        if (entries.length == 0) {
            sb.append("<p class='empty'>This folder is empty.</p>");
        }

        sb.append("</div>");
        sb.append(liveRefreshScript(relPath));
        sb.append(PageScripts.MODAL_HTML);
        sb.append(PageScripts.SCRIPT);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private Comparator<File> buildComparator(String sort, String order) {
        Comparator<File> byField;
        switch (sort) {
            case "size": byField = Comparator.comparingLong(File::length); break;
            case "date": byField = Comparator.comparingLong(File::lastModified); break;
            default:     byField = Comparator.comparing(f -> f.getName().toLowerCase());
        }
        if ("desc".equals(order)) byField = byField.reversed();
        // Folders always come first regardless of sort field/direction.
        return Comparator.<File, Boolean>comparing(f -> !f.isDirectory()).thenComparing(byField);
    }

    private String buildToolbar(String relPath, String sort, String order) {
        String encodedPath = PathUtil.urlEncode(relPath);
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='toolbar'>");

        sb.append("<span class='toolbar-label'>Sort:</span>");
        sb.append(sortLink(encodedPath, "name", sort, order, "Name"));
        sb.append(sortLink(encodedPath, "size", sort, order, "Size"));
        sb.append(sortLink(encodedPath, "date", sort, order, "Date"));


        sb.append("<div class='search-suggest-wrap'>");
        sb.append("<form class='search-inline' method='GET' action='/search'>")
          .append("<input type='hidden' name='path' value='").append(PathUtil.htmlEscape(relPath)).append("'>")
          .append("<input type='text' name='q' class='js-search-input' data-context-path=\"").append(PathUtil.htmlEscape(relPath))
          .append("\" placeholder='Search this folder...' autocomplete='off'>")
          .append("<button type='submit'>Search</button></form>");
        sb.append("<div class='search-suggestions' id='searchSuggestions'></div>");
        sb.append("</div>");

        sb.append("</div>");
        sb.append(buildFilterChips());
        return sb.toString();
    }

    private String buildFilterChips() {
        String[][] chips = {
            {"all", "All"}, {"image", "Images"}, {"pdf", "PDFs"}, {"document", "Docs"},
            {"video", "Video"}, {"audio", "Audio"}, {"archive", "Archives"}, {"other", "Other"}
        };
        StringBuilder sb = new StringBuilder("<div class='filter-chips'>");
        for (int i = 0; i < chips.length; i++) {
            String activeClass = i == 0 ? " active" : "";
            sb.append("<span class='chip").append(activeClass).append("' data-filter='").append(chips[i][0])
              .append("'>").append(chips[i][1]).append("</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private String sortLink(String encodedPath, String field, String currentSort, String currentOrder, String label) {
        String nextOrder = (field.equals(currentSort) && "asc".equals(currentOrder)) ? "desc" : "asc";
        boolean active = field.equals(currentSort);
        String arrow = active ? (currentOrder.equals("asc") ? " &uarr;" : " &darr;") : "";
        return "<a class='toolbar-action" + (active ? " active" : "") + "' href='/browse?path=" + encodedPath +
               "&sort=" + field + "&order=" + nextOrder + "'>" + label + arrow + "</a>";
    }

    private String buildBreadcrumb(String relPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='breadcrumb'><a href='/browse?path='>Home</a>");
        if (!relPath.isEmpty()) {
            String[] parts = relPath.split("/");
            StringBuilder acc = new StringBuilder();
            for (String part : parts) {
                if (part.isEmpty()) continue;
                if (acc.length() > 0) acc.append("/");
                acc.append(part);
                sb.append(" / <a href='/browse?path=").append(PathUtil.urlEncode(acc.toString())).append("'>")
                  .append(PathUtil.htmlEscape(part)).append("</a>");
            }
        }
        sb.append("</div>");
        return sb.toString();
    }

    // Opens an EventSource connection to /events for this folder; on any
    // change notification from the server, silently reload the page.
    private String liveRefreshScript(String relPath) {
        String jsPath = relPath.replace("\\", "\\\\").replace("'", "\\'");
        return "<script>" +
               "try{" +
               "const es = new EventSource('/events?path=' + encodeURIComponent('" + jsPath + "'));" +
               "es.onmessage = function(e){ if(e.data === 'refresh'){ location.reload(); } };" +
               "}catch(err){ console.warn('Live refresh unavailable:', err); }" +
               "</script>";
    }

    private void sendText(HttpExchange exchange, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
