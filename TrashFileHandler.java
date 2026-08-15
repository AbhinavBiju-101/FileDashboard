import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Serves "/trash-file?id=...&sub=...&mode=view|download" - streams a single
 * file found while browsing a trashed folder (see TrashBrowseHandler),
 * mirroring what FileViewHandler does for files under ROOT_DIR. Kept as a
 * separate, much simpler handler rather than teaching FileViewHandler about
 * trash entries too, since this only needs to serve bytes for viewing or
 * downloading - none of the range-request/download-history bookkeeping
 * around FileViewHandler is dropped, it's just not relevant to a read-only
 * detour into the recycle bin.
 */
public class TrashFileHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String id = QueryUtil.getParam(query, "id");
        String sub = QueryUtil.getParam(query, "sub");
        String mode = QueryUtil.getParam(query, "mode");
        id = id == null ? "" : URLDecoder.decode(id, "UTF-8");
        sub = sub == null ? "" : URLDecoder.decode(sub, "UTF-8");
        mode = mode == null ? "download" : mode;

        TrashManager.Entry entry = TrashManager.get(id);
        if (entry == null) {
            sendText(exchange, 404, "That item is no longer in the trash.");
            return;
        }

        File base = new File(Config.TRASH_DIR, entry.trashedName);
        File file;
        try {
            file = resolveWithinBase(base, sub);
        } catch (IOException e) {
            sendText(exchange, 403, "Forbidden");
            return;
        }

        if (!file.exists() || file.isDirectory()) {
            sendText(exchange, 404, "Not found");
            return;
        }

        long fileLength = file.length();
        exchange.getResponseHeaders().set("Content-Type", MimeUtil.resolve(file));
        if ("download".equals(mode)) {
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        }

        exchange.sendResponseHeaders(200, fileLength);
        try (OutputStream os = exchange.getResponseBody();
             RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = raf.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    private File resolveWithinBase(File base, String sub) throws IOException {
        String cleaned = sub.replace("\\", "/");
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);

        Path basePath = base.toPath().toAbsolutePath().normalize();
        Path target = basePath.resolve(cleaned).normalize();

        if (!target.equals(basePath) && !target.startsWith(basePath)) {
            throw new IOException("Access outside the trashed folder is not allowed.");
        }
        return target.toFile();
    }

    private void sendText(HttpExchange exchange, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
