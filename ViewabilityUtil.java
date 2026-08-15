import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Decides whether a file type is something a browser can actually render
 * inline (image, pdf, text, audio, video) versus something it would just
 * dump as raw bytes (docx, xlsx, zip, exe, unknown extensions, etc.).
 *
 * This exists specifically so the "View" button never lands someone on a
 * page of garbage bytes - if a type isn't in here, they get a clear
 * "no preview available" message with a Download option instead.
 */
public class ViewabilityUtil {

    private static final Set<String> VIEWABLE = new HashSet<>(Arrays.asList(
        // images
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico",
        // text-like documents a browser renders natively
        "pdf", "txt", "md", "csv", "json", "xml", "log", "html", "htm",
        "css", "js", "ts", "java", "py", "c", "cpp", "h", "hpp", "sh",
        "yml", "yaml", "ini", "conf", "properties",
        // audio
        "mp3", "wav", "ogg", "m4a", "flac", "aac",
        // video
        "mp4", "webm", "mov", "m4v"
    ));

    public static boolean isViewable(String extensionLowercase) {
        return VIEWABLE.contains(extensionLowercase);
    }
}
