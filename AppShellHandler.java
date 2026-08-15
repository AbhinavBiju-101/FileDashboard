import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves "/" - the main application window. This is the only page that
 * renders the sidebar; everything else (Dashboard, Browse, Search) loads
 * inside a tab's iframe, opened/managed by ShellScript.java.
 */
public class AppShellHandler implements HttpHandler {

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
        sb.append("<title>File Dashboard</title>");
        sb.append(Styles.CSS);
        sb.append("</head><body>");
        sb.append(SidebarRenderer.render());
        sb.append("<div class='main-content shell-main'>");
        sb.append("<div id='tabbar' class='tabbar'>");
        sb.append("<button id='newTabBtn' class='tab-new' onclick=\"openTab('/quickstart','Quick Start')\" title='New tab'>+</button>");
        sb.append("</div>");
        sb.append("<div id='tabContextMenu' class='context-menu'></div>");
        sb.append("<div id='tabcontent' class='tabcontent'></div>");
        sb.append("</div>");
        sb.append("<div id='addressBarOverlay' class='address-bar-overlay' onclick=\"if(event.target===this) closeAddressBar();\">");
        sb.append("<div class='address-bar-box'>");
        sb.append("<div class='address-bar-row'>");
        sb.append("<span class='address-bar-prefix'>/</span>");
        sb.append("<input type='text' id='addressBarInput' placeholder='Documents/Books or C:\\Users\\You\\...' autocomplete='off'>");
        sb.append("<span class='address-bar-hint'>Enter to go &middot; Esc to cancel</span>");
        sb.append("</div>");
        sb.append("<div id='addressBarSuggestions' class='address-suggestions'></div>");
        sb.append("</div></div>");
        String rootAbsPath = Settings.rootDir().getAbsolutePath().replace("\\", "\\\\").replace("'", "\\'");
        sb.append("<script>var SHELL_ROOT_ABS='").append(rootAbsPath).append("';</script>");
        sb.append(ShellScript.SCRIPT);
        sb.append("</body></html>");
        return sb.toString();
    }
}
