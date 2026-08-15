import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Files.probeContentType() is inconsistent across OSes - it often returns
 * null for perfectly ordinary text/code files (.md, .java, .log, ...),
 * which used to make FileViewHandler fall back to
 * "application/octet-stream" and silently trigger a download instead of an
 * inline view. This fills those gaps with a small manual table before
 * giving up.
 */
public class MimeUtil {

    private static final Map<String, String> EXT_TO_MIME = new HashMap<>();
    static {
        EXT_TO_MIME.put("md", "text/plain");
        EXT_TO_MIME.put("java", "text/plain");
        EXT_TO_MIME.put("py", "text/plain");
        EXT_TO_MIME.put("log", "text/plain");
        EXT_TO_MIME.put("yml", "text/plain");
        EXT_TO_MIME.put("yaml", "text/plain");
        EXT_TO_MIME.put("ini", "text/plain");
        EXT_TO_MIME.put("conf", "text/plain");
        EXT_TO_MIME.put("properties", "text/plain");
        EXT_TO_MIME.put("sh", "text/plain");
        EXT_TO_MIME.put("c", "text/plain");
        EXT_TO_MIME.put("cpp", "text/plain");
        EXT_TO_MIME.put("h", "text/plain");
        EXT_TO_MIME.put("hpp", "text/plain");
        EXT_TO_MIME.put("json", "application/json");
        EXT_TO_MIME.put("csv", "text/csv");
        EXT_TO_MIME.put("svg", "image/svg+xml");
        EXT_TO_MIME.put("mp4", "video/mp4");
        EXT_TO_MIME.put("webm", "video/webm");
        EXT_TO_MIME.put("mov", "video/quicktime");
        EXT_TO_MIME.put("m4v", "video/mp4");
        EXT_TO_MIME.put("mp3", "audio/mpeg");
        EXT_TO_MIME.put("wav", "audio/wav");
        EXT_TO_MIME.put("m4a", "audio/mp4");
        EXT_TO_MIME.put("flac", "audio/flac");
        EXT_TO_MIME.put("ogg", "audio/ogg");
    }

    public static String resolve(File file) {
        try {
            String probed = Files.probeContentType(file.toPath());
            if (probed != null) return probed;
        } catch (IOException ignored) {
            // fall through to the manual table below
        }
        String ext = GridRenderer.getExtension(file.getName()).toLowerCase();
        if (EXT_TO_MIME.containsKey(ext)) return EXT_TO_MIME.get(ext);

        // Unrecognized extension - if the content actually looks like text
        // (e.g. a custom format like .vcanvas), serve it as such instead of
        // falling back to a download-triggering octet-stream.
        if (TextSniffer.looksLikeText(file)) return "text/plain";
        return "application/octet-stream";
    }
}
