import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Serves "/events?path=..." as a Server-Sent Events stream. As long as the
 * browser tab is open, this watches the given folder with a WatchService and
 * pushes a "refresh" message whenever a file is added, removed, or changed -
 * so the dashboard grid can auto-refresh without the person hitting reload.
 *
 * One long-lived connection = one WatchService = one handler thread, which is
 * why FileServer uses a real thread pool executor instead of the default.
 */
public class LiveUpdateHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String relPath = QueryUtil.getParam(query, "path");
        relPath = relPath == null ? "" : URLDecoder.decode(relPath, "UTF-8");

        File dir;
        try {
            dir = PathUtil.resolve(relPath);
        } catch (IOException e) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        if (!dir.isDirectory()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        if (!Settings.isLiveRefreshEnabled()) {
            // Live refresh is turned off - close the connection immediately
            // rather than standing up a WatchService for nothing. The client's
            // EventSource just won't receive any messages, which is exactly
            // the "off" behavior (no auto-refresh).
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0); // 0 = chunked, length not known up front

        WatchService watcher = FileSystems.getDefault().newWatchService();
        WatchKey key = dir.toPath().register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        try (OutputStream os = exchange.getResponseBody()) {
            while (true) {
                WatchKey polled;
                try {
                    polled = watcher.poll(25, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    break;
                }

                if (polled == null) {
                    // No changes - send a comment as a heartbeat so proxies/browsers
                    // don't consider the connection dead.
                    os.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    continue;
                }

                polled.pollEvents(); // we don't care what changed, just that something did
                boolean stillValid = polled.reset();

                os.write("data: refresh\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();

                if (!stillValid) break;
            }
        } catch (IOException clientClosedConnection) {
            // Normal when the browser tab is closed or navigates away.
        } finally {
            key.cancel();
            watcher.close();
        }
    }
}
