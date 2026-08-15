import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decides whether a file's *content* looks like text, independent of its
 * extension - so a custom extension (like a homegrown .vcanvas format) still
 * gets preview/viewer support automatically, instead of requiring every
 * possible text-based extension to be hardcoded somewhere.
 *
 * Heuristic: read the first few KB and check for the absence of NUL bytes
 * (a strong binary signal) and a high ratio of printable/whitespace bytes -
 * broadly the same approach tools like `file` and `git` use to guess
 * text-vs-binary.
 */
public class TextSniffer {

    private static final int SNIFF_BYTES = 8000;
    private static final double PRINTABLE_RATIO_THRESHOLD = 0.95;

    public static boolean looksLikeText(File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] buf = new byte[SNIFF_BYTES];
            int read = is.read(buf);
            if (read <= 0) return true; // empty file - harmless to treat as text

            int printable = 0;
            for (int i = 0; i < read; i++) {
                int b = buf[i] & 0xFF;
                if (b == 0) return false; // NUL byte - reliable binary signal
                if (b == 9 || b == 10 || b == 13) { printable++; continue; } // tab/LF/CR
                if (b >= 32 && b < 127) { printable++; continue; }           // printable ASCII
                if (b >= 128) { printable++; }                              // lenient: allow UTF-8/extended bytes
            }
            return (double) printable / read > PRINTABLE_RATIO_THRESHOLD;
        } catch (IOException e) {
            return false;
        }
    }
}
