import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves "GET /dashboard-events" - returns RecentActivity's current version
 * counter as JSON: {"version": N}. The Dashboard tab polls this every few
 * seconds (see HomeHandler's script) and reloads itself when the version
 * number goes up - e.g. because a file got downloaded from some other tab.
 *
 * This used to be a long-held Server-Sent-Events stream, the same shape as
 * LiveUpdateHandler's per-folder "/events" watch. That was the bug behind
 * "Recently downloaded isn't updating": every open Browse tab keeps its own
 * "/events" connection open for as long as that tab exists, and browsers
 * cap concurrent connections to a single origin at 6 over HTTP/1.1 (which is
 * what com.sun.net.httpserver.HttpServer speaks - no HTTP/2 multiplexing
 * here). This app's whole UI is built around having several tabs open at
 * once, so that cap is easy to hit - and once it is, the Dashboard tab's own
 * long-held connection can end up queued behind the Browse tabs' and never
 * actually open. The refresh notification wasn't being lost - it just had
 * nowhere to arrive, because the connection carrying it never connected.
 *
 * Polling with a plain request/response sidesteps that entirely: each poll
 * opens and closes immediately, so it can't get stuck queued behind
 * something long-lived.
 */
public class DashboardEventsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long version = Settings.isLiveRefreshEnabled() ? RecentActivity.getVersion() : -1;
        String json = "{\"version\": " + version + "}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
