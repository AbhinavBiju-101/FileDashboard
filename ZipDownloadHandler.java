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
 * Serves "/zip?path=..." - streams the requested folder (and everything
 * inside it, recursively) as a single .zip file. Nothing is written to a
 * temp file first; entries are compressed straight into the HTTP response as
 * they're read, so this works for large folders without excess memory or disk use.
 */
public class ZipDownloadHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String relPath = QueryUtil.getParam(query, "path");
        relPath = relPath == null ? "" : URLDecoder.decode(relPath, "UTF-8");

        File dir;
        try {
            dir = PathUtil.resolve(relPath);
        } catch (IOException e) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        if (!dir.isDirectory()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        // Record the download so the Dashboard picks it up. There's no
        // single "file" here to add to RecentActivity's recentDownloaded
        // list (that list is rendered as file cards - see HomeHandler
        // .fileSection), so a folder-zip download won't get its own card
        // there. It still needs to log *something*, though - otherwise
        // ActivityLog.getVersion() never changes for a folder-zip download,
        // DashboardEventsHandler never reports a new version, and the
        // Dashboard tab's poll (see HomeHandler.dashboardRefreshScript)
        // never knows to refresh. ActivityLog.record() is safe to call with
        // a directory path - timelineRow() already skips any entry whose
        // path doesn't resolve to a file, the same graceful fallback used
        // for entries that have since moved or been deleted.
        ActivityLog.record(relPath, "downloaded");

        String zipName = (relPath.isEmpty() ? "download" : dir.getName()) + ".zip";
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + zipName + "\"");
        exchange.sendResponseHeaders(200, 0); // chunked - total size isn't known ahead of time

        Path base = dir.toPath();
        try (OutputStream os = exchange.getResponseBody();
             ZipOutputStream zos = new ZipOutputStream(os)) {
            Files.walk(base).filter(Files::isRegularFile)
                .filter(p -> !HiddenFileUtil.isHiddenPath(base, p))
                .forEach(p -> {
                try {
                    String entryName = base.relativize(p).toString().replace(File.separatorChar, '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(p, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    // Skip a file we couldn't read (permissions, mid-write, etc.)
                    // rather than failing the whole archive.
                }
            });
        }
    }
}
