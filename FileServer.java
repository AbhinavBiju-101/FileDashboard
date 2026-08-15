import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Entry point. In BlueJ: right-click this class -> void main(String[] args)
 * -> pass null (or an empty array) -> it starts the server on Config.PORT.
 *
 * Then open http://localhost:8080 in your browser.
 */
public class FileServer {

    public static void main(String[] args) throws Exception {
        if (!Config.ROOT_DIR.exists()) {
            System.out.println("Root directory does not exist: " + Config.ROOT_DIR);
            System.out.println("Edit Config.java and change ROOT_DIR to a folder that exists.");
            return;
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(Config.PORT), 0);

        // A real thread pool (not the single-threaded default) matters here:
        // the live-refresh endpoint ("/events") holds its connection open for
        // as long as a browser tab stays on the page, so other requests need
        // their own threads to avoid getting stuck behind it.
        server.setExecutor(Executors.newCachedThreadPool());

        addContext(server, "/", new DashboardHandler());
        addContext(server, "/file", new FileViewHandler());
        addContext(server, "/thumbnail", new ThumbnailHandler());
        addContext(server, "/upload", new UploadHandler());
        addContext(server, "/search", new SearchHandler());
        addContext(server, "/zip", new ZipDownloadHandler());
        addContext(server, "/events", new LiveUpdateHandler());

        server.start();

        System.out.println("File Dashboard running at http://localhost:" + Config.PORT);
        System.out.println("Serving folder: " + Config.ROOT_DIR.getAbsolutePath());
        if (Config.ACCESS_TOKEN != null && !Config.ACCESS_TOKEN.isEmpty()) {
            System.out.println("Access token is set - first visit needs: http://localhost:" + Config.PORT + "/?token=" + Config.ACCESS_TOKEN);
        }
        System.out.println("Press Ctrl+C in the terminal (or stop it from BlueJ) to shut down.");
    }

    // Registers a handler and attaches the shared auth filter to it.
    private static void addContext(HttpServer server, String path, HttpHandler handler) {
        HttpContext ctx = server.createContext(path, handler);
        ctx.getFilters().add(new AuthFilter());
    }
}
