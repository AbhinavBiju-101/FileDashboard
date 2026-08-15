import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Serves "/trash" - the recycle bin page. Lists everything moved there by
 * FileOpsHandler's delete action, each with its original location and
 * when it was deleted, with Restore and "Delete forever" per item, plus an
 * "Empty trash" action for everything at once.
 */
public class TrashHandler implements HttpHandler {

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
        List<TrashManager.Entry> items = TrashManager.list();
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, yyyy h:mm a");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Recycle Bin</title>");
        sb.append(Styles.CSS);
        sb.append("</head><body>");
        sb.append("<div class='page-content'>");

        sb.append("<div class='topbar'>");
        sb.append("<a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append("<div class='breadcrumb'>Recycle Bin - ").append(items.size()).append(" item").append(items.size() == 1 ? "" : "s").append("</div>");
        if (!items.isEmpty()) {
            sb.append("<div class='toolbar'><a href=\"#\" data-action=\"empty-trash\" class='toolbar-action'>Empty trash</a></div>");
        }
        sb.append("</div>");

        if (items.isEmpty()) {
            sb.append("<p class='empty'>Recycle bin is empty.</p>");
        } else {
            sb.append("<div class='grid'>");
            for (TrashManager.Entry e : items) {
                String icon = e.isDirectory ? "&#128193;" : GridRenderer.iconFor(GridRenderer.getExtension(e.originalName).toLowerCase());
                String name = PathUtil.htmlEscape(e.originalName);
                String location = e.originalRelPath.contains("/")
                    ? e.originalRelPath.substring(0, e.originalRelPath.lastIndexOf('/'))
                    : "Home";
                sb.append("<div class=\"card\">");
                sb.append("<div class=\"icon\">").append(icon).append("</div>");
                sb.append("<div class=\"name\" title=\"").append(name).append("\">").append(name).append("</div>");
                sb.append("<div class=\"meta path\">From: ").append(PathUtil.htmlEscape(location)).append("</div>");
                sb.append("<div class=\"meta\">Deleted ").append(fmt.format(new Date(e.deletedTime))).append("</div>");
                sb.append("<div class=\"actions\">");
                sb.append("<a href=\"#\" data-action=\"restore\" data-id=\"").append(e.id).append("\" data-name=\"").append(name).append("\">Restore</a>");
                sb.append("<a href=\"#\" data-action=\"permanent-delete\" data-id=\"").append(e.id).append("\" data-name=\"").append(name).append("\">Delete forever</a>");
                sb.append("</div></div>");
            }
            sb.append("</div>");
        }

        sb.append(PageScripts.MODAL_HTML);
        sb.append(PageScripts.SCRIPT);
        sb.append("</div></body></html>");
        return sb.toString();
    }
}
