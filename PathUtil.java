import java.io.File;
import java.io.IOException;

/**
 * Helpers for turning a URL "path" query param into a real File, safely.
 * Every incoming request path is resolved against Config.ROOT_DIR and checked
 * so nobody can escape it with things like "../../etc/passwd".
 */
public class PathUtil {

    public static File resolve(String relativePath) throws IOException {
        if (relativePath == null) relativePath = "";
        relativePath = relativePath.replace("\\", "/");
        while (relativePath.startsWith("/")) relativePath = relativePath.substring(1);

        File target = new File(Config.ROOT_DIR, relativePath).getCanonicalFile();
        File root = Config.ROOT_DIR.getCanonicalFile();

        String targetPath = target.getPath();
        String rootPath = root.getPath();

        if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
            throw new IOException("Access outside the root directory is not allowed.");
        }
        return target;
    }

    // Given an absolute File somewhere under ROOT_DIR, return its path relative
    // to the root using forward slashes (e.g. "Photos/2024/beach.jpg").
    public static String relativeToRoot(File file) throws IOException {
        File root = Config.ROOT_DIR.getCanonicalFile();
        File f = file.getCanonicalFile();
        String rootPath = root.getPath();
        String filePath = f.getPath();
        if (filePath.equals(rootPath)) return "";
        return filePath.substring(rootPath.length() + 1).replace(File.separatorChar, '/');
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
