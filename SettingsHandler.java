import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Serves "/settings" - GET renders the page, POST applies changes and
 * redirects back to GET with a status message. A plain form-POST page
 * rather than an AJAX one, since settings changes are infrequent and a full
 * reload on save is perfectly fine here.
 */
public class SettingsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handlePost(exchange);
            return;
        }
        handleGet(exchange);
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String msg = QueryUtil.getParam(query, "msg");
        String err = QueryUtil.getParam(query, "err");
        msg = msg == null ? null : URLDecoder.decode(msg, "UTF-8");
        err = err == null ? null : URLDecoder.decode(err, "UTF-8");

        String html = buildPage(msg, err);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String body = readAll(exchange.getRequestBody());
        String action = formParam(body, "action");
        String result = null; // error message, null = success

        if ("save-root-dir".equals(action)) {
            String path = formParam(body, "rootDir");
            result = Settings.setRootDirOverride(path);
        } else if ("save-dashboard".equals(action)) {
            String raw = formParam(body, "maxItems");
            try {
                Settings.setDashboardMaxItems(Integer.parseInt(raw.trim()));
            } catch (Exception e) {
                result = "Enter a whole number.";
            }
        } else if ("toggle-live-refresh".equals(action)) {
            Settings.setLiveRefreshEnabled(!Settings.isLiveRefreshEnabled());
        } else if ("enable-autostart".equals(action)) {
            result = AutostartManager.enable();
        } else if ("disable-autostart".equals(action)) {
            result = AutostartManager.disable();
        }

        String redirect = result == null
            ? "/settings?msg=" + PathUtil.urlEncode("Saved.")
            : "/settings?err=" + PathUtil.urlEncode(result);
        exchange.getResponseHeaders().set("Location", redirect);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private String buildPage(String msg, String err) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Settings</title>");
        sb.append(Styles.CSS);
        sb.append(settingsStyles());
        sb.append("</head><body><div class='page-content'>");

        sb.append("<div class='topbar'>");
        sb.append("<a class='brand-link' href='/dashboard' onclick=\"if(parent&&parent.navigateCurrentTab){ parent.navigateCurrentTab('/dashboard'); return false; }\"><h1>File Dashboard</h1></a>");
        sb.append("<div class='breadcrumb'>Settings</div>");
        sb.append("</div>");

        sb.append("<div class='settings-wrap'>");

        if (msg != null) sb.append("<div class='settings-flash ok'>").append(PathUtil.htmlEscape(msg)).append("</div>");
        if (err != null) sb.append("<div class='settings-flash err'>").append(PathUtil.htmlEscape(err)).append("</div>");

        // Home folder
        sb.append("<div class='settings-section'>");
        sb.append("<h2>Home folder</h2>");
        sb.append("<p class='settings-desc'>Which folder the sidebar's \"Home\" opens, and the outer boundary for browsing, search, and file operations.</p>");
        sb.append("<p class='settings-current'>Currently: <code>").append(PathUtil.htmlEscape(Settings.rootDir().getAbsolutePath())).append("</code>");
        if (Settings.getRootDirOverride() == null) sb.append(" <span class='settings-tag'>default</span>");
        sb.append("</p>");
        sb.append("<form method='POST' action='/settings' class='settings-form'>");
        sb.append("<input type='hidden' name='action' value='save-root-dir'>");
        sb.append("<input type='text' name='rootDir' placeholder='e.g. C:\\Users\\Abhinav or /home/abhinav' value='")
          .append(Settings.getRootDirOverride() != null ? PathUtil.htmlEscape(Settings.getRootDirOverride()) : "").append("'>");
        sb.append("<button type='submit'>Save</button>");
        sb.append("</form>");
        sb.append("<p class='settings-hint'>Leave blank and save to reset to the default. This is real filesystem access to whatever folder you point it at - rename/duplicate/delete/move all work there, so choose something you trust the whole tree of.</p>");
        sb.append("</div>");

        // Autostart
        sb.append("<div class='settings-section'>");
        sb.append("<h2>Start automatically at login</h2>");
        if (AutostartManager.isWindows()) {
            boolean enabled = AutostartManager.isEnabled();
            sb.append("<p class='settings-desc'>Currently: <strong>").append(enabled ? "Enabled" : "Disabled").append("</strong></p>");
            sb.append("<form method='POST' action='/settings' class='settings-form'>");
            sb.append("<input type='hidden' name='action' value='").append(enabled ? "disable-autostart" : "enable-autostart").append("'>");
            sb.append("<button type='submit'>").append(enabled ? "Disable autostart" : "Enable autostart").append("</button>");
            sb.append("</form>");
        } else {
            sb.append("<p class='settings-desc'>Autostart management here is only available on Windows. ")
              .append("On other platforms, use your OS's own startup tools (e.g. a systemd user service on Linux, a Login Item on macOS).</p>");
        }
        sb.append("</div>");

        // Dashboard
        sb.append("<div class='settings-section'>");
        sb.append("<h2>Dashboard</h2>");
        sb.append("<p class='settings-desc'>Maximum items shown in each Dashboard section (Frequently viewed, Recently downloaded, Frequent folders).</p>");
        sb.append("<form method='POST' action='/settings' class='settings-form'>");
        sb.append("<input type='hidden' name='action' value='save-dashboard'>");
        sb.append("<input type='number' name='maxItems' min='1' max='100' value='").append(Settings.getDashboardMaxItems()).append("' style='width:80px;'>");
        sb.append("<button type='submit'>Save</button>");
        sb.append("</form>");
        sb.append("</div>");

        // Live refresh
        sb.append("<div class='settings-section'>");
        sb.append("<h2>Live folder refresh</h2>");
        sb.append("<p class='settings-desc'>Automatically reload a Browse tab when files change in that folder (uses a background file-watcher per open tab). Currently: <strong>")
          .append(Settings.isLiveRefreshEnabled() ? "On" : "Off").append("</strong></p>");
        sb.append("<form method='POST' action='/settings' class='settings-form'>");
        sb.append("<input type='hidden' name='action' value='toggle-live-refresh'>");
        sb.append("<button type='submit'>Turn ").append(Settings.isLiveRefreshEnabled() ? "off" : "on").append("</button>");
        sb.append("</form>");
        sb.append("</div>");

        // Other settings ideas (not implemented, just documented)
        sb.append("<div class='settings-section settings-ideas'>");
        sb.append("<h2>Other settings ideas</h2>");
        sb.append("<p class='settings-desc'>Not built yet, but reasonable next additions:</p>");
        sb.append("<ul>");
        sb.append("<li>Dark mode toggle</li>");
        sb.append("<li>Default sort order/field for new Browse tabs</li>");
        sb.append("<li>Thumbnail size / quality</li>");
        sb.append("<li>View or rotate the access token from here instead of editing Config.java</li>");
        sb.append("<li>Auto-empty the Recycle Bin after N days</li>");
        sb.append("<li>Default upload destination</li>");
        sb.append("<li>Port number (would need a restart to take effect)</li>");
        sb.append("</ul>");
        sb.append("</div>");

        sb.append("</div></div></body></html>");
        return sb.toString();
    }

    private String settingsStyles() {
        return "<style>" +
            ".settings-wrap{max-width:640px;margin:0 auto;padding:32px 24px;}" +
            ".settings-flash{padding:10px 14px;border-radius:6px;margin-bottom:20px;font-size:14px;}" +
            ".settings-flash.ok{background:#eafbea;color:#186a18;border:1px solid #b7e4b7;}" +
            ".settings-flash.err{background:#fdeaea;color:#9c1f1f;border:1px solid #f0b8b8;}" +
            ".settings-section{border-bottom:1px solid #eee;padding:22px 0;}" +
            ".settings-section:last-child{border-bottom:none;}" +
            ".settings-section h2{font-size:16px;margin:0 0 6px;}" +
            ".settings-desc{color:#555;font-size:13px;margin:0 0 10px;line-height:1.5;}" +
            ".settings-current{font-size:13px;margin:0 0 10px;}" +
            ".settings-current code{background:#f4f5f7;padding:2px 6px;border-radius:4px;font-size:12px;}" +
            ".settings-tag{background:#eef0f2;color:#666;font-size:11px;padding:1px 8px;border-radius:10px;}" +
            ".settings-form{display:flex;gap:8px;}" +
            ".settings-form input[type=text]{flex:1;padding:8px 10px;border:1px solid #c7cbd1;border-radius:6px;font-size:13px;}" +
            ".settings-form input[type=number]{padding:8px 10px;border:1px solid #c7cbd1;border-radius:6px;font-size:13px;}" +
            ".settings-form button{padding:8px 16px;border:none;background:#2563eb;color:#fff;border-radius:6px;cursor:pointer;font-size:13px;}" +
            ".settings-form button:hover{background:#1d4ed8;}" +
            ".settings-hint{color:#888;font-size:12px;margin:10px 0 0;line-height:1.5;}" +
            ".settings-ideas ul{margin:0;padding-left:20px;color:#555;font-size:13px;line-height:1.9;}" +
            "</style>";
    }

    private String formParam(String body, String key) {
        if (body == null) return null;
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx == -1) continue;
            if (pair.substring(0, idx).equals(key)) {
                try {
                    return URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                } catch (Exception e) {
                    return pair.substring(idx + 1);
                }
            }
        }
        return null;
    }

    private String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toString("UTF-8");
    }
}
