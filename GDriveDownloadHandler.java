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
 * Understands Range requests (Accept-Ranges/206 Partial Content) - needed
 * for <video>/<audio> elements, which almost always issue a Range request
 * even on first load (some browsers refuse to start playback at all
 * without a 206 response to their very first request), and for scrubbing
 * partway into a video after that. Without this, a video preview would
 * just silently fail to load rather than falling back to playing from the
 * start. The whole file still has to be fetched from Drive and buffered
 * here first (see the comment on that below) - only the response back to
 * the browser is range-limited, not the upstream Drive request.
 *
 * name/mime are only display hints carried over from the listing that
 * generated the link (see GDriveBrowseHandler.java) - the actual streamed
 * bytes and the real mime type used for the response come from a fresh
 * Drive metadata lookup done here, not trusted from the query string.
 * "account" identifies which connected Google account to use (see
 * GDriveAuth.java) - resolved defensively the same way every other Drive
 * handler does, via GDriveAuth.resolveAccount(), rather than failing
 * outright on an old link saved before this app supported more than one.
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
        String account = QueryUtil.getParam(query, "account");
        if (account != null) {
            try { account = URLDecoder.decode(account, "UTF-8"); } catch (Exception ignored) {}
        }
        String accountId = GDriveAuth.resolveAccount(account);
        if (accountId == null) {
            respondError(exchange, 400, "No Google account is connected - connect one from Settings first.");
            return;
        }

        GDriveClient.DriveItem meta;
        try {
            meta = GDriveClient.getMetadata(accountId, id);
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
        String mime = GDriveClient.bestMimeForName(meta.mimeType, fileName);

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
            GDriveClient.streamFile(accountId, id, buffer);
        } catch (IOException e) {
            respondError(exchange, 502, "Couldn't download this file from Google Drive: " + e.getMessage());
            return;
        }
        byte[] bytes = buffer.toByteArray();
        String disposition = "view".equals(mode) ? "inline" : "attachment";
        exchange.getResponseHeaders().set("Content-Disposition",
            disposition + "; filename=\"" + fileName.replace("\"", "'") + "\"");
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");

        String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
        long[] range = rangeHeader == null ? null : parseRange(rangeHeader, bytes.length);
        if (range == null) {
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }

        long start = range[0], end = range[1];
        long len = end - start + 1;
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + bytes.length);
        exchange.sendResponseHeaders(206, len);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes, (int) start, (int) len);
        }
    }

    // Parses a single-range "bytes=START-END" Range header (the only form
    // browsers actually send for media playback - multi-range requests
    // aren't a real-world concern here). Returns null for anything
    // malformed or unsatisfiable, which the caller treats the same as "no
    // Range header at all" (falls back to a normal 200 with the whole
    // file) rather than failing the request outright.
    private long[] parseRange(String header, int totalLength) {
        if (!header.startsWith("bytes=")) return null;
        String spec = header.substring(6).split(",")[0].trim();
        int dash = spec.indexOf('-');
        if (dash == -1) return null;
        try {
            String startStr = spec.substring(0, dash);
            String endStr = spec.substring(dash + 1);
            long start, end;
            if (startStr.isEmpty()) {
                // "-N" = last N bytes
                long suffixLen = Long.parseLong(endStr);
                start = Math.max(0, totalLength - suffixLen);
                end = totalLength - 1;
            } else {
                start = Long.parseLong(startStr);
                end = endStr.isEmpty() ? totalLength - 1 : Long.parseLong(endStr);
            }
            if (start < 0 || end >= totalLength || start > end) return null;
            return new long[]{start, end};
        } catch (NumberFormatException e) {
            return null;
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
