import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves "/quickstart" - a short orientation page, opened whenever the "+"
 * (new tab) button is clicked. Not meant to be the everyday landing page
 * (that's the Dashboard) - this is for "what does this app actually do"
 * and quick links to get moving.
 */
public class QuickstartHandler implements HttpHandler {

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
        sb.append("<title>Quick Start</title>");
        sb.append(Styles.CSS);
        sb.append(quickstartStyles());
        sb.append("</head><body><div class='page-content'>");

        sb.append("<div class='qs-wrap'>");
        sb.append("<h1>File Dashboard</h1>");
        sb.append("<p class='qs-sub'>A local file explorer that lives in your browser.</p>");

        sb.append(section("&#128193;", "Browsing",
            "Click a folder to open it. Click a file to select it, double-click to preview it. " +
            "Right-click anything for actions: rename, duplicate, move, delete, or open a full reading tab."));

        sb.append(section("&#128269;", "Finding things",
            "Type in the search box for live suggestions ranked by what you actually use, or hit Enter " +
            "for a full recursive search. Press <kbd>/</kbd> anywhere to jump straight to a folder by path."));

        sb.append(section("&#128214;", "Reading",
            "Right-click a PDF, text file, or code file and choose \"Open Viewer\" for a dedicated full-tab " +
            "reading view with Previous/Next between files in the same folder. Left/Right arrow keys work " +
            "there too, and in the quick-preview modal for flipping through a folder of images."));

        sb.append(section("&#128465;", "Safety net",
            "Deleting something moves it to the Recycle Bin instead of removing it right away - restore it " +
            "any time until you empty the bin."));

        sb.append(section("&#9881;", "Making it yours",
            "Open Settings (sidebar) to change which folder \"Home\" points to, control autostart, and a few " +
            "other preferences."));

        sb.append("<div class='qs-actions'>");
        sb.append("<a href='#' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/browse?path='); } return false;\">Open Home</a>");
        sb.append("<a href='#' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/settings'); } return false;\">Open Settings</a>");
        sb.append("</div>");

        sb.append("</div></div></body></html>");
        return sb.toString();
    }

    private String section(String icon, String title, String body) {
        return "<div class='qs-section'>" +
               "<div class='qs-icon'>" + icon + "</div>" +
               "<div><h3>" + title + "</h3><p>" + body + "</p></div>" +
               "</div>";
    }

    private String quickstartStyles() {
        return "<style>" +
            ".qs-wrap{max-width:640px;margin:0 auto;padding:48px 24px;font-family:-apple-system,Segoe UI,Roboto,sans-serif;}" +
            ".qs-wrap h1{margin-bottom:4px;}" +
            ".qs-sub{color:#666;margin-top:0;margin-bottom:36px;}" +
            ".qs-section{display:flex;gap:16px;margin-bottom:26px;}" +
            ".qs-icon{font-size:26px;flex-shrink:0;width:36px;text-align:center;}" +
            ".qs-section h3{margin:0 0 4px;font-size:15px;}" +
            ".qs-section p{margin:0;color:#444;line-height:1.6;font-size:14px;}" +
            ".qs-section kbd{background:#eef0f2;border:1px solid #d5d8dc;border-radius:4px;padding:1px 6px;font-family:Menlo,Consolas,monospace;font-size:12px;}" +
            ".qs-actions{margin-top:36px;display:flex;gap:14px;}" +
            ".qs-actions a{background:#2563eb;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-size:14px;}" +
            ".qs-actions a:hover{background:#1d4ed8;}" +
            "</style>";
    }
}
