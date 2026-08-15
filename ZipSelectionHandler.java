import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Serves "/zip-selection?paths=a|b|c" - like ZipDownloadHandler but for an
 * arbitrary multi-select group instead of one whole folder. Each selected
 * top-level item keeps its own name as the entry prefix, so folders keep
 * their internal structure and files sit at the zip's root.
 */
public class ZipSelectionHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String pathsParam = QueryUtil.getParam(query, "paths");
        if (pathsParam == null || pathsParam.isEmpty()) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        String[] rawPaths = URLDecoder.decode(pathsParam, "UTF-8").split("\\|");

        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"selection.zip\"");
        exchange.sendResponseHeaders(200, 0); // chunked

        try (OutputStream os = exchange.getResponseBody();
             ZipOutputStream zos = new ZipOutputStream(os)) {
            for (String relPath : rawPaths) {
                if (relPath.isEmpty()) continue;
                File item;
                try {
                    item = PathUtil.resolve(relPath);
                } catch (IOException e) {
                    continue; // skip anything outside the root rather than failing the whole zip
                }
                if (!item.exists() || HiddenFileUtil.isHiddenName(item.getName())) continue;

                // Record the download - previously missing entirely for zip
                // downloads, which is why "Recently downloaded" (and the
                // Dashboard's live-refresh poll, which is driven by these
                // same version counters) never reacted to a zip-selection
                // download. A selected file gets a proper entry, same as a
                // single-file download via FileViewHandler; a selected
                // folder has no single "file" to add to RecentActivity's
                // file-card list, so it only goes into the ActivityLog
                // timeline (see ZipDownloadHandler for the same trade-off).
                if (item.isFile()) {
                    RecentActivity.recordDownload(relPath);
                }
                ActivityLog.record(relPath, "downloaded");

                if (item.isDirectory()) {
                    Path base = item.toPath();
                    Files.walk(base).filter(Files::isRegularFile).forEach(p -> {
                        try {
                            String entryName = item.getName() + "/" + base.relativize(p).toString().replace(File.separatorChar, '/');
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(p, zos);
                            zos.closeEntry();
                        } catch (IOException ignored) { /* skip unreadable file */ }
                    });
                } else {
                    zos.putNextEntry(new ZipEntry(item.getName()));
                    Files.copy(item.toPath(), zos);
                    zos.closeEntry();
                }
            }
        }
    }
}
