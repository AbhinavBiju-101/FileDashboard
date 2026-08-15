import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Serves "/gdrive-file?id=...&name=...&mime=...&mode=..." - streams a
 * Drive file's raw bytes back through this server (rather than sending the
 * browser straight to Google) so it behaves like any other download link in
 * the app: a normal same-origin URL with a sensible filename, no separate
 * Google sign-in prompt in the browser tab itself.
 *
 * mode=view sends Content-Disposition: inline (for the preview modal and
 * /gdrive-viewer - an <img>/<iframe>/fetch() embedding the bytes directly
 * rather than triggering a save-file prompt). Any other value (or omitted)
 * sends "attachment", same as local's /file?mode=download.
 *
 * name/mime are only display hints carried over from the listing that
 * generated the link (see GDriveBrowseHandler.java) - the actual streamed
 * bytes and the real mime type used for the response come from a fresh
 * Drive metadata lookup done here, not trusted from the query string.
 */
public class GDriveDownloadHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String id = QueryUtil.getParam(query, "id");
        String mode = QueryUtil.getParam(query, "mode"); // "view" = inline (viewer/preview), default = attachment (Download)
        if (id != null) {
            try { id = URLDecoder.decode(id, "UTF-8"); } catch (Exception ignored) {}
        }
        if (id == null || id.isEmpty()) {
            respondError(exchange, 400, "Missing file id.");
            return;
        }

        GDriveClient.DriveItem meta;
        try {
            meta = GDriveClient.getMetadata(id);
        } catch (IOException e) {
            respondError(exchange, 502, "Couldn't look up this file on Google Drive: " + e.getMessage());
            return;
        }
        if (GDriveClient.isFolder(meta.mimeType)) {
            respondError(exchange, 400, "That's a folder, not a file.");
            return;
        }
        if (GDriveClient.isNativeGoogleDoc(meta.mimeType)) {
            respondError(exchange, 400, "Google Docs/Sheets/Slides can't be downloaded directly yet - use \"Open\" instead.");
            return;
        }

        String fileName = meta.name != null ? meta.name : "download";
        String mime = meta.mimeType != null ? meta.mimeType : "application/octet-stream";

        // Buffered rather than streamed straight to the response, because
        // com.sun.net.httpserver needs a known Content-Length (or chunked
        // transfer) declared before the body starts, and the simplest way
        // to get an accurate length here - without trusting the "size"
        // field from an earlier listing that may be stale - is to fetch
        // the whole thing first. Fine for the file sizes this app's other
        // download paths already assume; a genuinely large-file streaming
        // path would need chunked responses instead.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            GDriveClient.streamFile(id, buffer);
        } catch (IOException e) {
            respondError(exchange, 502, "Couldn't download this file from Google Drive: " + e.getMessage());
            return;
        }
        byte[] bytes = buffer.toByteArray();

        exchange.getResponseHeaders().set("Content-Type", mime);
        String disposition = "view".equals(mode) ? "inline" : "attachment";
        exchange.getResponseHeaders().set("Content-Disposition",
            disposition + "; filename=\"" + fileName.replace("\"", "'") + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void respondError(HttpExchange exchange, int status, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
