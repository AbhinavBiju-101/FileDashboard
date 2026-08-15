import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Helpers for turning a URL "path" query param into a real File, safely.
 * Every incoming request path is resolved against Config.ROOT_DIR and checked
 * so nobody can escape it with things like "../../etc/passwd".
 *
 * Uses Path.normalize() (lexical - collapses ".."/"." segments without
 * touching the filesystem) rather than File.getCanonicalFile() (which also
 * follows symlinks/junctions). That distinction matters in practice: Windows
 * often has Desktop/Documents/Pictures redirected via NTFS junction points
 * (e.g. OneDrive's "Known Folder Move"), and canonicalizing would resolve
 * those to wherever they actually point - which can legitimately differ
 * enough from the canonicalized root to incorrectly look like an escape,
 * even for an ordinary subfolder. Lexical normalization still correctly
 * blocks real ".." traversal attempts, just without that false positive.
 */
public class PathUtil {

    public static File resolve(String relativePath) throws IOException {
        if (relativePath == null) relativePath = "";
        relativePath = relativePath.replace("\\", "/");
        while (relativePath.startsWith("/")) relativePath = relativePath.substring(1);

        Path root = Settings.rootDir().toPath().toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();

        if (!target.equals(root) && !target.startsWith(root)) {
            throw new IOException("Access outside the root directory is not allowed.");
        }
        return target.toFile();
    }

    // Same "no escaping via .." protection as resolve() above, but scoped to
    // an arbitrary base directory instead of ROOT_DIR - used by the trash
    // handlers/manager to safely reach inside a single trashed folder
    // (which lives under Config.TRASH_DIR, outside ROOT_DIR entirely).
    public static File resolveWithinBase(File base, String sub) throws IOException {
        String cleaned = sub == null ? "" : sub.replace("\\", "/");
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);

        Path basePath = base.toPath().toAbsolutePath().normalize();
        Path target = basePath.resolve(cleaned).normalize();

        if (!target.equals(basePath) && !target.startsWith(basePath)) {
            throw new IOException("Access outside that folder is not allowed.");
        }
        return target.toFile();
    }

    // Given a File somewhere under ROOT_DIR, return its path relative to the
    // root using forward slashes (e.g. "Photos/2024/beach.jpg").
    public static String relativeToRoot(File file) {
        Path root = Settings.rootDir().toPath().toAbsolutePath().normalize();
        Path f = file.toPath().toAbsolutePath().normalize();
        if (f.equals(root)) return "";
        Path rel = root.relativize(f);
        return rel.toString().replace(File.separatorChar, '/');
    }

    public static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    public static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                 .replace("<", "&lt;")
                 .replace(">", "&gt;")
                 .replace("\"", "&quot;");
    }
}
