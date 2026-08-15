import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Handles both halves of the "Connect a Google account" OAuth2 round trip -
 * registered under two paths (see FileServer.java) pointing at the same
 * instance, dispatching on which one was hit:
 *
 *   /gauth/start    - redirects the browser on to Google's consent screen.
 *                      Takes an optional ?context= query param, forwarded
 *                      through to GDriveAuth.beginAuth() untouched, that
 *                      controls how /gauth/callback below responds once
 *                      the round trip completes:
 *                        - "settings" (the default, used by Settings'
 *                          "Add a Google account" button - a normal
 *                          same-window link, not a popup): redirects back
 *                          to /settings with a result message.
 *                        - "picker": used by the Drive-session account
 *                          picker (see ShellScript.java's
 *                          shellOpenDrivePicker()), which opens this in an
 *                          actual popup window rather than navigating the
 *                          app shell itself away - navigating the shell (or
 *                          worse, reloading whatever frame happened to call
 *                          window.open()) would tear down every open tab.
 *                          So instead of redirecting anywhere, the callback
 *                          renders a tiny self-contained "Connected -
 *                          you can close this window" page that closes
 *                          itself; the picker modal notices the popup
 *                          closed and re-fetches /gdrive-accounts to pick
 *                          up the newly connected account.
 *
 *   /gauth/callback - Google redirects back here with an authorization
 *                      code; this exchanges it for tokens and then responds
 *                      per the context above.
 *
 * All the actual OAuth mechanics (PKCE, token exchange, refresh, and the
 * account list itself) live in GDriveAuth.java - this handler is just the
 * HTTP glue and the two different "you're done" responses around it.
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
        String query = exchange.getRequestURI().getRawQuery();
        String context = QueryUtil.getParam(query, "context");
        String redirectTo;
        try {
            redirectTo = GDriveAuth.beginAuth(context);
        } catch (IOException e) {
            respondEarlyError(exchange, context, e.getMessage());
            return;
        }
        redirect(exchange, redirectTo);
    }

    private void handleCallback(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String error = QueryUtil.getParam(query, "error");
        String state = QueryUtil.getParam(query, "state");
        if (state != null) {
            try { state = URLDecoder.decode(state, "UTF-8"); } catch (Exception ignored) {}
        }
        String context = GDriveAuth.contextFromState(state);

        if (error != null) {
            respondError(exchange, context, "Google sign-in was cancelled or denied.");
            return;
        }
        String code = QueryUtil.getParam(query, "code");
        if (code == null || state == null) {
            respondError(exchange, context, "Google's response was missing required parameters.");
            return;
        }
        try {
            code = URLDecoder.decode(code, "UTF-8");
        } catch (Exception e) {
            respondError(exchange, context, "Google's response could not be read.");
            return;
        }
        GDriveAuth.AuthResult result;
        try {
            result = GDriveAuth.completeAuth(code, state);
        } catch (IOException e) {
            respondError(exchange, context, "Couldn't finish connecting that Google account: " + e.getMessage());
            return;
        }
        GDriveAuth.AccountInfo account = GDriveAuth.getAccountInfo(result.accountId);
        String label = account != null ? account.displayName() : "your Google account";

        if ("picker".equals(context)) {
            respondPopupSuccess(exchange, label);
        } else {
            redirect(exchange, "/settings?gauth=1&msg=" + PathUtil.urlEncode("Connected " + label + "."));
        }
    }

    private void respondError(HttpExchange exchange, String context, String message) throws IOException {
        if ("picker".equals(context)) {
            respondPopupError(exchange, message);
        } else {
            redirect(exchange, "/settings?gauth=1&err=" + PathUtil.urlEncode(message));
        }
    }

    // beginAuth() itself can fail (e.g. not configured yet) before any
    // state/context round trip with Google has happened - the context is
    // still available directly from the query string in that case, no
    // state parsing needed.
    private void respondEarlyError(HttpExchange exchange, String context, String message) throws IOException {
        if ("picker".equals(context)) {
            respondPopupError(exchange, message);
        } else {
            redirect(exchange, "/settings?gauth=1&err=" + PathUtil.urlEncode(message));
        }
    }

    private void respondPopupSuccess(HttpExchange exchange, String label) throws IOException {
        sendPopupPage(exchange, "Connected as " + PathUtil.htmlEscape(label) + ". You can close this window.", false);
    }

    private void respondPopupError(HttpExchange exchange, String message) throws IOException {
        sendPopupPage(exchange, PathUtil.htmlEscape(message), true);
    }

    // A minimal standalone page for the popup-window flow - no app shell,
    // no opener.reload() (see the class comment above for why not). Just
    // closes itself after a moment; if window.close() is refused (some
    // browsers won't let a script close a window it didn't script-open, if
    // this somehow got opened by a plain link instead of window.open()) the
    // message stays on screen and the person can close it by hand.
    private void sendPopupPage(HttpExchange exchange, String message, boolean isError) throws IOException {
        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
            "<title>Google Drive</title>" +
            "<style>body{font-family:-apple-system,Segoe UI,Roboto,sans-serif;display:flex;align-items:center;" +
            "justify-content:center;height:100vh;margin:0;background:#f5f6f8;color:" + (isError ? "#a33" : "#222") + ";}" +
            "p{max-width:320px;text-align:center;padding:0 20px;font-size:14px;line-height:1.5;}</style>" +
            "</head><body><p>" + message + "</p>" +
            "<script>setTimeout(function(){ try{ window.close(); }catch(e){} }, 1200);</script>" +
            "</body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}
