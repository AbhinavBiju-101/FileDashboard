import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;

/**
 * Handles "POST /upload?path=..." with a multipart/form-data body
 * containing a single <input type="file" name="file">.
 */
public class UploadHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String query = exchange.getRequestURI().getRawQuery();
        String relPath = QueryUtil.getParam(query, "path");
        relPath = relPath == null ? "" : URLDecoder.decode(relPath, "UTF-8");

        File targetDir;
        try {
            targetDir = PathUtil.resolve(relPath);
        } catch (IOException e) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        if (!targetDir.isDirectory()) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String boundary = contentType == null ? null : extractBoundary(contentType);
        if (boundary == null) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        byte[] body = readAll(exchange.getRequestBody());
        MultipartParser.Part part = MultipartParser.extractFilePart(body, boundary);

        if (part == null || part.filename == null || part.filename.isEmpty()) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        File outFile = new File(targetDir, sanitizeFilename(part.filename));
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(part.data);
        }

        exchange.getResponseHeaders().set("Location", "/browse?path=" + PathUtil.urlEncode(relPath));
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private String sanitizeFilename(String name) {
        return new File(name).getName();
    }

    private String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring("boundary=".length()).replace("\"", "");
            }
        }
        return null;
    }

    private byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }
}
