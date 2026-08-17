import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Serves "/gdrive-export?id=...&name=...&mime=...&mode=..." - a native
 * Google Doc/Sheet/Slide/Drawing, converted server-side to PDF via
 * GDriveClient.exportFile() and streamed back the same way
 * GDriveDownloadHandler streams a regular file's raw bytes.
 *
 * This exists specifically so previewing one of these files doesn't depend
 * on the *browser's* own Google sign-in matching the connected account -
 * see GDriveClient.exportFile()'s comment for the full story. Google's own
 * embeddable "/preview" iframe is still used as a fallback for the one
 * native type that can't be exported this way (Forms - see
 * GDriveClient.isExportable()).
 *
 * mode=view sends Content-Disposition: inline (for the preview modal and
 * /gdrive-viewer's iframe). Any other value sends "attachment" with a
 * ".pdf" filename, for an explicit "Download as PDF" link.
 */
public class GDriveExportHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String id = QueryUtil.getParam(query, "id");
        String name = QueryUtil.getParam(query, "name");
        String mode = QueryUtil.getParam(query, "mode");
        if (id != null) {
            try { id = URLDecoder.decode(id, "UTF-8"); } catch (Exception ignored) {}
        }
        if (name != null) {
            try { name = URLDecoder.decode(name, "UTF-8"); } catch (Exception ignored) {}
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

        String exportMime = GDriveClient.exportMimeFor(meta.mimeType);
        if (exportMime == null) {
            respondError(exchange, 400, "This file type can't be exported for preview - use \"Open in Drive\" instead.");
            return;
        }

        String fileName = (meta.name != null ? meta.name : (name != null ? name : "download")) + ".pdf";

        // Buffered for the same reason GDriveDownloadHandler buffers -
        // com.sun.net.httpserver needs a known Content-Length up front,
        // and export results are converted documents, not huge media
        // files, so holding one fully in memory is a reasonable trade-off
        // here (no Range/206 support needed either, unlike video/audio).
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            GDriveClient.exportFile(accountId, id, exportMime, buffer);
        } catch (IOException e) {
            respondError(exchange, 502, "Couldn't export this file from Google Drive: " + e.getMessage());
            return;
        }
        byte[] bytes = buffer.toByteArray();
        String disposition = "view".equals(mode) ? "inline" : "attachment";
        exchange.getResponseHeaders().set("Content-Disposition",
            disposition + "; filename=\"" + fileName.replace("\"", "'") + "\"");
        exchange.getResponseHeaders().set("Content-Type", exportMime);
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
