import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps a file extension to a highlight.js language class, for syntax
 * highlighting in the preview modal and Viewer tab. Deliberately doesn't
 * try to cover every possible language: known extensions get an explicit
 * class (more reliable than auto-detection); anything else that's text-like
 * but unmapped (a custom format, an unrecognized language) still gets
 * highlighted via highlight.js's own auto-detection - just without a class
 * hint. A few clearly-prose extensions (.txt, .log, .csv, .md) are excluded
 * entirely, since forcing "code" styling onto plain prose looks wrong and
 * .md already gets its own dedicated rendering via MarkdownLite.
 */
public class CodeLanguageUtil {

    private static final Map<String, String> EXT_TO_LANG = new HashMap<>();
    private static final Set<String> NEVER_HIGHLIGHT = new HashSet<>();

    static {
        EXT_TO_LANG.put("java", "java");
        EXT_TO_LANG.put("py", "python");
        EXT_TO_LANG.put("c", "c");
        EXT_TO_LANG.put("cpp", "cpp");
        EXT_TO_LANG.put("h", "cpp");
        EXT_TO_LANG.put("hpp", "cpp");
        EXT_TO_LANG.put("js", "javascript");
        EXT_TO_LANG.put("ts", "typescript");
        EXT_TO_LANG.put("html", "xml");
        EXT_TO_LANG.put("htm", "xml");
        EXT_TO_LANG.put("css", "css");
        EXT_TO_LANG.put("json", "json");
        EXT_TO_LANG.put("xml", "xml");
        EXT_TO_LANG.put("yml", "yaml");
        EXT_TO_LANG.put("yaml", "yaml");
        EXT_TO_LANG.put("sh", "bash");
        EXT_TO_LANG.put("ini", "ini");
        EXT_TO_LANG.put("conf", "ini");
        EXT_TO_LANG.put("properties", "properties");

        NEVER_HIGHLIGHT.add("txt");
        NEVER_HIGHLIGHT.add("log");
        NEVER_HIGHLIGHT.add("csv");
        NEVER_HIGHLIGHT.add("md"); // handled separately by MarkdownLite
    }

    public static boolean shouldHighlight(String extLowercase) {
        return !NEVER_HIGHLIGHT.contains(extLowercase);
    }

    // Returns the highlight.js language class name, or "" if unmapped
    // (meaning: still highlight, just let highlight.js auto-detect).
    // Only call this after confirming shouldHighlight() is true.
    public static String hljsLanguage(String extLowercase) {
        return EXT_TO_LANG.getOrDefault(extLowercase, "");
    }
}
