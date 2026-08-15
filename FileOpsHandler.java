import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Handles "POST /fileops" with a form-encoded body: action=rename|duplicate|delete,
 * path=<relative path>, and (for rename) newName=<new file/folder name>.
 * Responds with a small JSON object: {"success": true/false, "message": "..."}.
 */
public class FileOpsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String body = readAll(exchange.getRequestBody());
        String action = formParam(body, "action");
        String relPath = formParam(body, "path");
        String newName = formParam(body, "newName");
        relPath = relPath == null ? "" : relPath;

        if (relPath.isEmpty()) {
            respondJson(exchange, false, "Can't modify the root folder itself.");
            return;
        }

        File target;
        try {
            target = PathUtil.resolve(relPath);
        } catch (IOException e) {
            respondJson(exchange, false, "Access denied.");
            return;
        }

        if (!target.exists()) {
            respondJson(exchange, false, "That file or folder no longer exists.");
            return;
        }

        if (HiddenFileUtil.isHiddenName(target.getName())) {
            respondJson(exchange, false, "Hidden files can't be modified through the dashboard.");
            return;
        }

        try {
            String result;
            switch (action == null ? "" : action) {
                case "rename":
                    result = handleRename(target, relPath, newName);
                    break;
                case "duplicate":
                    result = handleDuplicate(target);
                    break;
                case "delete":
                    result = handleDelete(target, relPath);
                    break;
                default:
                    respondJson(exchange, false, "Unknown action.");
                    return;
            }
            respondJson(exchange, true, result);
        } catch (IOException e) {
            respondJson(exchange, false, e.getMessage());
        }
    }

    private String handleRename(File target, String relPath, String newName) throws IOException {
        if (newName == null) newName = "";
        newName = newName.trim();
        String sanitized = new File(newName).getName(); // strips any path separators
        if (sanitized.isEmpty()) throw new IOException("New name can't be empty.");

        File dest = new File(target.getParentFile(), sanitized);
        if (dest.exists()) {
            throw new IOException("A file or folder named \"" + sanitized + "\" already exists here.");
        }

        Files.move(target.toPath(), dest.toPath());
        RecentActivity.forgetPath(relPath);
        return "Renamed to " + sanitized;
    }

    private String handleDuplicate(File target) throws IOException {
        File parent = target.getParentFile();
        String copyName = generateCopyName(parent, target.getName());
        File dest = new File(parent, copyName);

        if (target.isDirectory()) {
            copyDirectoryRecursively(target.toPath(), dest.toPath());
        } else {
            Files.copy(target.toPath(), dest.toPath());
        }
        return "Created " + copyName;
    }

    private String handleDelete(File target, String relPath) throws IOException {
        if (target.isDirectory()) {
            deleteRecursively(target.toPath());
        } else {
            Files.delete(target.toPath());
        }
        RecentActivity.forgetPath(relPath);
        return "Deleted";
    }

    private String generateCopyName(File parent, String originalName) {
        String base = originalName;
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) { // > 0, not >= 0, so a leading dot (hidden file) isn't treated as an extension
            base = originalName.substring(0, dot);
            ext = originalName.substring(dot);
        }

        String candidate = base + " (copy)" + ext;
        int counter = 2;
        while (new File(parent, candidate).exists()) {
            candidate = base + " (copy " + counter + ")" + ext;
            counter++;
        }
        return candidate;
    }

    private void copyDirectoryRecursively(Path src, Path dest) throws IOException {
        try (Stream<Path> walk = Files.walk(src)) {
            walk.forEach(sourcePath -> {
                try {
                    Path targetPath = dest.resolve(src.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(sourcePath, targetPath);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) { /* best-effort */ }
            });
        }
    }

    private String formParam(String body, String key) {
        if (body == null) return null;
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx == -1) continue;
            if (pair.substring(0, idx).equals(key)) {
                try {
                    return URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                } catch (Exception e) {
                    return pair.substring(idx + 1);
                }
            }
        }
        return null;
    }

    private String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toString("UTF-8");
    }

    private void respondJson(HttpExchange exchange, boolean success, String message) throws IOException {
        String json = "{\"success\": " + success + ", \"message\": \"" + MiniJson.escape(message == null ? "" : message) + "\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
