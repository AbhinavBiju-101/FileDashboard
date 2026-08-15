import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal multipart/form-data parser, just enough to pull the first uploaded
 * file (a part with a "filename" attribute) out of a raw request body.
 * Written by hand (byte-level) since no external libraries are used.
 */
public class MultipartParser {

    public static class Part {
        public String filename;
        public byte[] data;
    }

    public static Part extractFilePart(byte[] body, String boundary) {
        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] headerEndMarker = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

        int start = indexOf(body, boundaryBytes, 0);
        while (start != -1) {
            int headerStart = start + boundaryBytes.length;
            int headerEnd = indexOf(body, headerEndMarker, headerStart);
            if (headerEnd == -1) break;

            String headers = new String(body, headerStart, headerEnd - headerStart, StandardCharsets.ISO_8859_1);
            String filename = extractFilename(headers);

            int dataStart = headerEnd + 4;
            int nextBoundary = indexOf(body, boundaryBytes, dataStart);
            if (nextBoundary == -1) break;

            int dataEnd = nextBoundary;
            if (dataEnd >= 2 && body[dataEnd - 1] == '\n' && body[dataEnd - 2] == '\r') {
                dataEnd -= 2;
            }

            if (filename != null && !filename.isEmpty()) {
                Part part = new Part();
                part.filename = filename;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(body, dataStart, dataEnd - dataStart);
                part.data = out.toByteArray();
                return part;
            }

            start = nextBoundary;
        }
        return null;
    }

    private static String extractFilename(String headers) {
        Matcher m = Pattern.compile("filename=\"([^\"]*)\"").matcher(headers);
        if (m.find()) return m.group(1);
        return null;
    }

    private static int indexOf(byte[] data, byte[] pattern, int fromIndex) {
        outer:
        for (int i = fromIndex; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
