import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Date;
import java.util.Enumeration;
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
        // Loopback-only (127.0.0.1) unless network access has been
        // explicitly turned on in Settings - see Settings.java's doc
        // comment on networkAccessEnabled for the full reasoning. This is
        // the actual fix, not a mitigation: with the socket only ever
        // bound to the loopback interface, there is no network route to
        // it from another device at all, regardless of firewall rules, the
        // access token, or anyone guessing this machine's IP - the same
        // way nothing on your home Wi-Fi can reach a database that only
        // listens on localhost.
        boolean networked = Settings.isNetworkAccessEnabled();
        if (networked) {
            // Belt-and-suspenders: normally a token is created the instant
            // network access is turned on (see Settings.setNetworkAccessEnabled()),
            // but this guarantees the invariant holds even if that state
            // was reached some other way - a hand-edited settings.json, or
            // a future code path that sets the flag directly. There must
            // never be a moment where this server is reachable from the
            // network with no token to require.
            Settings.getOrCreateAccessToken();
        }
        InetSocketAddress bindAddress = networked
              ? new InetSocketAddress(Config.PORT)
              : new InetSocketAddress(InetAddress.getLoopbackAddress(), Config.PORT);
        try {
            server = HttpServer.create(bindAddress, 0);
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
        GoogleAuthHandler gAuth = new GoogleAuthHandler();
        addGDriveContext(server, "/gauth/start", gAuth);
        addGDriveContext(server, "/gauth/callback", gAuth);
        addGDriveContext(server, "/gdrive", new GDriveBrowseHandler());
        addGDriveContext(server, "/gdrive-file", new GDriveDownloadHandler());
        addGDriveContext(server, "/gdrive-export", new GDriveExportHandler());
        addGDriveContext(server, "/gdrive-search", new GDriveSearchHandler());
        addGDriveContext(server, "/gdrive-suggest", new GDriveSuggestHandler());
        addGDriveContext(server, "/gdrive-viewer", new GDriveViewerHandler());
        addGDriveContext(server, "/gdrive-accounts", new GDriveAccountsHandler());
        addGDriveContext(server, "/gdrive-ops", new GDriveOpsHandler());
        addGDriveContext(server, "/gdrive-onboarding", new GDriveOnboardingHandler());
        addGDriveContext(server, "/gdrive-access-request", new GDriveAccessRequestHandler());
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
        if (networked) {
            String lanIp = detectLanAddress();
            String token = Settings.getAccessToken();
            System.out.println("NETWORK ACCESS IS ON - reachable from other devices on this network" +
                (lanIp != null ? " at http://" + lanIp + ":" + Config.PORT : "") + ".");
            if (token != null) {
                System.out.println("Access token required from other devices: ?token=" + token +
                    (lanIp != null ? " (full URL: http://" + lanIp + ":" + Config.PORT + "/?token=" + token + ")" : ""));
            }
        } else {
            System.out.println("Only reachable from this computer (127.0.0.1) - turn on Network Access in Settings to change that.");
        }
        if (Config.ACCESS_TOKEN != null && !Config.ACCESS_TOKEN.isEmpty()) {
            System.out.println("Access token is set - first visit needs: http://localhost:" + Config.PORT + "/?token=" + Config.ACCESS_TOKEN);
        }
        System.out.println("Log file: " + new File(Config.DATA_DIR, "server.log").getAbsolutePath());
        System.out.println("Press Ctrl+C in the terminal (or stop it from BlueJ) to shut down.");
    }

    // Best-effort private-LAN IPv4 address to show in the startup banner and
    // Settings page - purely informational (so the person knows what to
    // type on their other device), never used for any access-control
    // decision. Falls back to null (banner just omits it) if nothing
    // suitable turns up, which can happen on some VPN/container setups.
    static String detectLanAddress() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) continue;
                    if (addr.getAddress().length != 4) continue; // IPv4 only - simpler to type/share
                    if (addr.isSiteLocalAddress()) return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // Best-effort only.
        }
        return null;
    }

    // Registers a handler and attaches the shared auth filter to it.
    private static void addContext(HttpServer server, String path, HttpHandler handler) {
        HttpContext ctx = server.createContext(path, handler);
        ctx.getFilters().add(new AuthFilter());
    }

    // Same as addContext(), plus GDriveGateFilter - every Drive-related
    // route goes through this one instead, so Settings.
    // isGdriveExperimentalEnabled() being off actually turns the feature
    // off server-side, not just in whatever UI happens to link to it.
    private static void addGDriveContext(HttpServer server, String path, HttpHandler handler) {
        HttpContext ctx = server.createContext(path, handler);
        ctx.getFilters().add(new AuthFilter());
        ctx.getFilters().add(new GDriveGateFilter());
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
