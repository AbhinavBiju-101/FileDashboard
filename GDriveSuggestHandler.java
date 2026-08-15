import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Serves "/gdrive-suggest?q=..." - a small JSON array of
 * {id, name, type ("folder"|"file")} suggestions, used by both the search
 * box's live dropdown and the address bar's "jump to a Drive folder by
 * name" flow (see GDrivePageScripts.java). Same name-contains,
 * whole-Drive search as GDriveSearchHandler - just capped much smaller
 * since this is a live-typing dropdown, not a results page.
 */
public class GDriveSuggestHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String q = QueryUtil.getParam(query, "q");
        q = q == null ? "" : URLDecoder.decode(q, "UTF-8");
        String onlyFolders = QueryUtil.getParam(query, "foldersOnly");
        String account = QueryUtil.getParam(query, "account");
        account = account == null ? null : URLDecoder.decode(account, "UTF-8");
        String accountId = GDriveAuth.resolveAccount(account);

        List<GDriveClient.DriveItem> items;
        if (accountId == null || q.trim().isEmpty()) {
            items = Collections.emptyList();
        } else {
            try {
                items = GDriveClient.search(accountId, q);
            } catch (IOException e) {
                items = Collections.emptyList();
            }
        }

        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (GDriveClient.DriveItem item : items) {
            boolean isFolder = GDriveClient.isFolder(item.mimeType);
            if ("1".equals(onlyFolders) && !isFolder) continue;
            if (count >= 8) break;
            if (count > 0) sb.append(",");
            sb.append("{\"id\":\"").append(MiniJson.escape(item.id)).append("\",")
              .append("\"name\":\"").append(MiniJson.escape(item.name)).append("\",")
              .append("\"type\":\"").append(isFolder ? "folder" : "file").append("\"}");
            count++;
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
