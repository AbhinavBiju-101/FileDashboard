/**
 * Tiny helper shared by handlers to pull a value out of a raw query string,
 * e.g. "path=Photos%2F2024&mode=view".
 */
public class QueryUtil {
    public static String getParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx == -1) continue;
            String k = pair.substring(0, idx);
            String v = pair.substring(idx + 1);
            if (k.equals(key)) return v;
        }
        return null;
    }
}
