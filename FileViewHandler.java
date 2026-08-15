import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Serves "/file?path=...&mode=view|download|preview".
 *
 * mode=view     streams the file inline (browser renders images/PDFs/text/video directly).
 * mode=download forces a Save As via Content-Disposition: attachment.
 * mode=preview  used for file types the browser can't render (docx, zip, etc.) -
 *               shows a small "no preview available" page with a Download button,
 *               instead of letting the browser dump raw bytes as a garbled "file".
 *
 * Also supports HTTP Range requests (RFC 7233) for view/download, which is what
 * lets a browser seek/scrub within audio or video, and resume interrupted downloads.
 */
public class FileViewHandler implements HttpHandler {

    private static final int BUF_SIZE = 8192;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String relPath = QueryUtil.getParam(query, "path");
        String mode = QueryUtil.getParam(query, "mode");
        relPath = relPath == null ? "" : URLDecoder.decode(relPath, "UTF-8");
        mode = mode == null ? "view" : mode;

        File file;
        try {
            file = PathUtil.resolve(relPath);
        } catch (IOException e) {
            sendText(exchange, 403, "Forbidden");
            return;
        }

        if (!file.exists() || file.isDirectory()) {
            sendText(exchange, 404, "Not found");
            return;
        }

        if ("preview".equals(mode)) {
            sendPreviewPage(exchange, file, relPath);
            return;
        }

        if ("download".equals(mode)) {
            RecentActivity.recordDownload(relPath);
            ActivityLog.record(relPath, "downloaded");
        } else {
            RecentActivity.recordView(relPath);
            ActivityLog.record(relPath, "viewed");
        }

        long fileLength = file.length();
        String contentType = MimeUtil.resolve(file);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
        if ("download".equals(mode)) {
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        }

        String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            servePartial(exchange, file, fileLength, rangeHeader);
        } else {
            serveFull(exchange, file, fileLength);
        }
    }

    private void sendPreviewPage(HttpExchange exchange, File file, String relPath) throws IOException {
        String ext = GridRenderer.getExtension(file.getName());
        String name = PathUtil.htmlEscape(file.getName());
        String size = GridRenderer.humanSize(file.length());

        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
            "<title>" + name + "</title>" + Styles.CSS +
            "</head><body>" +
            "<div style='max-width:480px;margin:80px auto;text-align:center;font-family:-apple-system,Segoe UI,Roboto,sans-serif;'>" +
            "<div style='font-size:56px;'>" + GridRenderer.iconFor(ext.toLowerCase()) + "</div>" +
            "<h2 style='margin:12px 0 4px;'>" + name + "</h2>" +
            "<p style='color:#888;font-size:13px;margin-top:0;'>" + size + "</p>" +
            "<p style='color:#555;'>There's no in-browser preview for <strong>." +
            PathUtil.htmlEscape(ext) + "</strong> files.</p>" +
            "<p><a href='/file?path=" + PathUtil.urlEncode(relPath) + "&mode=download' " +
            "style='display:inline-block;background:#2563eb;color:#fff;padding:10px 20px;" +
            "border-radius:6px;text-decoration:none;font-size:14px;'>Download instead</a></p>" +
            "</div></body></html>";

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void serveFull(HttpExchange exchange, File file, long fileLength) throws IOException {
        exchange.sendResponseHeaders(200, fileLength);
        try (OutputStream os = exchange.getResponseBody();
             RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            copy(raf, os, 0, fileLength);
        }
    }

    private void servePartial(HttpExchange exchange, File file, long fileLength, String rangeHeader) throws IOException {
        long[] range = parseRange(rangeHeader, fileLength);
        long start = range[0];
        long end = range[1];

        if (start < 0 || end >= fileLength || start > end) {
            exchange.getResponseHeaders().set("Content-Range", "bytes */" + fileLength);
            exchange.sendResponseHeaders(416, -1);
            return;
        }

        long contentLength = end - start + 1;
        exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
        exchange.sendResponseHeaders(206, contentLength);

        try (OutputStream os = exchange.getResponseBody();
             RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            copy(raf, os, start, contentLength);
        }
    }

    private void copy(RandomAccessFile raf, OutputStream os, long start, long length) throws IOException {
        raf.seek(start);
        byte[] buf = new byte[BUF_SIZE];
        long remaining = length;
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int n = raf.read(buf, 0, toRead);
            if (n == -1) break;
            os.write(buf, 0, n);
            remaining -= n;
        }
    }

    // Parses headers like "bytes=0-999", "bytes=500-", or the suffix form "bytes=-500".
    private long[] parseRange(String header, long fileLength) {
        String spec = header.substring("bytes=".length());
        String[] parts = spec.split("-", 2);
        long start, end;
        if (parts[0].isEmpty()) {
            long suffixLength = Long.parseLong(parts[1]);
            start = Math.max(0, fileLength - suffixLength);
            end = fileLength - 1;
        } else {
            start = Long.parseLong(parts[0]);
            end = (parts.length > 1 && !parts[1].isEmpty()) ? Long.parseLong(parts[1]) : fileLength - 1;
        }
        return new long[]{start, end};
    }

    private void sendText(HttpExchange exchange, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes();
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
