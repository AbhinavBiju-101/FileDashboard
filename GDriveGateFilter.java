import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Wraps every /gdrive* and /gauth/* route (see FileServer.addGDriveContext()).
 * Google Drive integration is an experimental, opt-in feature - Settings.
 * isGdriveExperimentalEnabled() defaults to false - and this filter is what
 * makes that actually true server-side, not just a hidden sidebar link: with
 * the setting off, none of these routes do anything at all, regardless of
 * how they're reached (a stale bookmark, a saved session pointing at an
 * account, a stray API call).
 */
public class GDriveGateFilter extends Filter {

    @Override
    public String description() {
        return "Blocks Google Drive routes unless the experimental setting is enabled";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        if (Settings.isGdriveExperimentalEnabled()) {
            chain.doFilter(exchange);
            return;
        }

        String msg = "<html><body style='font-family:sans-serif;padding:40px;max-width:520px;'>" +
                "<h2>Google Drive integration is off</h2>" +
                "<p>It's an experimental, opt-in feature - File Dashboard's main job is browsing your own local files. " +
                "Turn it on from <a href='/settings'>Settings</a> if you want it.</p>" +
                "</body></html>";
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(404, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
