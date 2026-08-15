import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serves "/gdrive-accounts" - a read-only JSON list of currently-connected
 * Google accounts (id/email/name/picture, never tokens), for anything that
 * needs to show or choose between them client-side:
 *   - ShellScript.java's account picker (shellOpenDrivePicker()), when
 *     starting a new Google Drive session or when its list needs
 *     refreshing after the popup-window "add account" flow closes.
 *   - SessionsHandler.java's session list, to show "Signed in as ..." next
 *     to each Drive session.
 *
 * No accountId/auth needed to call this - it's just "which accounts exist",
 * the same information Settings' account list already shows.
 */
public class GDriveAccountsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<GDriveAuth.AccountInfo> accounts = GDriveAuth.listAccounts();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < accounts.size(); i++) {
            GDriveAuth.AccountInfo a = accounts.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"id\":\"").append(MiniJson.escape(a.id)).append("\",");
            sb.append("\"email\":").append(a.email == null ? "null" : ("\"" + MiniJson.escape(a.email) + "\"")).append(",");
            sb.append("\"name\":").append(a.name == null ? "null" : ("\"" + MiniJson.escape(a.name) + "\"")).append(",");
            sb.append("\"picture\":").append(a.picture == null ? "null" : ("\"" + MiniJson.escape(a.picture) + "\""));
            sb.append("}");
        }
        sb.append("]");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
