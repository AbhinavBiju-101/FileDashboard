import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves "/dashboard-events" - a Server-Sent Events stream the Dashboard tab
 * subscribes to, so it refreshes itself when activity changes somewhere
 * else (e.g. downloading a file from a Browse tab should update "Recently
 * downloaded" without needing to manually reload the Dashboard tab).
 *
 * Same idea as LiveUpdateHandler's WatchService-based folder refresh, but
 * polling RecentActivity's version counter instead - there's no single
 * folder to watch here, just "did the tracked activity change at all".
 */
public class DashboardEventsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!Settings.isLiveRefreshEnabled()) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0); // chunked

        long lastSeenVersion = RecentActivity.getVersion();

        try (OutputStream os = exchange.getResponseBody()) {
            while (true) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    break;
                }

                long current = RecentActivity.getVersion();
                if (current != lastSeenVersion) {
                    lastSeenVersion = current;
                    os.write("data: refresh\n\n".getBytes(StandardCharsets.UTF_8));
                } else {
                    os.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                }
                os.flush();
            }
        } catch (IOException clientClosedConnection) {
            // Normal when the browser tab is closed or navigates away.
        }
    }
}
