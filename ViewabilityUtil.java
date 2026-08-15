import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Decides whether a file type is something a browser can actually render
 * inline (image, pdf, audio, video, or text) versus something it would just
 * dump as raw bytes (docx, xlsx, zip, exe, etc.).
 *
 * Images/PDF/audio/video are decided purely by extension - sniffing
 * wouldn't help identify a binary format anyway. Text is different: rather
 * than requiring every possible text-based extension to be hardcoded here,
 * unrecognized extensions fall back to TextSniffer reading the actual file
 * content - so a custom format (like a homegrown .vcanvas) still gets full
 * viewer support without needing code changes for every possible extension.
 */
public class ViewabilityUtil {

    private static final Set<String> IMAGE_EXTS = new HashSet<>(Arrays.asList(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico"));

    private static final Set<String> AUDIO_EXTS = new HashSet<>(Arrays.asList(
        "mp3", "wav", "ogg", "m4a", "flac", "aac"));

    private static final Set<String> VIDEO_EXTS = new HashSet<>(Arrays.asList(
        "mp4", "webm", "mov", "m4v"));

    // Rendered via mammoth.js client-side (see PageScripts.DOCX_RESOURCES) -
    // not text-like (isTextLike stays false for these; there's no plain-text
    // representation worth editing), just its own viewable category.
    private static final Set<String> DOCX_EXTS = new HashSet<>(Arrays.asList("docx"));

    // Extensions known to be text without needing to sniff - a fast path,
    // not an exhaustive requirement (see isTextLike below).
    private static final Set<String> KNOWN_TEXT_EXTS = new HashSet<>(Arrays.asList(
        "txt", "md", "csv", "json", "xml", "log", "html", "htm",
        "css", "js", "ts", "java", "py", "c", "cpp", "h", "hpp", "sh",
        "yml", "yaml", "ini", "conf", "properties"));

    public static boolean isViewable(File file, String extLowercase) {
        return IMAGE_EXTS.contains(extLowercase)
            || extLowercase.equals("pdf")
            || AUDIO_EXTS.contains(extLowercase)
            || VIDEO_EXTS.contains(extLowercase)
            || DOCX_EXTS.contains(extLowercase)
            || isTextLike(file, extLowercase);
    }

    // True for known text extensions, or - for anything else - if the
    // file's actual content looks like text.
    public static boolean isTextLike(File file, String extLowercase) {
        if (KNOWN_TEXT_EXTS.contains(extLowercase)) return true;
        // Don't bother sniffing formats we already know are binary.
        if (IMAGE_EXTS.contains(extLowercase) || AUDIO_EXTS.contains(extLowercase)
            || VIDEO_EXTS.contains(extLowercase) || DOCX_EXTS.contains(extLowercase)
            || extLowercase.equals("pdf")) {
            return false;
        }
        return file != null && file.isFile() && TextSniffer.looksLikeText(file);
    }
}
