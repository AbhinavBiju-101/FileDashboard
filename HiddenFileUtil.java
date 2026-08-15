import java.nio.file.Path;

/**
 * Detects hidden files/folders (dotfiles) so they can be excluded from
 * directory listings, search results, and zip downloads - most importantly
 * so Config.DATA_DIR (".filedashboard", which lives inside the home folder
 * that's now the browse root) never shows up or gets touched through the UI.
 */
public class HiddenFileUtil {

    public static boolean isHiddenName(String name) {
        return name.startsWith(".");
    }

    // True if any path segment between base and p is hidden.
    public static boolean isHiddenPath(Path base, Path p) {
        Path rel = base.relativize(p);
        for (Path part : rel) {
            if (isHiddenName(part.toString())) return true;
        }
        return false;
    }
}
