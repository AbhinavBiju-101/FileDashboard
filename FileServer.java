import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.Executors;

/**
 * Entry point. In BlueJ: right-click this class -> void main(String[] args)
 * -> pass null (or an empty array) -> it starts the server on Config.PORT.
 * Also runnable as a plain jar: java -jar FileDashboard.jar (see build-jar.bat).
 *
 * Then open http://localhost:8080 in your browser.
 */
public class FileServer {

    public static void main(String[] args) {
        try {
            setupLogging();
        } catch (IOException e) {
            // Logging is a nice-to-have; a logging failure shouldn't stop the server.
        }

        System.out.println("---- File Dashboard starting: " + new Date() + " ----");

        if (!Settings.rootDir().exists()) {
            System.out.println("Root directory does not exist: " + Settings.rootDir());
            System.out.println("Edit Config.java and change ROOT_DIR to a folder that exists.");
            return;
        }

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(Config.PORT), 0);
        } catch (BindException e) {
            // Very likely cause when autostarted: it's already running from a
            // previous login/launch. Not an error worth alarming over.
            System.out.println("File Dashboard already appears to be running (port " + Config.PORT + " is in use).");
            System.out.println("Open http://localhost:" + Config.PORT + " - no need to start it again.");
            return;
        } catch (IOException e) {
            System.out.println("Could not start the server: " + e.getMessage());
            return;
        }

        // A real thread pool (not the single-threaded default) matters here:
        // the live-refresh endpoint ("/events") holds its connection open for
        // as long as a browser tab stays on the page, so other requests need
        // their own threads to avoid getting stuck behind it.
        server.setExecutor(Executors.newCachedThreadPool());

        addContext(server, "/", new AppShellHandler());
        addContext(server, "/dashboard", new HomeHandler());
        addContext(server, "/browse", new BrowseHandler());
        addContext(server, "/file", new FileViewHandler());
        addContext(server, "/thumbnail", new ThumbnailHandler());
        addContext(server, "/upload", new UploadHandler());
        addContext(server, "/search", new SearchHandler());
        addContext(server, "/suggest", new SuggestHandler());
        addContext(server, "/zip", new ZipDownloadHandler());
        addContext(server, "/zip-selection", new ZipSelectionHandler());
        addContext(server, "/events", new LiveUpdateHandler());
        addContext(server, "/fileops", new FileOpsHandler());
        addContext(server, "/trash", new TrashHandler());
        addContext(server, "/trashops", new TrashOpsHandler());
        addContext(server, "/trash-browse", new TrashBrowseHandler());
        addContext(server, "/trash-file", new TrashFileHandler());
        addContext(server, "/sessions", new SessionsHandler());
        addContext(server, "/viewer", new ViewerHandler());
        addContext(server, "/save-text", new SaveTextHandler());
        addContext(server, "/reveal", new RevealHandler());
        addContext(server, "/abspath", new AbsPathHandler());
        addContext(server, "/quickstart", new QuickstartHandler());
        addContext(server, "/settings", new SettingsHandler());
        addContext(server, "/subfolders", new SubfoldersHandler());
        addContext(server, "/dashboard-events", new DashboardEventsHandler());

        try {
            server.start();
        } catch (Exception e) {
            System.out.println("Could not start the server: " + e.getMessage());
            return;
        }

        System.out.println("File Dashboard running at http://localhost:" + Config.PORT);
        System.out.println("Serving folder: " + Settings.rootDir().getAbsolutePath());
        if (Config.ACCESS_TOKEN != null && !Config.ACCESS_TOKEN.isEmpty()) {
            System.out.println("Access token is set - first visit needs: http://localhost:" + Config.PORT + "/?token=" + Config.ACCESS_TOKEN);
        }
        System.out.println("Log file: " + new File(Config.DATA_DIR, "server.log").getAbsolutePath());
        System.out.println("Press Ctrl+C in the terminal (or stop it from BlueJ) to shut down.");
    }

    // Registers a handler and attaches the shared auth filter to it.
    private static void addContext(HttpServer server, String path, HttpHandler handler) {
        HttpContext ctx = server.createContext(path, handler);
        ctx.getFilters().add(new AuthFilter());
    }

    // Mirrors System.out/System.err to a log file in Config.DATA_DIR, in
    // addition to whatever console exists. When launched headlessly (via
    // javaw for autostart), there IS no console - this log file is the only
    // way to see what happened if something goes wrong.
    private static void setupLogging() throws IOException {
        if (!Config.DATA_DIR.exists()) Config.DATA_DIR.mkdirs();
        File logFile = new File(Config.DATA_DIR, "server.log");
        PrintStream fileOut = new PrintStream(new FileOutputStream(logFile, true), true, "UTF-8");

        PrintStream originalOut = System.out;
        PrintStream tee = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                originalOut.write(b);
                fileOut.write(b);
            }
            @Override
            public void flush() throws IOException {
                originalOut.flush();
                fileOut.flush();
            }
        }, true, "UTF-8");

        System.setOut(tee);
        System.setErr(tee);
    }
}
