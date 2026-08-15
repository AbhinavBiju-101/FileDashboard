import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;

/**
 * Handles both halves of the "Connect Google Drive" OAuth2 round trip -
 * registered under two paths (see FileServer.java) pointing at the same
 * instance, dispatching on which one was hit:
 *
 *   /gauth/start    - Settings' "Connect" button posts here; redirects the
 *                      browser on to Google's consent screen.
 *   /gauth/callback - Google redirects back here with an authorization
 *                      code; this exchanges it for tokens and sends the
 *                      browser back to Settings with a result message.
 *
 * All the actual OAuth mechanics (PKCE, token exchange, refresh) live in
 * GDriveAuth.java - this handler is just the HTTP glue around it.
 */
public class GoogleAuthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/callback")) {
            handleCallback(exchange);
        } else {
            handleStart(exchange);
        }
    }

    private void handleStart(HttpExchange exchange) throws IOException {
        String redirectTo;
        try {
            redirectTo = GDriveAuth.beginAuth();
        } catch (IOException e) {
            redirect(exchange, "/settings?err=" + PathUtil.urlEncode(e.getMessage()));
            return;
        }
        redirect(exchange, redirectTo);
    }

    private void handleCallback(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String error = QueryUtil.getParam(query, "error");
        if (error != null) {
            redirect(exchange, "/settings?err=" + PathUtil.urlEncode("Google sign-in was cancelled or denied."));
            return;
        }
        String code = QueryUtil.getParam(query, "code");
        String state = QueryUtil.getParam(query, "state");
        if (code == null || state == null) {
            redirect(exchange, "/settings?err=" + PathUtil.urlEncode("Google's response was missing required parameters."));
            return;
        }
        try {
            code = URLDecoder.decode(code, "UTF-8");
            state = URLDecoder.decode(state, "UTF-8");
        } catch (Exception e) {
            redirect(exchange, "/settings?err=" + PathUtil.urlEncode("Google's response could not be read."));
            return;
        }
        try {
            GDriveAuth.completeAuth(code, state);
        } catch (IOException e) {
            redirect(exchange, "/settings?err=" + PathUtil.urlEncode("Couldn't finish connecting Google Drive: " + e.getMessage()));
            return;
        }
        String email = GDriveAuth.getEmail();
        String msg = email != null ? ("Connected Google Drive as " + email + ".") : "Connected Google Drive.";
        redirect(exchange, "/settings?msg=" + PathUtil.urlEncode(msg));
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}
